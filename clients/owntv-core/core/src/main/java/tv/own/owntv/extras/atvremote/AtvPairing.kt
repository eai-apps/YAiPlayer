// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.atvremote

import java.io.Closeable
import java.io.IOException

/** The TV answered with a status other than OK (for example 402: the code was wrong). */
class AtvPairingException(val status: Int, message: String) : IOException(message) {
    val wrongCode: Boolean get() = status == STATUS_BAD_SECRET

    companion object {
        const val STATUS_BAD_SECRET = 402
        /** Set locally when the typed code cannot belong to this TV; nothing was sent. */
        const val LOCAL_CODE_MISMATCH = -1
    }
}

/**
 * Pairing on port 6467. [start] makes the TV show a six-character code; [finish] sends the secret derived from
 * that code. All calls block and belong on a background thread.
 *
 * Message order (as in the reference implementations): pairing_request → pairing_request_ack, options → options,
 * configuration → configuration_ack, secret → secret_ack.
 */
class AtvPairing private constructor(
    private val identity: AtvIdentity,
    private val channel: AtvChannel,
) : Closeable {

    /** SHA-256 of the TV certificate seen during pairing; remembered and required on the command port. */
    val tvCertificateSha256: String = AtvTls.sha256(channel.serverCertificate)

    fun start(clientName: String) {
        AtvLog.add("pairing: request as \"$clientName\"")
        channel.send(PoloMessages.pairingRequest(clientName))
        expect(PoloMessages.Kind.PAIRING_REQUEST_ACK)
        channel.send(PoloMessages.options())
        expect(PoloMessages.Kind.OPTIONS)
        channel.send(PoloMessages.configuration())
        expect(PoloMessages.Kind.CONFIGURATION_ACK)
        // The code is on the TV screen now; the user may take a while to type it.
        channel.setReadTimeout(0)
    }

    /**
     * Checks [code] locally first. A mistyped code throws [AtvPairingException] with [AtvPairingException.LOCAL_CODE_MISMATCH]
     * and leaves the TV waiting, so the user can type it again.
     */
    fun finish(code: String) {
        val secret = AtvPairingCode.secret(identity.certificate, channel.serverCertificate, code)
            ?: throw AtvPairingException(AtvPairingException.LOCAL_CODE_MISMATCH, "Code does not match")
        channel.setReadTimeout(15_000)
        channel.send(PoloMessages.secret(secret))
        expect(PoloMessages.Kind.SECRET_ACK)
    }

    private fun expect(kind: PoloMessages.Kind) {
        val msg = PoloMessages.parse(channel.receive())
        AtvLog.add("pairing: got ${msg.kind} status ${msg.status} (waiting for $kind)")
        if (msg.status != PoloMessages.STATUS_OK) throw AtvPairingException(msg.status, "TV status ${msg.status}")
        if (msg.kind != kind) throw AtvPairingException(0, "Unexpected pairing message")
    }

    override fun close() = channel.close()

    companion object {
        fun open(identity: AtvIdentity, host: String, port: Int = AtvTls.PAIRING_PORT): AtvPairing =
            AtvPairing(identity, AtvTls.connect(identity, host, port, pinnedSha256 = null))
    }
}
