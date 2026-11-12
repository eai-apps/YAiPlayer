// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.atvremote

import java.io.Closeable
import java.io.IOException

/** The TV does not know this phone (never paired, or the TV forgot it after a reset). */
class AtvNeedsPairingException(cause: Throwable) : IOException("Pairing required", cause)

/** What the TV reported about itself; delivered on the session's reader thread. */
interface AtvSessionListener {
    fun onReady(info: AtvTvInfo) {}
    fun onPowerState(on: Boolean) {}
    fun onVolume(level: Int, max: Int, muted: Boolean) {}
    fun onCurrentApp(packageName: String) {}
    /** A text field got focus on the TV: the phone can open its own keyboard and type into it. */
    fun onKeyboardRequested() {}
    /** The connection ended; [error] is null when [AtvRemoteSession.close] was called. */
    fun onClosed(error: Throwable?) {}
}

data class AtvTvInfo(val model: String, val vendor: String, val features: Int)

/**
 * Command connection on port 6466. Answers the TV's configure / set_active / ping messages and sends key presses,
 * text and app launches. [connect] blocks; the reader runs on its own daemon thread.
 */
class AtvRemoteSession private constructor(
    private val channel: AtvChannel,
    private val listener: AtvSessionListener,
) : Closeable {
    @Volatile private var closedByUser = false
    @Volatile private var features = AtvFeatures.REQUESTED
    @Volatile private var imeCounter = 0
    @Volatile private var fieldCounter = 0
    @Volatile var ready = false
        private set
    @Volatile var info: AtvTvInfo? = null
        private set

    private val reader = Thread({ readLoop() }, "atv-remote-reader").apply { isDaemon = true }

    /**
     * The first message must arrive before [connect] returns. With TLS 1.3 the client's handshake ends before the TV
     * has checked the phone's certificate; a TV that does not know the phone then just closes the line. Reading the
     * first message here turns that into [AtvNeedsPairingException] instead of a vague «connection lost».
     */
    private fun awaitFirst() {
        channel.setReadTimeout(FIRST_MESSAGE_TIMEOUT_MS)
        val first = try {
            channel.receive()
        } catch (e: java.net.SocketTimeoutException) {
            AtvLog.error("remote: no first message", e)
            channel.close()
            throw e
        } catch (e: java.io.IOException) {
            AtvLog.error("remote: closed before the first message (TV does not know this phone)", e)
            channel.close()
            throw AtvNeedsPairingException(e)
        }
        val msg = RemoteMessages.parse(first)
        AtvLog.add("remote: first message ${msg.javaClass.simpleName}")
        handle(msg)
    }

    private fun readLoop() {
        var error: Throwable? = null
        try {
            // The TV pings every few seconds; silence for this long means the connection is gone.
            channel.setReadTimeout(READ_TIMEOUT_MS)
            while (!closedByUser) handle(RemoteMessages.parse(channel.receive()))
        } catch (t: Throwable) {
            if (!closedByUser) {
                error = t
                AtvLog.error("remote: connection ended", t)
            }
        } finally {
            ready = false
            channel.close()
            listener.onClosed(error)
        }
    }

    private fun handle(msg: RemoteMessages.Incoming) {
        when (msg) {
            is RemoteMessages.Configure -> {
                features = AtvFeatures.REQUESTED and msg.features
                info = AtvTvInfo(msg.model, msg.vendor, msg.features)
                AtvLog.add("remote: configure from ${msg.vendor} ${msg.model}, features ${msg.features}")
                channel.send(RemoteMessages.configureReply(features))
            }
            RemoteMessages.SetActive -> channel.send(RemoteMessages.setActive(features))
            is RemoteMessages.Ping -> channel.send(RemoteMessages.pingResponse(msg.value))
            is RemoteMessages.Started -> {
                if (!ready) AtvLog.add("remote: ready, TV ${if (msg.on) "on" else "asleep"}")
                listener.onPowerState(msg.on)
                if (!ready) {
                    ready = true
                    listener.onReady(info ?: AtvTvInfo("", "", features))
                }
            }
            is RemoteMessages.Volume -> listener.onVolume(msg.level, msg.max, msg.muted)
            is RemoteMessages.ImeCounters -> {
                msg.imeCounter?.let { imeCounter = it }
                msg.fieldCounter?.let { fieldCounter = it }
                msg.appPackage?.let { listener.onCurrentApp(it) }
            }
            is RemoteMessages.ImeShow -> {
                msg.fieldCounter?.let { fieldCounter = it }
                listener.onKeyboardRequested()
            }
            RemoteMessages.Error, RemoteMessages.Other -> Unit
        }
    }

    fun key(keyCode: Int, direction: AtvDirection = AtvDirection.SHORT) =
        channel.send(RemoteMessages.keyInject(keyCode, direction))

    /** Types [text] into the field that has focus on the TV (the TV's keyboard must be open). */
    fun text(text: String) {
        require(text.isNotEmpty())
        channel.send(RemoteMessages.imeText(text, imeCounter, fieldCounter))
    }

    /** A link such as `https://www.youtube.com`, or a bare package name, which becomes `market://launch?id=`. */
    fun launch(appLinkOrPackage: String) = channel.send(RemoteMessages.appLink(AtvAppLinks.normalize(appLinkOrPackage)))

    fun supports(feature: Int): Boolean = features and feature == feature

    override fun close() {
        closedByUser = true
        channel.close()
    }

    companion object {
        const val READ_TIMEOUT_MS = 20_000
        const val FIRST_MESSAGE_TIMEOUT_MS = 8_000

        fun connect(
            identity: AtvIdentity,
            host: String,
            pinnedSha256: String?,
            listener: AtvSessionListener,
            port: Int = AtvTls.REMOTE_PORT,
        ): AtvRemoteSession {
            val channel = try {
                AtvTls.connect(identity, host, port, pinnedSha256)
            } catch (e: AtvHandshakeException) {
                // The TV ends the handshake (alert, or just closing the socket) when it does not know the phone.
                throw AtvNeedsPairingException(e)
            }
            return AtvRemoteSession(channel, listener).also {
                it.awaitFirst()
                it.reader.start()
            }
        }
    }
}

object AtvAppLinks {
    private val PACKAGE = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
    private val SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")

    fun normalize(value: String): String {
        val v = value.trim()
        require(v.isNotEmpty())
        if (SCHEME.containsMatchIn(v)) return v
        require(PACKAGE.matches(v)) { "Not an app link or package name" }
        return "market://launch?id=$v"
    }
}
