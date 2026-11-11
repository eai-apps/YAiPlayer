// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.remote

import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList


interface RemotePairingStore {
    fun loadDevices(): List<PairedDevice>
    fun saveDevices(devices: List<PairedDevice>)
    fun loadFailures(): Pair<Int, Long> = 0 to 0L
    fun saveFailures(count: Int, until: Long) {}
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

    override fun loadFailures() = prefs.getInt("failures", 0) to prefs.getLong("blocked_until", 0L)
    override fun saveFailures(count: Int, until: Long) { prefs.edit().putInt("failures", count).putLong("blocked_until", until).commit() }
    override fun loadDevices(): List<PairedDevice> {
        val raw = prefs.getString("paired_devices_v2", null) ?: return emptyList()
        return runCatching {
            val arr = org.json.JSONArray(raw)
            List(arr.length()) { i -> PairedDevice.fromJsonObject(arr.getJSONObject(i)) }
        }.getOrDefault(emptyList())
    }

    override fun saveDevices(devices: List<PairedDevice>) {
        val arr = org.json.JSONArray()
        devices.forEach { arr.put(it.toJsonObject()) }
        prefs.edit().remove("paired_devices").putString("paired_devices_v2", arr.toString()).apply()
    }
}

class RemotePairingManager(
    private val store: RemotePairingStore = InMemoryRemotePairingStore(),
    private val clock: () -> Long = System::currentTimeMillis,
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
        val failures = store.loadFailures()
        failedAttempts = failures.first
        lockoutUntilMs = failures.second
    }

    fun getPairedDevices(): List<PairedDevice> = pairedDevices.toList()

    @Synchronized
    fun generateNewCode(): String {
        val pin = "%06d".format(SecureRandom().nextInt(1_000_000))
        currentPin = pin
        pinExpiresAtMs = clock() + CODE_LIFETIME_MS
        return pin
    }

    fun pinRemainingSeconds(): Long {
        val remaining = (pinExpiresAtMs - clock()) / 1000L
        return remaining.coerceAtLeast(0L)
    }

    fun isLockedOut(): Boolean = clock() < lockoutUntilMs

    fun lockoutRemainingSeconds(): Long {
        val remaining = (lockoutUntilMs - clock()) / 1000L
        return remaining.coerceAtLeast(0L)
    }

    @Synchronized
    fun attemptPair(code: String, clientName: String, clientId: String): PairAttemptResult {
        val now = clock()
        if (now < lockoutUntilMs) {
            return PairAttemptResult.RateLimited((lockoutUntilMs - now) / 1000L)
        }

        val activePin = currentPin
        val isValid = activePin != null && now < pinExpiresAtMs && activePin == code.trim()

        if (!isValid) {
            failedAttempts++
            if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
                lockoutUntilMs = now + LOCKOUT_DURATION_MS
                failedAttempts = 0
                store.saveFailures(failedAttempts, lockoutUntilMs)
                return PairAttemptResult.RateLimited(LOCKOUT_DURATION_MS / 1000L)
            }
            store.saveFailures(failedAttempts, lockoutUntilMs)
            return PairAttemptResult.InvalidCode
        }

        // Success: reset rate limiting counters and generate token
        failedAttempts = 0
        lockoutUntilMs = 0L
        store.saveFailures(0, 0L)
        currentPin = null
        pinExpiresAtMs = 0L

        if (pairedDevices.size >= 16) return PairAttemptResult.InvalidCode
        val token = generateSecureToken()
        val existingIndex = -1
        val newDevice = PairedDevice(
            id = UUID.randomUUID().toString(),
            name = clientName.take(64).ifBlank { "Remote" },
            token = RemoteSecurity.hash(token),
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
        return pairedDevices.any { RemoteSecurity.equal(it.token, RemoteSecurity.hash(token)) }
    }

    private val usedNonces = linkedMapOf<String, Long>()
    @Synchronized
    fun authorize(method: String, path: String, nonce: String, signature: String, body: String): Boolean {
        val timestamp = nonce.substringBefore(':').toLongOrNull() ?: return false
        val now = clock()
        if (timestamp < now - 30_000 || timestamp > now + 30_000 || nonce.length > 100) return false
        usedNonces.entries.removeAll { it.value < now - 60_000 }
        if (usedNonces.containsKey(nonce) || usedNonces.size >= 2048) return false
        val valid = pairedDevices.any { device ->
            val mac = javax.crypto.Mac.getInstance("HmacSHA256")
            mac.init(javax.crypto.spec.SecretKeySpec(device.token.toByteArray(), "HmacSHA256"))
            RemoteSecurity.equal(signature, RemoteSecurity.hex(mac.doFinal("$method\n$path\n$nonce\n$body".toByteArray())))
        }
        if (valid) usedNonces[nonce] = now
        return valid
    }
    @Synchronized fun invalidateCode() { currentPin = null; pinExpiresAtMs = 0L }

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
