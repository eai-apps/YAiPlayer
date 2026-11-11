// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
// Minimal protobuf wire format (varint, length-delimited) for the Android TV Remote v2 protocol.
// Field numbers follow polo.proto (AOSP, Apache-2.0) and remotemessage.proto (louis49/androidtv-remote, MIT),
// see docs/THIRD_PARTY.md. No generated code and no protobuf runtime are used.
package tv.own.owntv.extras.atvremote

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** Thrown when the bytes from the TV are not a valid protobuf message. */
class AtvProtoException(message: String) : IOException(message)

internal class ProtoWriter {
    private val out = ByteArrayOutputStream()

    fun varint(field: Int, value: Long): ProtoWriter {
        tag(field, 0)
        rawVarint(value)
        return this
    }

    fun varint(field: Int, value: Int): ProtoWriter = varint(field, value.toLong())

    fun bool(field: Int, value: Boolean): ProtoWriter = varint(field, if (value) 1L else 0L)

    fun bytes(field: Int, value: ByteArray): ProtoWriter {
        tag(field, 2)
        rawVarint(value.size.toLong())
        out.write(value)
        return this
    }

    fun string(field: Int, value: String): ProtoWriter = bytes(field, value.toByteArray(Charsets.UTF_8))

    fun message(field: Int, body: ProtoWriter): ProtoWriter = bytes(field, body.toByteArray())

    fun toByteArray(): ByteArray = out.toByteArray()

    private fun tag(field: Int, wireType: Int) = rawVarint(((field shl 3) or wireType).toLong())

    private fun rawVarint(value: Long) {
        var v = value
        while (true) {
            if (v and 0x7FL.inv() == 0L) {
                out.write(v.toInt())
                return
            }
            out.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
    }
}

/** One decoded field: [varint] for wire type 0, [bytes] for wire type 2. Other wire types are skipped. */
internal class ProtoField(val number: Int, val varint: Long, val bytes: ByteArray?)

internal object ProtoReader {
    fun fields(data: ByteArray): List<ProtoField> {
        val result = ArrayList<ProtoField>()
        var pos = 0
        while (pos < data.size) {
            val (key, afterKey) = readVarint(data, pos)
            pos = afterKey
            val number = (key ushr 3).toInt()
            if (number <= 0) throw AtvProtoException("Bad field number")
            when ((key and 7).toInt()) {
                0 -> {
                    val (v, next) = readVarint(data, pos)
                    result += ProtoField(number, v, null)
                    pos = next
                }
                1 -> pos = skip(data, pos, 8)
                2 -> {
                    val (len, next) = readVarint(data, pos)
                    if (len < 0 || len > data.size - next) throw AtvProtoException("Bad length")
                    result += ProtoField(number, 0, data.copyOfRange(next, next + len.toInt()))
                    pos = next + len.toInt()
                }
                5 -> pos = skip(data, pos, 4)
                else -> throw AtvProtoException("Unsupported wire type")
            }
        }
        return result
    }

    private fun skip(data: ByteArray, pos: Int, n: Int): Int {
        if (pos + n > data.size) throw AtvProtoException("Truncated field")
        return pos + n
    }

    fun readVarint(data: ByteArray, start: Int): Pair<Long, Int> {
        var result = 0L
        var shift = 0
        var pos = start
        while (true) {
            if (pos >= data.size) throw AtvProtoException("Truncated varint")
            if (shift >= 64) throw AtvProtoException("Varint too long")
            val b = data[pos++].toInt() and 0xFF
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) return result to pos
            shift += 7
        }
    }
}

internal fun List<ProtoField>.msg(number: Int): List<ProtoField>? =
    lastOrNull { it.number == number && it.bytes != null }?.let { ProtoReader.fields(it.bytes!!) }

internal fun List<ProtoField>.has(number: Int): Boolean = any { it.number == number }

internal fun List<ProtoField>.int(number: Int): Long = lastOrNull { it.number == number && it.bytes == null }?.varint ?: 0L

internal fun List<ProtoField>.str(number: Int): String =
    lastOrNull { it.number == number && it.bytes != null }?.bytes?.toString(Charsets.UTF_8) ?: ""

/** Messages on both ports are prefixed with their length as a varint. */
internal object AtvFraming {
    /** The TV never sends large messages; voice payloads (not used here) are 20 KB. */
    const val MAX_MESSAGE = 64 * 1024

    fun write(out: OutputStream, message: ByteArray) {
        val prefix = ProtoWriter()
        // A varint with no tag: write it through a throwaway field and strip the tag byte.
        val withTag = prefix.varint(1, message.size.toLong()).toByteArray()
        out.write(withTag, 1, withTag.size - 1)
        out.write(message)
        out.flush()
    }

    fun read(input: InputStream): ByteArray {
        var length = 0L
        var shift = 0
        while (true) {
            val b = input.read()
            if (b < 0) throw EOFException("Connection closed")
            length = length or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) break
            shift += 7
            if (shift > 28) throw AtvProtoException("Bad length prefix")
        }
        if (length > MAX_MESSAGE) throw AtvProtoException("Message too large")
        val buf = ByteArray(length.toInt())
        var read = 0
        while (read < buf.size) {
            val n = input.read(buf, read, buf.size - read)
            if (n < 0) throw EOFException("Connection closed")
            read += n
        }
        return buf
    }
}
