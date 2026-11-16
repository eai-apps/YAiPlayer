// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.core.account

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import java.io.IOException
import java.net.Proxy
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Owner 16.11: in Russia Cloudflare (`*.workers.dev`, our own domains on it) and image.tmdb.org are often
 * blocked — a tester's sync never connected. Our servers run relays (Caddy, see G:\evvant\Projects\SSH\SERVERS.md):
 * each forwards a path prefix to one of these services, so the database and the services stay where they are.
 *
 * [Fallback] sits on the app's shared OkHttp client: a call to one of [HOSTS] that fails on the network goes out
 * again through the relays, one after another; the host then goes through the relay that worked for 30 minutes
 * before direct is tried again. A relay has no domain: the app trusts exactly its certificate ([Relay.certSha256]).
 */
object ApiRoutes {
    data class Relay(val number: Int, val id: String, val base: String, val certSha256: String)

    /** In the order tried. Add a server after it is set up (number = «сервер N» on screen). */
    val RELAYS = listOf(
        Relay(1, "operator", "https://194.26.203.2:8443", "1311c85f3584b8627cf6434355338982486a5cf59cee06c8ebe0a5d243fdefe9"),
        Relay(2, "dutch", "https://130.49.185.16:8443", "771205b3c5f3f4ac42964970916926e5c20cff0c5d3fd2c44922351205213e40"),
        Relay(3, "moldova", "https://143.246.223.135:9443", "19c36be67ba4ee96d079c21987cff457668face6db5e803aee7c24376d5fa3b9"),
    )

    /** Service host → the relay's path prefix for it (the relay's Caddyfile has the same list). */
    val HOSTS = mapOf(
        "yaiplayer-api.yai-media-player.workers.dev" to "",
        "tmdb.owntv.me" to "/tmdb",
        "owntv-tmdb-meta.xiannero.workers.dev" to "/tmdb-legacy",
        "my-owntv-opensub.xiannero.workers.dev" to "/opensub",
        "image.tmdb.org" to "/tmdb-img",
    )

    private val API_HOST = HOSTS.keys.first()

    /** How the account/sync server was reached last: null = not yet, 0 = direct, N = relay «сервер N». */
    private val _apiRoute = MutableStateFlow<Int?>(null)
    val apiRoute: StateFlow<Int?> = _apiRoute

    /** Host → (relay that worked, since when). */
    private val viaRelay = ConcurrentHashMap<String, Pair<Relay, Long>>()

    private val clients = ConcurrentHashMap<String, OkHttpClient>()

    private fun relayClient(r: Relay): OkHttpClient = clients.getOrPut(r.id) {
        val trust = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) { throw CertificateException() }
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                val got = chain.firstOrNull()?.encoded?.let { MessageDigest.getInstance("SHA-256").digest(it) }
                    ?.joinToString("") { b -> "%02x".format(b) }
                if (got != r.certSha256) throw CertificateException("relay certificate mismatch")
            }
        }
        val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(trust), null) }
        // A plain client: never through this interceptor again, never through the user's proxy.
        OkHttpClient.Builder()
            .sslSocketFactory(ssl.socketFactory, trust)
            .hostnameVerifier { _, _ -> true } // identity = the pinned certificate, not a name
            .proxy(Proxy.NO_PROXY)
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectTimeout(8, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private fun throughRelay(request: okhttp3.Request, r: Relay, prefix: String): Response {
        val u = request.url
        val target = r.base.trimEnd('/') + prefix + u.encodedPath + (u.encodedQuery?.let { "?$it" } ?: "")
        return relayClient(r).newCall(request.newBuilder().url(target).build()).execute()
    }

    private fun note(host: String, route: Int) { if (host == API_HOST) _apiRoute.value = route }

    object Fallback : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val host = request.url.host
            val prefix = HOSTS[host] ?: return chain.proceed(request)
            val now = System.currentTimeMillis()
            viaRelay[host]?.let { (r, since) ->
                if (now - since < RETRY_DIRECT_MS) {
                    try { return throughRelay(request, r, prefix).also { note(host, r.number) } } catch (_: IOException) { viaRelay.remove(host) }
                } else viaRelay.remove(host)
            }
            val directError = try {
                return chain.proceed(request).also { note(host, 0) }
            } catch (e: IOException) { e }
            if (chain.call().isCanceled()) throw directError
            for (r in RELAYS) {
                try {
                    return throughRelay(request, r, prefix).also { viaRelay[host] = r to now; note(host, r.number) }
                } catch (_: IOException) { /* next relay */ }
            }
            throw directError
        }
    }

    private const val RETRY_DIRECT_MS = 30 * 60_000L
}
