package tv.own.owntv.core.stalker

import java.security.MessageDigest
import kotlin.random.Random

/**
 * Helpers for portals that sign a login to a "device": a random MAC in the MAG range, and the serial
 * number / device ids a typical STB emulator derives from that MAC. Deterministic, so the same MAC
 * always yields the same identity and a portal that remembers the first one it saw keeps matching.
 */
object StalkerDeviceDerivation {
    private const val MAG_PREFIX = "00:1A:79"

    /** A random MAC with the MAG vendor prefix, canonical `AA:BB:CC:DD:EE:FF` form. */
    fun randomMac(random: Random = Random.Default): String {
        val tail = (0 until 3).joinToString(":") { "%02X".format(random.nextInt(256)) }
        return "$MAG_PREFIX:$tail"
    }

    /** Serial number, device id and device id 2 derived from [canonicalMac] (upper-case hex). */
    fun identityFor(canonicalMac: String): StalkerDeviceIdentity {
        val serial = hex(digest("MD5", canonicalMac)).take(13)
        return StalkerDeviceIdentity(
            serialNumber = serial,
            deviceId = hex(digest("SHA-256", canonicalMac)),
            deviceId2 = hex(digest("SHA-256", serial)),
        )
    }

    private fun digest(algorithm: String, text: String): ByteArray =
        MessageDigest.getInstance(algorithm).digest(text.toByteArray(Charsets.UTF_8))

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02X".format(it) }
}
