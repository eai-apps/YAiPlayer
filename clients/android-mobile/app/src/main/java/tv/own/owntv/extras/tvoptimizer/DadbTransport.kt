// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.tvoptimizer

import android.content.Context
import dadb.AdbKeyPair
import dadb.Dadb
import java.io.File
import java.net.InetAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Network ADB from the phone, through dadb (mobile-dev-inc/dadb, Apache-2.0). The phone's ADB key is created once in
 * app-private storage that is excluded from backups; the TV asks «Allow debugging?» the first time and remembers it
 * when «Always allow» is ticked.
 */
class DadbTransport(context: Context) : AdbTransport {
    private val dir = File(context.applicationContext.noBackupFilesDir, "adb")
    private val privateKey = File(dir, "adbkey")
    private val publicKey = File(dir, "adbkey.pub")

    @Volatile private var dadb: Dadb? = null

    override val isConnected: Boolean get() = dadb != null

    private fun keyPair(): AdbKeyPair {
        if (!privateKey.isFile || !publicKey.isFile) AdbKeyPair.generate(privateKey, publicKey)
        return AdbKeyPair.read(privateKey, publicKey)
    }

    override suspend fun connect(host: String, port: Int): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val address = InetAddress.getByName(host)
            // Same rule as the remote: only the home network (private ranges or the phone's own subnet).
            require(tv.own.owntv.extras.atvremote.AtvTls.isHomeAddress(address)) { "not local" }
            disconnectNow()
            // The first command waits for the user to tap «Allow» on the TV: up to a minute.
            val d = Dadb.create(address.hostAddress!!, port, keyPair(), connectTimeout = 5_000, socketTimeout = 60_000)
            try {
                check(d.shell("echo ok").output.trim() == "ok") { "no shell" }
            } catch (t: Throwable) {
                runCatching { d.close() }
                throw t
            }
            dadb = d
        }
    }

    override suspend fun runShell(cmd: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val d = dadb ?: error("not connected")
            d.shell(cmd).allOutput
        }
    }

    /** Installs (or updates) an APK on the TV for user 0. */
    override suspend fun install(apk: File): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val d = dadb ?: error("not connected")
            val result = d.install(apk, "-r", "--user", "0")
            check(result is dadb.InstallResult.Success) { (result as? dadb.InstallResult.Failure)?.reason ?: "install failed" }
        }
    }

    override suspend fun execBytes(cmd: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val d = dadb ?: error("not connected")
            d.open("exec:$cmd").use { it.source.readByteArray() }
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) { disconnectNow() }

    private fun disconnectNow() {
        val d = dadb
        dadb = null
        runCatching { d?.close() }
    }
}
