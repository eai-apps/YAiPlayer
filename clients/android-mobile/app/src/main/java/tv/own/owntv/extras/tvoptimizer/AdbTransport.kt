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

    companion object {
        const val DEFAULT_ADB_PORT = 5555
    }
}
