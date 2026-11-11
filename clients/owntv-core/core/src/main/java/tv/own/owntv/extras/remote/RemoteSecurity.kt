// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.remote

import java.net.InetAddress
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object RemoteSecurity {
    fun hash(value: String): String = hex(MessageDigest.getInstance("SHA-256").digest(value.toByteArray()))
    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
    fun localAddress(host: String): InetAddress {
        require(host.matches(Regex("[0-9.]+")))
        return InetAddress.getByName(host).also { require(isLocal(it)) }
    }
    fun isLocal(address: InetAddress): Boolean = address is java.net.Inet4Address && address.isSiteLocalAddress
    fun signature(token: String, method: String, path: String, nonce: String, body: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(hash(token).toByteArray(), "HmacSHA256"))
        return hex(mac.doFinal("$method\n$path\n$nonce\n$body".toByteArray()))
    }
    fun shareableLink(value: String): Boolean = runCatching {
        val uri = java.net.URI(value)
        uri.scheme in listOf("https", "http") && !uri.host.isNullOrBlank() && uri.userInfo == null &&
            uri.rawQuery == null && uri.rawFragment == null && value.length <= 2048 &&
            !uri.path.startsWith("/live/") && !uri.path.startsWith("/movie/") && !uri.path.startsWith("/series/") &&
            uri.path.substringAfterLast('.').lowercase() in listOf("mp4", "mkv", "m3u8", "mpd", "ts", "webm")
    }.getOrDefault(false)
    fun equal(a: String, b: String): Boolean = MessageDigest.isEqual(a.toByteArray(), b.toByteArray())
}
