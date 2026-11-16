// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.core.account

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import java.net.Proxy
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Owner 16.11: in Russia `*.workers.dev` (Cloudflare) is often blocked, and a tester's sync never connected.
 * The account/sync API is reached directly first; when that fails on the network level, through relays on
 * our own servers (each a plain proxy to the same Cloudflare worker — one database, nothing duplicated).
 *
 * A relay has no domain: it answers on its IP with its own certificate, and the app trusts exactly that
 * certificate (its SHA-256 below), so nobody in between can stand in for it.
 */
object ApiRoutes {
    data class Route(val id: String, val base: String, val certSha256: String? = null)

    /** In the order tried. Add a server here after it is set up (see G:\evvant\Projects\SSH\SERVERS.md). */
    val ALL = listOf(
        Route("direct", AccountConfig.API_BASE),
        Route("operator", "https://194.26.203.2:8443", "1311c85f3584b8627cf6434355338982486a5cf59cee06c8ebe0a5d243fdefe9"),
    )

    /** What the last successful call went through — the screen shows it («Напрямую» / «Ретранслятор: оператор ✓»). */
    private val _active = MutableStateFlow<Route?>(null)
    val active: StateFlow<Route?> = _active

    @Volatile private var preferred: Route = ALL.first()
    @Volatile private var preferredSince = 0L

    /** The order for the next call: the route that worked last first; direct is tried again every 30 minutes. */
    fun order(): List<Route> {
        val p = preferred
        if (p.id != "direct" && System.currentTimeMillis() - preferredSince > RETRY_DIRECT_MS) return ALL
        return listOf(p) + ALL.filter { it != p }
    }

    fun worked(route: Route) {
        if (preferred != route) { preferred = route; preferredSince = System.currentTimeMillis() }
        _active.value = route
    }

    private val clients = HashMap<String, OkHttpClient>()

    @Synchronized
    fun client(base: OkHttpClient, route: Route): OkHttpClient {
        val sha = route.certSha256 ?: return base
        return clients.getOrPut(route.id) {
            val trust = object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) { throw CertificateException() }
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                    val got = chain.firstOrNull()?.encoded?.let { MessageDigest.getInstance("SHA-256").digest(it) }
                        ?.joinToString("") { b -> "%02x".format(b) }
                    if (got != sha) throw CertificateException("relay certificate mismatch")
                }
            }
            val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(trust), null) }
            base.newBuilder()
                .sslSocketFactory(ssl.socketFactory, trust)
                .hostnameVerifier { _, _ -> true } // identity = the pinned certificate above, not a name
                .proxy(Proxy.NO_PROXY)
                .connectTimeout(8, TimeUnit.SECONDS)
                .build()
        }
    }

    private const val RETRY_DIRECT_MS = 30 * 60_000L
}
