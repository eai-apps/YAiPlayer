// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.remote

import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class RemotePairingManager(
    private val pinValidityMs: Long = 2 * 60 * 1000L,
    private val blockDurationMs: Long = 5 * 60 * 1000L,
    private val maxFailedAttempts: Int = 5
) {
    private val random = SecureRandom()
    private val pairedTokens = ConcurrentHashMap<String, String>() // token -> deviceName

    @Volatile private var currentPin: String? = null
    @Volatile private var pinExpiresAtMs: Long = 0L
    @Volatile private var blockedUntilMs: Long = 0L
    private val failedAttempts = AtomicInteger(0)

    fun generateNewPin(): String {
        val now = System.currentTimeMillis()
        if (now < blockedUntilMs) {
            return "" // currently blocked
        }
        val pin = String.format("%06d", random.nextInt(1_000_000))
        currentPin = pin
        pinExpiresAtMs = now + pinValidityMs
        return pin
    }

    fun getCurrentPin(): String? {
        val now = System.currentTimeMillis()
        if (now < blockedUntilMs) return null
        if (now > pinExpiresAtMs) {
            currentPin = null
            return null
        }
        return currentPin
    }

    fun isBlocked(): Boolean = System.currentTimeMillis() < blockedUntilMs

    fun getRemainingBlockSeconds(): Long {
        val remaining = (blockedUntilMs - System.currentTimeMillis()) / 1000L
        return if (remaining > 0) remaining else 0L
    }

    fun verifyPin(enteredPin: String, deviceName: String, tvName: String = "YAiPlayer TV"): PairingResult {
        val now = System.currentTimeMillis()
        if (now < blockedUntilMs) {
            return PairingResult.Blocked(getRemainingBlockSeconds())
        }

        val activePin = currentPin
        if (activePin == null || now > pinExpiresAtMs) {
            currentPin = null
            return PairingResult.Expired
        }

        if (activePin == enteredPin.trim()) {
            failedAttempts.set(0)
            currentPin = null // consume PIN
            val token = UUID.randomUUID().toString().replace("-", "")
            pairedTokens[token] = deviceName
            return PairingResult.Success(token, tvName)
        } else {
            val attempts = failedAttempts.incrementAndGet()
            if (attempts >= maxFailedAttempts) {
                blockedUntilMs = now + blockDurationMs
                currentPin = null
                return PairingResult.Blocked(blockDurationMs / 1000L)
            }
            return PairingResult.InvalidPin
        }
    }

    fun isTokenValid(token: String): Boolean = pairedTokens.containsKey(token)

    fun getPairedDevices(): Map<String, String> = pairedTokens.toMap()

    fun removeToken(token: String) {
        pairedTokens.remove(token)
    }

    fun clearAll() {
        pairedTokens.clear()
        currentPin = null
        failedAttempts.set(0)
        blockedUntilMs = 0L
    }
}
