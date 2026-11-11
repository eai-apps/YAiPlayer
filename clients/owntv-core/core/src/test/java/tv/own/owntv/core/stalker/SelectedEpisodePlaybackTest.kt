// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.core.stalker

import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.content.episodePlaybackIndex
import tv.own.owntv.core.database.entity.EpisodeEntity
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.network.HttpClient
import tv.own.owntv.core.parser.XtreamClient

class SelectedEpisodePlaybackTest {
    private val source = SourceEntity(name = "Portal", type = SourceType.STALKER,
        url = "http://portal/c/", mac = "00:1A:79:AA:BB:CC")
    private fun episode(season: Int, number: Int, id: Long = season * 100L + number) = EpisodeEntity(
        id = id, seriesId = 10, seasonNumber = season, episodeNumber = number, name = "",
        remoteId = "season-$season:$number", streamUrl = "ffmpeg http://localhost/season-$season.mkv",
    )
    private val selected = listOf(episode(1, 1), episode(1, 2), episode(2, 3))

    @Test fun selectedEpisodeReachesTheActualStalkerRequest() = runBlocking {
        val requests = mutableListOf<HttpUrl>()
        val stop = IOException("request captured before transport")
        val client = StalkerClient(OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request().url
            throw stop
        }.build())
        val resolver = resolver(client)
        for (ep in selected) {
            val failure = runCatching { resolver.resolveEpisode(source, ep) }.exceptionOrNull()
            // Coroutine stack-trace recovery may copy an IOException across Dispatchers.IO.
            assertTrue(failure is IOException)
            assertEquals(stop.message, failure?.message)
            val url = requests.last()
            assertEquals("vod", url.queryParameter("type"))
            assertEquals("create_link", url.queryParameter("action"))
            assertEquals(ep.streamUrl, url.queryParameter("cmd"))
            assertEquals(ep.episodeNumber.toString(), url.queryParameter("series"))
            assertEquals(listOf(ep.episodeNumber.toString()), url.queryParameterValues("series"))
        }
        assertEquals(3, requests.size)
    }

    @Test fun treeEpisodesKeepTheirOwnSeasonAndEpisodeId() = runBlocking {
        val queries = mutableListOf<List<String>>()
        val client = object : StalkerClient(OkHttpClient()) {
            override suspend fun getVodEpisodeFileCmd(apiBase: String, mac: String, token: String,
                userAgent: String?, movieId: String, seasonId: String, episodeId: String): String {
                queries += listOf(movieId, seasonId, episodeId)
                return "/media/file_$episodeId.mpg"
            }
            override suspend fun createLink(apiBase: String, mac: String, token: String,
                userAgent: String?, cmd: String, type: String, episode: Int?): String {
                assertEquals("vod", type)
                assertNull(episode) // the file, rather than a shared season command, selects this episode
                return cmd
            }
        }
        val resolver = resolver(client)
        for (ep in selected) {
            val remote = "${ep.seasonNumber}0${ep.episodeNumber}"
            val ref = StalkerClient.vodEpisodeRef("show", "season-${ep.seasonNumber}", remote)
            assertEquals("/media/file_$remote.mpg", resolver.resolveEpisode(source, ep.copy(streamUrl = ref)))
            assertEquals(listOf("show", "season-${ep.seasonNumber}", remote), queries.last())
        }
    }

    @Test fun xtreamUsesEpisodeIdRatherThanItsOrdinalOrTheFirstEpisode() = runBlocking {
        val source = SourceEntity(name = "Xtream", type = SourceType.XTREAM,
            url = "http://xtream", username = "user", password = "pass")
        val xtream = XtreamClient(HttpClient(OkHttpClient()))
        val resolver = resolver(StalkerClient(OkHttpClient()))
        for (ep in selected) {
            val remote = "${ep.seasonNumber}00${ep.episodeNumber}"
            val stored = xtream.seriesEpisodeUrl(source, remote, "mkv")
            assertEquals("http://xtream/series/user/pass/$remote.mkv",
                resolver.resolveEpisode(source, ep.copy(streamUrl = stored, remoteId = remote)))
        }
    }

    @Test fun queueAndResumeSelectTheRequestedSeasonAndEpisode() {
        selected.forEachIndexed { index, ep -> assertEquals(index, episodePlaybackIndex(selected, ep)) }
        // The UI still holds a row read before a refresh replaced its local id.
        assertEquals(2, episodePlaybackIndex(selected, selected[2].copy(id = 999)))
        // All Stalker episodes in a season may share one command; URL equality is not identity.
        assertEquals(selected[0].streamUrl, selected[1].streamUrl)
        assertEquals(1, episodePlaybackIndex(selected, selected[1]))
    }

    @Test fun missingEpisodeNeverSilentlyStartsEpisodeOne() {
        assertEquals(-1, episodePlaybackIndex(selected, episode(2, 4)))
        assertEquals(-1, episodePlaybackIndex(emptyList(), selected[0]))
        assertEquals(-1, episodePlaybackIndex(selected, selected[0].copy(seriesId = 999)))
    }

    /** The operator's portal (10.11.2026): the season record is one "Custom URL" — the first episode's
     *  file — and create_link ignores `series=` (answering with that file or just `?token=…`). */
    @Test fun customUrlSeasonPlaysTheSelectedEpisodeNotTheFirst() = runBlocking {
        val seasonCmd = "http://files:82/2025/SERIAL_2/MobLand/s02/S02E01.mkv"
        for (answer in listOf("?token=abc", "$seasonCmd?token=abc", "ffmpeg $seasonCmd?token=abc")) {
            val client = object : StalkerClient(OkHttpClient()) {
                override suspend fun createLink(apiBase: String, mac: String, token: String,
                    userAgent: String?, cmd: String, type: String, episode: Int?): String =
                    StalkerClient.stripCmdPrefix(answer)
            }
            val resolver = resolver(client)
            for (n in listOf(1, 2, 5, 12)) {
                val ep = episode(2, n).copy(streamUrl = seasonCmd)
                val expected = "http://files:82/2025/SERIAL_2/MobLand/s02/S02E${n.toString().padStart(2, '0')}.mkv?token=abc"
                assertEquals("answer=$answer episode=$n", expected, resolver.resolveEpisode(source, ep))
            }
        }
    }

    @Test fun pinEpisodeLeavesCorrectOrUnmarkedUrlsAlone() {
        val r = resolver(StalkerClient(OkHttpClient()))
        assertEquals("http://h/s01/S01E05.mkv?t=1", r.pinEpisodeInUrl("http://h/s01/S01E05.mkv?t=1", 5))
        assertEquals("http://h/film.mkv?t=1", r.pinEpisodeInUrl("http://h/film.mkv?t=1", 5))
        assertEquals("http://h/S1E10.mp4", r.pinEpisodeInUrl("http://h/S1E9.mp4", 10))
        assertEquals("http://h/Show.s01.e007.mkv", r.pinEpisodeInUrl("http://h/Show.s01.e001.mkv", 7))
        // Only the file name counts: a folder or the query must never be rewritten.
        assertEquals("http://h/S01E01-pack/ep.mkv?x=S01E01", r.pinEpisodeInUrl("http://h/S01E01-pack/ep.mkv?x=S01E01", 3))
    }

    private fun resolver(client: StalkerClient): StreamUrlResolver {
        val auth = object : StalkerAuthManager(client) {
            override suspend fun <T> withAuthRetry(creds: StalkerCredentials,
                block: suspend (StalkerSession) -> T): T = block(StalkerSession(
                    apiBase = "http://portal/portal.php", token = "test-token",
                    expiresAtMs = Long.MAX_VALUE, profile = emptyMap(),
                ))
        }
        return StreamUrlResolver(auth, client)
    }
}
