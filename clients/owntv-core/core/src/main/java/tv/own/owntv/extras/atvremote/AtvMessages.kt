// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
// Messages of the Android TV Remote v2 protocol. Field numbers: polo.proto (pairing, port 6467) and
// remotemessage.proto (commands, port 6466); see docs/THIRD_PARTY.md.
package tv.own.owntv.extras.atvremote

/** Pairing messages (polo.proto, OuterMessage). */
object PoloMessages {
    const val PROTOCOL_VERSION = 2
    const val STATUS_OK = 200
    private const val ENCODING_HEXADECIMAL = 3
    private const val ROLE_INPUT = 1
    const val CODE_LENGTH = 6

    private fun outer() = ProtoWriter().varint(1, PROTOCOL_VERSION).varint(2, STATUS_OK)

    private fun encoding() = ProtoWriter().varint(1, ENCODING_HEXADECIMAL).varint(2, CODE_LENGTH)

    fun pairingRequest(clientName: String): ByteArray =
        outer().message(10, ProtoWriter().string(1, "atvremote").string(2, clientName)).toByteArray()

    fun options(): ByteArray =
        outer().message(20, ProtoWriter().message(1, encoding()).varint(3, ROLE_INPUT)).toByteArray()

    fun configuration(): ByteArray =
        outer().message(30, ProtoWriter().message(1, encoding()).varint(2, ROLE_INPUT)).toByteArray()

    fun secret(secret: ByteArray): ByteArray = outer().message(40, ProtoWriter().bytes(1, secret)).toByteArray()

    enum class Kind { PAIRING_REQUEST_ACK, OPTIONS, CONFIGURATION_ACK, SECRET_ACK, OTHER }

    class Incoming(val status: Int, val kind: Kind)

    fun parse(data: ByteArray): Incoming {
        val f = ProtoReader.fields(data)
        val kind = when {
            f.has(11) -> Kind.PAIRING_REQUEST_ACK
            f.has(20) -> Kind.OPTIONS
            f.has(31) -> Kind.CONFIGURATION_ACK
            f.has(41) -> Kind.SECRET_ACK
            else -> Kind.OTHER
        }
        return Incoming(f.int(2).toInt(), kind)
    }
}

/** Feature bits announced in remote_configure / remote_set_active. */
object AtvFeatures {
    const val PING = 1
    const val KEY = 2
    const val IME = 4
    const val POWER = 32
    const val VOLUME = 64
    const val APP_LINK = 512
    const val REQUESTED = PING or KEY or IME or POWER or VOLUME or APP_LINK
}

/** Key press direction (RemoteDirection). */
enum class AtvDirection(val wire: Int) { START_LONG(1), END_LONG(2), SHORT(3) }

/** Command messages (remotemessage.proto, RemoteMessage). */
object RemoteMessages {
    fun configureReply(features: Int): ByteArray {
        val deviceInfo = ProtoWriter().varint(3, 1).string(4, "1").string(5, "atvremote").string(6, "1.0.0")
        return ProtoWriter().message(1, ProtoWriter().varint(1, features).message(2, deviceInfo)).toByteArray()
    }

    fun setActive(features: Int): ByteArray = ProtoWriter().message(2, ProtoWriter().varint(1, features)).toByteArray()

    fun pingResponse(value: Long): ByteArray = ProtoWriter().message(9, intField(1, value)).toByteArray()

    fun keyInject(keyCode: Int, direction: AtvDirection): ByteArray =
        ProtoWriter().message(10, intField(1, keyCode.toLong()).varint(2, direction.wire)).toByteArray()

    /** Inserts [text] at the cursor of the focused field on the TV. */
    fun imeText(text: String, imeCounter: Int, fieldCounter: Int): ByteArray {
        val obj = intField(1, text.length.toLong()).also { it.varintIfSet(2, text.length.toLong()) }
        if (text.isNotEmpty()) obj.string(3, text)
        val edit = ProtoWriter().varint(1, 1).message(2, obj)
        val batch = intField(1, imeCounter.toLong()).also { it.varintIfSet(2, fieldCounter.toLong()) }.message(3, edit)
        return ProtoWriter().message(21, batch).toByteArray()
    }

    fun appLink(link: String): ByteArray = ProtoWriter().message(90, ProtoWriter().string(1, link)).toByteArray()

    // proto3: zero values are not written.
    private fun intField(field: Int, value: Long): ProtoWriter = ProtoWriter().varintIfSet(field, value)

    private fun ProtoWriter.varintIfSet(field: Int, value: Long): ProtoWriter =
        if (value != 0L) varint(field, value) else this

    sealed interface Incoming
    data class Configure(val features: Int, val model: String, val vendor: String, val appVersion: String) : Incoming
    data object SetActive : Incoming
    data class Ping(val value: Long) : Incoming
    data class Started(val on: Boolean) : Incoming
    data class Volume(val level: Int, val max: Int, val muted: Boolean) : Incoming
    data class ImeCounters(val imeCounter: Int?, val fieldCounter: Int?, val appPackage: String?) : Incoming
    /** remote_ime_show_request: a text field got focus on the TV and its keyboard opened. */
    data class ImeShow(val fieldCounter: Int?) : Incoming
    data object Error : Incoming
    data object Other : Incoming

    fun parse(data: ByteArray): Incoming {
        val f = ProtoReader.fields(data)
        f.msg(1)?.let { cfg ->
            val info = cfg.msg(2).orEmpty()
            return Configure(cfg.int(1).toInt(), info.str(1), info.str(2), info.str(6))
        }
        if (f.has(2)) return SetActive
        if (f.has(3)) return Error
        f.msg(8)?.let { return Ping(it.int(1)) }
        f.msg(20)?.let { inject ->
            val app = inject.msg(1).orEmpty()
            val field = inject.msg(2).orEmpty()
            return ImeCounters(
                app.int(1).toInt().takeIf { it != 0 },
                field.int(1).toInt().takeIf { it != 0 },
                app.str(12).ifEmpty { null },
            )
        }
        f.msg(21)?.let { batch ->
            return ImeCounters(batch.int(1).toInt().takeIf { it != 0 }, batch.int(2).toInt().takeIf { it != 0 }, null)
        }
        f.msg(22)?.let { show ->
            return ImeShow(show.msg(2)?.int(1)?.toInt()?.takeIf { it != 0 })
        }
        f.msg(40)?.let { return Started(it.int(1) != 0L) }
        f.msg(50)?.let { return Volume(it.int(7).toInt(), it.int(6).toInt(), it.int(8) != 0L) }
        return Other
    }
}

/** Android key codes used by the remote (android/keycodes.h, mirrored in RemoteKeyCode). */
object AtvKeys {
    const val HOME = 3
    const val BACK = 4
    const val DIGIT_0 = 7
    const val DPAD_UP = 19
    const val DPAD_DOWN = 20
    const val DPAD_LEFT = 21
    const val DPAD_RIGHT = 22
    const val DPAD_CENTER = 23
    const val VOLUME_UP = 24
    const val VOLUME_DOWN = 25
    const val POWER = 26
    const val DEL = 67
    const val ENTER = 66
    const val MENU = 82
    const val SEARCH = 84
    const val MEDIA_PLAY_PAUSE = 85
    const val MEDIA_STOP = 86
    const val MEDIA_NEXT = 87
    const val MEDIA_PREVIOUS = 88
    const val MEDIA_REWIND = 89
    const val MEDIA_FAST_FORWARD = 90
    const val VOLUME_MUTE = 164
    const val CHANNEL_UP = 166
    const val CHANNEL_DOWN = 167
    const val GUIDE = 172
    const val SETTINGS = 176
    const val TV = 170
    const val TV_INPUT = 178

    fun digit(n: Int): Int {
        require(n in 0..9)
        return DIGIT_0 + n
    }
}

/**
 * Live typing from the phone's keyboard into the TV's field: what changed between two states of the phone's text,
 * as backspaces to press and then text to insert at the TV's cursor (common prefix kept).
 */
object AtvTyping {
    data class Edit(val deletes: Int, val insert: String)

    fun diff(old: String, new: String): Edit {
        var common = 0
        val max = minOf(old.length, new.length)
        while (common < max && old[common] == new[common]) common++
        return Edit(old.length - common, new.substring(common))
    }
}
