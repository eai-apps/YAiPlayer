// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
// Android parts of the system remote: the phone's key in Android Keystore, remembered TVs, and Wi-Fi discovery.
package tv.own.owntv.extras.atvremote

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedKeyManager
import java.net.Socket
import java.security.Principal
import java.security.PrivateKey
import javax.security.auth.x500.X500Principal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * RSA-2048 key generated inside Android Keystore; the private key never leaves the phone. Keystore issues the
 * self-signed certificate the TV stores at pairing. Deleting the app (or its data) means pairing again.
 */
object AtvKeystoreIdentity : AtvIdentity {
    private const val ALIAS = "yaiplayer_atv_remote_rsa_v1"

    @Synchronized
    private fun store(): KeyStore {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!store.containsAlias(ALIAS)) {
            val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setKeySize(2048)
                .setDigests(
                    KeyProperties.DIGEST_NONE,
                    KeyProperties.DIGEST_SHA256,
                    KeyProperties.DIGEST_SHA384,
                    KeyProperties.DIGEST_SHA512,
                )
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1, KeyProperties.SIGNATURE_PADDING_RSA_PSS)
                .setCertificateSubject(X500Principal("CN=atvremote"))
                .setCertificateSerialNumber(BigInteger.valueOf(System.currentTimeMillis()))
                .setCertificateNotBefore(Date(System.currentTimeMillis() - 86_400_000L))
                .setCertificateNotAfter(Date(4102444800000L))
                .build()
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore").apply { initialize(spec) }
                .generateKeyPair()
        }
        return store
    }

    override val certificate: X509Certificate
        get() = store().getCertificate(ALIAS) as X509Certificate

    override fun keyManagers(): Array<KeyManager> = arrayOf(OnlyThisKey)

    /** Offers only this alias, so other Keystore entries of the app (the player remote's EC key) are never sent. */
    private object OnlyThisKey : X509ExtendedKeyManager() {
        override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?) = ALIAS
        override fun chooseEngineClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, engine: SSLEngine?) = ALIAS
        override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?) = arrayOf(ALIAS)
        override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = null
        override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?): String? = null
        override fun getCertificateChain(alias: String?): Array<X509Certificate>? =
            if (alias == ALIAS) arrayOf(certificate) else null
        override fun getPrivateKey(alias: String?): PrivateKey? =
            if (alias == ALIAS) store().getKey(ALIAS, null) as? PrivateKey else null
    }
}

/** A TV this phone has paired with. [certSha256] is required on every later connection. */
data class AtvSavedTv(
    val id: String,
    val name: String,
    val host: String,
    val pairingPort: Int,
    val remotePort: Int,
    val certSha256: String,
    val lastUsedAt: Long,
)

/** Remembered TVs, newest first. Holds no secrets: the key is in Keystore and the TV keeps its own record. */
class AtvDeviceStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("atv_remote_devices_v1", Context.MODE_PRIVATE)
    private val _devices = MutableStateFlow(load())
    val devices: StateFlow<List<AtvSavedTv>> = _devices.asStateFlow()

    private fun load(): List<AtvSavedTv> = runCatching { decode(prefs.getString(KEY, null)) }.getOrDefault(emptyList())

    @Synchronized
    fun save(tv: AtvSavedTv) {
        val list = listOf(tv) + _devices.value.filterNot { it.id == tv.id || it.certSha256 == tv.certSha256 }
        persist(list)
    }

    @Synchronized
    fun touch(id: String, host: String) {
        val list = _devices.value.map { if (it.id == id) it.copy(host = host, lastUsedAt = System.currentTimeMillis()) else it }
        persist(list.sortedByDescending { it.lastUsedAt })
    }

    @Synchronized
    fun rename(id: String, name: String) = persist(_devices.value.map { if (it.id == id) it.copy(name = name) else it })

    @Synchronized
    fun remove(id: String) = persist(_devices.value.filterNot { it.id == id })

    private fun persist(list: List<AtvSavedTv>) {
        prefs.edit().putString(KEY, encode(list)).apply()
        _devices.value = list
    }

    companion object {
        private const val KEY = "devices"

        fun encode(list: List<AtvSavedTv>): String = JSONArray().apply {
            list.forEach {
                put(
                    JSONObject()
                        .put("id", it.id).put("name", it.name).put("host", it.host)
                        .put("pairingPort", it.pairingPort).put("remotePort", it.remotePort)
                        .put("cert", it.certSha256).put("lastUsedAt", it.lastUsedAt),
                )
            }
        }.toString()

        fun decode(json: String?): List<AtvSavedTv> {
            if (json.isNullOrBlank()) return emptyList()
            val arr = JSONArray(json)
            return (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val cert = o.optString("cert")
                if (!cert.matches(Regex("[0-9a-f]{64}"))) return@mapNotNull null
                AtvSavedTv(
                    id = o.optString("id").ifEmpty { cert },
                    name = o.optString("name"),
                    host = o.optString("host"),
                    pairingPort = o.optInt("pairingPort", AtvTls.PAIRING_PORT),
                    remotePort = o.optInt("remotePort", AtvTls.REMOTE_PORT),
                    certSha256 = cert,
                    lastUsedAt = o.optLong("lastUsedAt"),
                )
            }
        }
    }
}

/** A TV announcing `_androidtvremote2._tcp` on the Wi-Fi. [port] is its command port (6466). */
data class AtvFoundTv(val serviceName: String, val host: String, val port: Int)

/** mDNS discovery of Android TV / Google TV devices with the Google remote service. */
class AtvDiscovery(context: Context) {
    private val nsd = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val _found = MutableStateFlow<List<AtvFoundTv>>(emptyList())
    val found: StateFlow<List<AtvFoundTv>> = _found.asStateFlow()
    private var listener: NsdManager.DiscoveryListener? = null

    @Synchronized
    fun start() {
        if (listener != null) return
        _found.value = emptyList()
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onServiceFound(info: NsdServiceInfo) = resolve(info)
            override fun onServiceLost(info: NsdServiceInfo) {
                _found.value = _found.value.filterNot { it.serviceName == info.serviceName }
            }
        }
        listener = l
        runCatching { nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l) }.onFailure { listener = null }
    }

    @Synchronized
    fun stop() {
        listener?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        listener = null
    }

    @Suppress("DEPRECATION")
    private fun resolve(info: NsdServiceInfo) {
        // resolveService handles one request at a time on older Android; failures are retried on the next announcement.
        runCatching {
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
                override fun onServiceResolved(resolved: NsdServiceInfo) {
                    val address = if (Build.VERSION.SDK_INT >= 34) {
                        resolved.hostAddresses.firstOrNull { it is java.net.Inet4Address } ?: resolved.hostAddresses.firstOrNull()
                    } else {
                        resolved.host
                    } ?: return
                    if (!AtvTls.isLocalAddress(address)) return
                    val tv = AtvFoundTv(resolved.serviceName, address.hostAddress ?: return, resolved.port)
                    _found.value = (_found.value.filterNot { it.serviceName == tv.serviceName } + tv).sortedBy { it.serviceName }
                }
            })
        }
    }

    companion object {
        const val SERVICE_TYPE = "_androidtvremote2._tcp."
    }
}
