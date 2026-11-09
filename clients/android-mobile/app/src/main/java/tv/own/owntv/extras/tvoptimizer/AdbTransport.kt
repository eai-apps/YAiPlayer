// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.tvoptimizer

import kotlinx.coroutines.delay

/**
 * Transport contract for connecting to an Android TV via ADB and executing shell commands.
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

/**
 * Safe demo/stub implementation of [AdbTransport] for UI simulation without performing real network ADB operations.
 */
class FakeAdbTransport(
    private val simulateDelayMs: Long = 400L,
) : AdbTransport {

    private var connected = false
    private val executedCommands = mutableListOf<String>()

    override val isConnected: Boolean get() = connected

    val history: List<String> get() = executedCommands.toList()

    override suspend fun connect(host: String, port: Int): Result<Unit> {
        if (simulateDelayMs > 0) delay(simulateDelayMs)
        connected = true
        return Result.success(Unit)
    }

    override suspend fun runShell(cmd: String): Result<String> {
        if (!connected) return Result.failure(IllegalStateException("ADB not connected"))
        if (simulateDelayMs > 0) delay(simulateDelayMs / 2)
        executedCommands.add(cmd)
        val output = when {
            cmd.startsWith("pm disable-user") -> "Package new state: disabled-user"
            cmd.startsWith("pm enable") -> "Package new state: default"
            else -> "Success"
        }
        return Result.success(output)
    }

    override suspend fun disconnect() {
        connected = false
    }
}
