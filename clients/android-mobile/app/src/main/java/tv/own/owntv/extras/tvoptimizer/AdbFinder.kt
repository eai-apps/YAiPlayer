// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.tvoptimizer

import android.content.Context
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import tv.own.owntv.extras.atvremote.AtvDeviceStore
import tv.own.owntv.extras.atvremote.AtvLog
import tv.own.owntv.extras.atvremote.AtvNetworkCheck

/**
 * Finds the TV box's network debugging (ADB, port 5555) without typing an address (owner 12.11: «other apps just
 * find it on the Wi-Fi»). Tried in order: the address the caller already knows, the TV boxes the remote paired
 * with or found, then every address of the phone's own Wi-Fi subnet. The first open port 5555 wins.
 */
object AdbFinder {
    private const val PROBE_MS = 600

    /** The addresses of a /24 around an IPv4 address of the phone, except the phone itself. */
    fun subnetHosts(phoneIp: String): List<String> {
        val parts = phoneIp.split('.')
        if (parts.size != 4 || parts.any { it.toIntOrNull() !in 0..255 }) return emptyList()
        val prefix = parts.take(3).joinToString(".")
        return (1..254).map { "$prefix.$it" }.filter { it != phoneIp }
    }

    /** Known addresses first, without duplicates; only IPv4 literals. */
    fun candidates(preferred: List<String?>, known: List<String>): List<String> =
        (preferred.filterNotNull() + known).map { it.trim() }
            .filter { h -> h.split('.').let { p -> p.size == 4 && p.all { it.toIntOrNull() in 0..255 } } }
            .distinct()

    private fun open(host: String, port: Int, timeoutMs: Int = PROBE_MS): Boolean = runCatching {
        Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs) }
        true
    }.getOrDefault(false)

    suspend fun find(context: Context, preferred: List<String?>, port: Int = AdbTransport.DEFAULT_ADB_PORT): String? =
        withContext(Dispatchers.IO) {
            val known = runCatching { AtvDeviceStore(context).devices.value.map { it.host } }.getOrDefault(emptyList())
            val first = candidates(preferred, known)
            first.firstOrNull { open(it, port, 1_500) }?.let {
                AtvLog.add("adb: found $it:$port (known address)")
                return@withContext it
            }
            val phone = AtvNetworkCheck.read(context).addresses.filter { ip ->
                runCatching { java.net.InetAddress.getByName(ip).isSiteLocalAddress }.getOrDefault(false)
            }
            val scan = phone.flatMap { subnetHosts(it) }.filter { it !in first }.distinct()
            val gate = Semaphore(48)
            val found = coroutineScope {
                scan.map { h -> async { gate.withPermit { h.takeIf { open(it, port) } } } }.awaitAll().filterNotNull()
            }
            AtvLog.add("adb: subnet scan ${scan.size} addresses, open 5555: ${found.joinToString()}")
            found.firstOrNull()
        }
}
