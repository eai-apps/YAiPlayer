// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.core.account

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * What this device remembers about the signed-in account: the session token, the login, the data key that opens the
 * source secrets (encrypted with an Android Keystore key, never stored in the clear), and the link between server
 * profiles/records and the local Room rows. Kept outside the Room database on purpose: no migration, and signing out
 * is one `clear()`.
 */
class AccountStore(context: Context) {
    data class Session(val token: String, val login: String, val accountId: String)

    private val prefs = context.getSharedPreferences("yai_account_v1", Context.MODE_PRIVATE)
    private val _session = MutableStateFlow(readSession())
    val session: StateFlow<Session?> = _session

    private fun readSession(): Session? {
        val t = prefs.getString("token", null) ?: return null
        return Session(t, prefs.getString("login", "") ?: "", prefs.getString("account_id", "") ?: "")
    }

    fun signIn(token: String, login: String, accountId: String) {
        prefs.edit().putString("token", token).putString("login", login).putString("account_id", accountId).apply()
        _session.value = readSession()
    }

    fun clear() {
        prefs.edit().clear().apply()
        _session.value = null
    }

    // ---- data key, sealed by the Keystore ----
    private fun keystoreKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    var dataKey: ByteArray?
        get() = runCatching {
            val raw = AccountCrypto.unb64u(prefs.getString("data_key", null) ?: return null)
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(128, raw, 0, 12))
                doFinal(raw, 12, raw.size - 12)
            }
        }.getOrNull()
        set(value) {
            if (value == null) { prefs.edit().remove("data_key").apply(); return }
            val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, keystoreKey()) }
            prefs.edit().putString("data_key", AccountCrypto.b64u(c.iv + c.doFinal(value))).apply()
        }

    // ---- links between server ids and local rows ----
    /** serverProfileId → local profile id */
    var profileLinks: Map<String, Long>
        get() = readMap("profile_links") { it.toLong() }
        set(v) = writeMap("profile_links", v)

    /** recordId → "localSourceId|rev|fingerprint" */
    var sourceLinks: Map<String, SourceLink>
        get() = readMap("source_links") { SourceLink.parse(it) }
        set(v) = writeMap("source_links", v.mapValues { it.value.toString() })

    data class SourceLink(val localId: Long, val rev: Int, val fingerprint: String, val profileServerId: String) {
        override fun toString() = "$localId|$rev|$fingerprint|$profileServerId"
        companion object {
            fun parse(s: String): SourceLink {
                val p = s.split('|')
                return SourceLink(p[0].toLong(), p.getOrNull(1)?.toIntOrNull() ?: 0, p.getOrNull(2) ?: "", p.getOrNull(3) ?: "")
            }
        }
    }

    private fun <T> readMap(key: String, conv: (String) -> T): Map<String, T> {
        val o = runCatching { JSONObject(prefs.getString(key, "{}") ?: "{}") }.getOrElse { JSONObject() }
        return o.keys().asSequence().associateWith { conv(o.getString(it)) }
    }

    private fun writeMap(key: String, map: Map<String, Any>) {
        val o = JSONObject()
        map.forEach { (k, v) -> o.put(k, v.toString()) }
        prefs.edit().putString(key, o.toString()).apply()
    }

    private companion object { const val ALIAS = "yai_account_data_key_v1" }
}
