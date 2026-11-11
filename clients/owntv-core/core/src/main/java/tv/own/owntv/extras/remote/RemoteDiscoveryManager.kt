// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.remote

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.net.Inet4Address

class RemoteDiscoveryManager(context: Context) {
    private val nsd = context.applicationContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private var registration: NsdManager.RegistrationListener? = null

    fun advertise(name: String, port: Int, deviceId: String = "") {
        val manager = nsd ?: return
        stopAdvertising()
        val info = NsdServiceInfo().apply {
            serviceName = name.take(32)
            serviceType = SERVICE_TYPE
            setPort(port)
            if (deviceId.isNotBlank()) setAttribute("id", deviceId)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "NSD registration failed: $errorCode")
            }
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
        }
        registration = listener
        runCatching { manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    fun stopAdvertising() {
        val manager = nsd ?: return
        registration?.let { runCatching { manager.unregisterService(it) } }
        registration = null
    }

    fun discover(): Flow<List<RemoteDiscoveryInfo>> = callbackFlow {
        val manager = nsd ?: run {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val discovered = mutableMapOf<String, RemoteDiscoveryInfo>()
        val pending = ArrayDeque<NsdServiceInfo>()
        var resolving = false

        fun resolveNext() {
            if (resolving) return
            val next = pending.removeFirstOrNull() ?: return
            resolving = true
            @Suppress("DEPRECATION")
            manager.resolveService(
                next,
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                        resolving = false
                        resolveNext()
                    }

                    override fun onServiceResolved(info: NsdServiceInfo) {
                        val host = info.host
                        if (host is Inet4Address) {
                            val hostAddress = host.hostAddress
                            if (!hostAddress.isNullOrBlank()) {
                                val id = info.attributes["id"]?.toString(Charsets.UTF_8).orEmpty()
                                discovered[info.serviceName] = RemoteDiscoveryInfo(
                                    name = info.serviceName,
                                    host = hostAddress,
                                    port = info.port,
                                    deviceId = id,
                                )
                                trySend(discovered.values.toList())
                            }
                        }
                        resolving = false
                        resolveNext()
                    }
                },
            )
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onServiceFound(info: NsdServiceInfo) {
                if (info.serviceType.trimEnd('.').endsWith(SERVICE_TYPE.trimEnd('.'))) {
                    pending.addLast(info)
                    resolveNext()
                }
            }

            override fun onServiceLost(info: NsdServiceInfo) {
                discovered.remove(info.serviceName)
                trySend(discovered.values.toList())
            }

            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                close()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }

        runCatching { manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure { close() }

        awaitClose {
            runCatching { manager.stopServiceDiscovery(listener) }
        }
    }

    companion object {
        private const val TAG = "RemoteDiscovery"
        private const val SERVICE_TYPE = "_yaiplayer._tcp."
    }
}
