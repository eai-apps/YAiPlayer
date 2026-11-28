// MIP Player (operator edition of YAiPlayer). Original code.
package tv.own.owntv.core.mip

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What the device remembers about the operator login: token, login, expiry and the local rows made from the panel. */
class MipStore(context: Context) {
    data class Session(val token: String, val login: String)

    private val prefs = context.getSharedPreferences("mip_session_v1", Context.MODE_PRIVATE)
    private val _session = MutableStateFlow(read())
    val session: StateFlow<Session?> = _session

    private fun read(): Session? {
        val t = prefs.getString("token", null) ?: return null
        return Session(t, prefs.getString("login", "") ?: "")
    }

    fun signIn(token: String, login: String) {
        prefs.edit().putString("token", token).putString("login", login).apply()
        _session.value = read()
    }

    var profileId: Long
        get() = prefs.getLong("profile_id", -1L)
        set(v) { prefs.edit().putLong("profile_id", v).apply() }
    var playlistSourceId: Long
        get() = prefs.getLong("playlist_source", -1L)
        set(v) { prefs.edit().putLong("playlist_source", v).apply() }
    var portalSourceId: Long
        get() = prefs.getLong("portal_source", -1L)
        set(v) { prefs.edit().putLong("portal_source", v).apply() }
    var expires: String
        get() = prefs.getString("expires", "") ?: ""
        set(v) { prefs.edit().putString("expires", v).apply() }
    var paymentMessage: String
        get() = prefs.getString("payment_message", "") ?: ""
        set(v) { prefs.edit().putString("payment_message", v).apply() }

    fun clear() {
        prefs.edit().clear().apply()
        _session.value = null
    }
}
