// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.atvremote

import java.io.IOException
import java.net.ConnectException
import java.net.InetAddress
import java.net.SocketTimeoutException

/**
 * «Проверить связь»: plain TCP to the pairing port (6467) and the command port (6466), then a TLS handshake on the
 * pairing port, which accepts any phone. Tells apart «no answer» (wrong address, TV asleep, Wi-Fi isolation),
 * «closed» (the TV is there but its remote service is off) and a TLS failure on the phone's side.
 */
object AtvProbe {
    enum class Port { OPEN, CLOSED, NO_ANSWER, ERROR }

    data class Result(
        val host: String,
        val pairing: Port,
        val remote: Port,
        /** null when TCP to 6467 failed; otherwise the TLS outcome («TLSv1.3» or the error). */
        val tls: String?,
        val tlsOk: Boolean,
    )

    fun port(address: InetAddress, port: Int, timeoutMs: Int = 3_000): Port = try {
        AtvTls.openSocket(address, port, timeoutMs).close()
        Port.OPEN
    } catch (e: ConnectException) {
        Port.CLOSED
    } catch (e: SocketTimeoutException) {
        Port.NO_ANSWER
    } catch (e: IOException) {
        if (e.message.orEmpty().contains("ECONNREFUSED")) Port.CLOSED
        else if (e.message.orEmpty().contains("ETIMEDOUT") || e.message.orEmpty().contains("EHOSTUNREACH")) Port.NO_ANSWER
        else Port.ERROR
    }

    /** Blocks for up to ~15 s; call off the main thread. */
    fun run(identity: AtvIdentity, host: String): Result {
        AtvLog.add("probe $host start")
        val address = InetAddress.getByName(host)
        val pairing = port(address, AtvTls.PAIRING_PORT)
        val remote = port(address, AtvTls.REMOTE_PORT)
        var tls: String? = null
        var tlsOk = false
        if (pairing == Port.OPEN) {
            try {
                AtvTls.connect(identity, host, AtvTls.PAIRING_PORT, pinnedSha256 = null).use { tls = "OK"; tlsOk = true }
            } catch (e: Exception) {
                val root = generateSequence<Throwable>(e) { it.cause }.last()
                tls = "${root.javaClass.simpleName}: ${root.message?.take(80).orEmpty()}"
            }
        }
        AtvLog.add("probe $host: 6467 $pairing, 6466 $remote, tls ${tls ?: "-"}")
        return Result(host, pairing, remote, tls, tlsOk)
    }
}
