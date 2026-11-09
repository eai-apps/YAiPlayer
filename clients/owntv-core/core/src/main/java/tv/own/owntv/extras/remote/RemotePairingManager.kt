// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.remote

import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random

interface RemotePairingStore {
    fun loadDevices(): List<PairedDevice>
    fun saveDevices(devices: List<PairedDevice>)
}

class InMemoryRemotePairingStore : RemotePairingStore {
    private val devices = CopyOnWriteArrayList<PairedDevice>()
    override fun loadDevices(): List<PairedDevice> = devices.toList()
    override fun saveDevices(devices: List<PairedDevice>) {
        this.devices.clear()
        this.devices.addAll(devices)
    }
}

class SharedPrefsRemotePairingStore(context: android.content.Context) : RemotePairingStore {
    private val prefs = context.applicationContext.getSharedPreferences("remote_pairing_store", android.content.Context.MODE_PRIVATE)

    override fun loadDevices(): List<PairedDevice> {
        val raw = prefs.getString("paired_devices", null) ?: return emptyList()
        return runCatching {
            val arr = org.json.JSONArray(raw)
            List(arr.length()) { i -> PairedDevice.fromJsonObject(arr.getJSONObject(i)) }
        }.getOrDefault(emptyList())
    }

    override fun saveDevices(devices: List<PairedDevice>) {
        val arr = org.json.JSONArray()
        devices.forEach { arr.put(it.toJsonObject()) }
        prefs.edit().putString("paired_devices", arr.toString()).apply()
    }
}

class RemotePairingManager(
    private val store: RemotePairingStore = InMemoryRemotePairingStore(),
) {
    sealed interface PairAttemptResult {
        data class Success(val token: String) : PairAttemptResult
        data object InvalidCode : PairAttemptResult
        data class RateLimited(val remainingSeconds: Long) : PairAttemptResult
    }

    private val pairedDevices = CopyOnWriteArrayList<PairedDevice>()

    @Volatile
    var currentPin: String? = null
        private set

    @Volatile
    var pinExpiresAtMs: Long = 0L
        private set

    @Volatile
    private var failedAttempts: Int = 0

    @Volatile
    private var lockoutUntilMs: Long = 0L

    init {
        pairedDevices.addAll(store.loadDevices())
    }

    fun getPairedDevices(): List<PairedDevice> = pairedDevices.toList()

    fun generateNewCode(): String {
        val pin = "%06d".format(Random.nextInt(1_000_000))
        currentPin = pin
        pinExpiresAtMs = System.currentTimeMillis() + CODE_LIFETIME_MS
        return pin
    }

    fun pinRemainingSeconds(): Long {
        val remaining = (pinExpiresAtMs - System.currentTimeMillis()) / 1000L
        return remaining.coerceAtLeast(0L)
    }

    fun isLockedOut(): Boolean = System.currentTimeMillis() < lockoutUntilMs

    fun lockoutRemainingSeconds(): Long {
        val remaining = (lockoutUntilMs - System.currentTimeMillis()) / 1000L
        return remaining.coerceAtLeast(0L)
    }

    @Synchronized
    fun attemptPair(code: String, clientName: String, clientId: String): PairAttemptResult {
        val now = System.currentTimeMillis()
        if (now < lockoutUntilMs) {
            return PairAttemptResult.RateLimited((lockoutUntilMs - now) / 1000L)
        }

        val activePin = currentPin
        val isValid = activePin != null && now <= pinExpiresAtMs && activePin == code.trim()

        if (!isValid) {
            failedAttempts++
            if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
                lockoutUntilMs = now + LOCKOUT_DURATION_MS
                failedAttempts = 0
                return PairAttemptResult.RateLimited(LOCKOUT_DURATION_MS / 1000L)
            }
            return PairAttemptResult.InvalidCode
        }

        // Success: reset rate limiting counters and generate token
        failedAttempts = 0
        lockoutUntilMs = 0L
        currentPin = null
        pinExpiresAtMs = 0L

        val token = generateSecureToken()
        val existingIndex = pairedDevices.indexOfFirst { it.id == clientId }
        val newDevice = PairedDevice(
            id = clientId,
            name = clientName.ifBlank { "Remote" },
            token = token,
            pairedAtMs = now,
        )

        if (existingIndex >= 0) {
            pairedDevices[existingIndex] = newDevice
        } else {
            pairedDevices.add(newDevice)
        }
        store.saveDevices(pairedDevices.toList())

        return PairAttemptResult.Success(token)
    }

    fun isValidToken(token: String?): Boolean {
        if (token.isNullOrBlank()) return false
        return pairedDevices.any { it.token == token }
    }

    @Synchronized
    fun forgetDevice(deviceId: String) {
        val removed = pairedDevices.removeAll { it.id == deviceId }
        if (removed) {
            store.saveDevices(pairedDevices.toList())
        }
    }

    @Synchronized
    fun clearAll() {
        pairedDevices.clear()
        store.saveDevices(emptyList())
    }

    private fun generateSecureToken(): String {
        val bytes = ByteArray(24)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val CODE_LIFETIME_MS = 2 * 60 * 1000L // 2 minutes
        const val LOCKOUT_DURATION_MS = 5 * 60 * 1000L // 5 minutes
        const val MAX_FAILED_ATTEMPTS = 5
    }
}
