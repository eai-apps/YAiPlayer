// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
// Android parts of the system remote: the stored phone certificate, remembered TVs, and Wi-Fi discovery.
package tv.own.owntv.extras.atvremote

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Keeps the phone's [AtvSoftwareIdentity] in no-backup app storage, encrypted with an AES-GCM key from Android
 * Keystore (AES works with every Keystore; only the TLS signature had to leave it). Made once on first use; deleting
 * the app data means pairing again. The old Keystore RSA key (alias yaiplayer_atv_remote_rsa_v1) is removed: TVs
 * paired with it must be paired again, but it never completed a TLS 1.3 connection anyway.
 */
object AtvIdentityStore {
    private const val FILE = "atv_remote_identity_v2.bin"
    private const val PLAIN_FILE = "atv_remote_identity_v2.pem"
    private const val AES_ALIAS = "yaiplayer_atv_identity_aes_v1"
    private const val OLD_RSA_ALIAS = "yaiplayer_atv_remote_rsa_v1"
    @Volatile private var cached: AtvSoftwareIdentity? = null

    @Synchronized
    fun get(context: Context): AtvSoftwareIdentity {
        cached?.let { return it }
        val dir = context.applicationContext.noBackupFilesDir
        val enc = java.io.File(dir, FILE)
        val plain = java.io.File(dir, PLAIN_FILE)
        val loaded = runCatching {
            when {
                enc.exists() -> AtvSoftwareIdentity.decode(decrypt(enc.readBytes()))
                plain.exists() -> AtvSoftwareIdentity.decode(plain.readText())
                else -> null
            }
        }.onFailure { AtvLog.error("identity load", it) }.getOrNull()
        val identity = loaded ?: AtvSoftwareIdentity.create().also { fresh ->
            AtvLog.add("identity: new software RSA-2048 certificate")
            val pem = fresh.encode()
            val sealed = runCatching { encrypt(pem) }.onFailure { AtvLog.error("identity encrypt", it) }.getOrNull()
            if (sealed != null) {
                enc.writeBytes(sealed); plain.delete()
            } else {
                plain.writeText(pem)
            }
            runCatching {
                KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(OLD_RSA_ALIAS)
            }
        }
        cached = identity
        return identity
    }

    private fun aesKey(): javax.crypto.SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(AES_ALIAS, null) as? javax.crypto.SecretKey)?.let { return it }
        val gen = javax.crypto.KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(AES_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private fun encrypt(text: String): ByteArray {
        val c = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        c.init(javax.crypto.Cipher.ENCRYPT_MODE, aesKey())
        return c.iv + c.doFinal(text.toByteArray(Charsets.UTF_8))
    }

    private fun decrypt(data: ByteArray): String {
        val c = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        c.init(javax.crypto.Cipher.DECRYPT_MODE, aesKey(), javax.crypto.spec.GCMParameterSpec(128, data.copyOfRange(0, 12)))
        return c.doFinal(data, 12, data.size - 12).toString(Charsets.UTF_8)
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
    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
    // Some phones drop multicast (the mDNS answers) while the screen's app holds no multicast lock.
    private var multicast: android.net.wifi.WifiManager.MulticastLock? = null
    // Older Android resolves one service at a time and fails the rest (FAILURE_ALREADY_ACTIVE): with two TVs, or
    // one TV announced twice, the second was lost. Resolve them one after another instead.
    private val pending = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
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
        runCatching {
            multicast = wifi?.createMulticastLock("yaiplayer-atv")?.apply { setReferenceCounted(false); acquire() }
        }
        runCatching { nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l) }.onFailure { listener = null }
    }

    @Synchronized
    fun stop() {
        listener?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        listener = null
        runCatching { multicast?.release() }
        multicast = null
        synchronized(pending) { pending.clear(); resolving = false }
    }

    private fun resolve(info: NsdServiceInfo) {
        synchronized(pending) {
            if (pending.none { it.serviceName == info.serviceName }) pending.addLast(info)
            if (resolving) return
            resolving = true
        }
        next()
    }

    @Suppress("DEPRECATION")
    private fun next() {
        val info = synchronized(pending) {
            pending.removeFirstOrNull() ?: run { resolving = false; return }
        }
        val ok = runCatching {
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = next()
                override fun onServiceResolved(resolved: NsdServiceInfo) {
                    val address = if (Build.VERSION.SDK_INT >= 34) {
                        resolved.hostAddresses.firstOrNull { it is java.net.Inet4Address } ?: resolved.hostAddresses.firstOrNull()
                    } else {
                        resolved.host
                    }
                    // No address filter here: mDNS answers only reach this phone from its own link. The connection
                    // itself still checks the address (AtvTls.isHomeAddress).
                    address?.hostAddress?.let { host ->
                        val tv = AtvFoundTv(resolved.serviceName, host, resolved.port)
                        _found.value = (_found.value.filterNot { it.serviceName == tv.serviceName } + tv).sortedBy { it.serviceName }
                    }
                    next()
                }
            })
        }.isSuccess
        if (!ok) next()
    }

    companion object {
        const val SERVICE_TYPE = "_androidtvremote2._tcp."
    }
}

/** Second try for the remote's connections: a socket bound to the home Wi-Fi (see [AtvTls.wifiSocketProvider]). */
object AtvLocalNetwork {
    fun install(context: Context) {
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return
        AtvTls.wifiSocketProvider = {
            // createSocket() on a Network's factory binds the socket to that network.
            runCatching { homeNetwork(cm)?.socketFactory?.createSocket() }.getOrNull()
        }
    }

    @Suppress("DEPRECATION")
    private fun homeNetwork(cm: android.net.ConnectivityManager): android.net.Network? =
        cm.allNetworks.firstOrNull { n ->
            val caps = cm.getNetworkCapabilities(n) ?: return@firstOrNull false
            caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VPN) &&
                (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET))
        }
}

/** What the remote's "network check" shows when no TV is found (owner 12.11: the phone said "another network"). */
data class AtvNetworkCheck(val wifi: Boolean, val vpn: Boolean, val addresses: List<String>) {
    companion object {
        fun read(context: Context): AtvNetworkCheck {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            val caps = runCatching { cm?.getNetworkCapabilities(cm.activeNetwork) }.getOrNull()
            val wifi = caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true ||
                caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) == true
            val vpn = caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) == true
            val addresses = runCatching {
                java.net.NetworkInterface.getNetworkInterfaces().toList()
                    .filter { it.isUp && !it.isLoopback }
                    .flatMap { nic -> nic.inetAddresses.toList() }
                    .filterIsInstance<java.net.Inet4Address>()
                    .mapNotNull { it.hostAddress }
            }.getOrDefault(emptyList())
            return AtvNetworkCheck(wifi, vpn, addresses)
        }
    }
}
