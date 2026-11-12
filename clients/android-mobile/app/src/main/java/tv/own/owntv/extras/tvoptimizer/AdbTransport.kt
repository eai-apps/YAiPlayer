// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.tvoptimizer

/**
 * Transport contract for connecting to an Android TV via network ADB and executing shell commands.
 * The real implementation is [DadbTransport]; tests use a scripted fake.
 */
interface AdbTransport {
    val isConnected: Boolean
    suspend fun connect(host: String, port: Int = DEFAULT_ADB_PORT): Result<Unit>
    suspend fun runShell(cmd: String): Result<String>
    suspend fun disconnect()

    /** Installs an APK on the TV; only the real transport can. */
    suspend fun install(apk: java.io.File): Result<Unit> = Result.failure(UnsupportedOperationException())

    /** Copies a file to the TV (`/data/local/tmp/...`); only the real transport can. */
    suspend fun push(file: java.io.File, remotePath: String): Result<Unit> = Result.failure(UnsupportedOperationException())

    /** A command's raw bytes (`exec:`), e.g. a PNG from screencap; only the real transport can. */
    suspend fun execBytes(cmd: String): Result<ByteArray> = Result.failure(UnsupportedOperationException())

    companion object {
        const val DEFAULT_ADB_PORT = 5555
    }
}
