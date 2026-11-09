// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.core.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import tv.own.owntv.core.database.OwnTVDatabase
import tv.own.owntv.core.content.ProviderMediaDetails
import tv.own.owntv.core.content.providerSearchText
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.MovieDao
import tv.own.owntv.core.database.dao.SeriesDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.MovieEntity
import tv.own.owntv.core.database.entity.SeriesEntity
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.parser.XtreamClient
import tv.own.owntv.core.stalker.StalkerAuthManager
import tv.own.owntv.core.stalker.StalkerClient
import tv.own.owntv.core.stalker.stalkerCredentials

/** Positive results live in Room; an empty/failed lookup is attempted once per item per session. */
class ProviderDetailsRepository(
    private val movies: MovieDao,
    private val series: SeriesDao,
    private val sources: SourceDao,
    private val categories: CategoryDao,
    private val stalker: StalkerClient,
    private val auth: StalkerAuthManager,
    private val xtream: XtreamClient,
    private val database: OwnTVDatabase,
) {
    private val attempted = java.util.concurrent.ConcurrentHashMap.newKeySet<Pair<Boolean, Long>>()
    private data class Details(val metadata: ProviderMediaDetails, val plot: String?)

    suspend fun movie(item: MovieEntity): MovieEntity = withContext(Dispatchers.IO) {
        if (listOf(item.originalTitle, item.genres, item.country, item.director, item.actors).all { !it.isNullOrBlank() }) return@withContext item
        val details = fetch(false, item.id, item.sourceId, item.categoryId, item.remoteId, item.name) ?: return@withContext item
        database.useWriterConnection { connection -> connection.immediateTransaction {
        val current = movies.getById(item.id) ?: return@immediateTransaction item
        val d = details.metadata
        val updated = current.copy(originalTitle = current.originalTitle ?: d.originalTitle, genres = current.genres ?: d.genres,
            country = current.country ?: d.country, director = current.director ?: d.director, actors = current.actors ?: d.actors,
            year = current.year ?: d.year, durationSecs = current.durationSecs ?: d.durationSecs, plot = current.plot ?: details.plot)
        val searchable = updated.copy(searchText = providerSearchText(updated.name, updated.originalTitle, updated.genres,
            updated.year, updated.country, updated.director, updated.actors, updated.plot))
        if (searchable != current) movies.updateAll(listOf(searchable))
        searchable
        } }
    }

    suspend fun show(item: SeriesEntity): SeriesEntity = withContext(Dispatchers.IO) {
        if (listOf(item.originalTitle, item.genres, item.country, item.director, item.actors).all { !it.isNullOrBlank() }) return@withContext item
        val details = fetch(true, item.id, item.sourceId, item.categoryId, item.remoteId, item.name) ?: return@withContext item
        database.useWriterConnection { connection -> connection.immediateTransaction {
        val current = series.getSeriesById(item.id) ?: return@immediateTransaction item
        val d = details.metadata
        val updated = current.copy(originalTitle = current.originalTitle ?: d.originalTitle, genres = current.genres ?: d.genres,
            country = current.country ?: d.country, director = current.director ?: d.director, actors = current.actors ?: d.actors,
            year = current.year ?: d.year, durationSecs = current.durationSecs ?: d.durationSecs, plot = current.plot ?: details.plot)
        val searchable = updated.copy(searchText = providerSearchText(updated.name, updated.originalTitle, updated.genres,
            updated.year, updated.country, updated.director, updated.actors, updated.plot))
        if (searchable != current) series.updateAll(listOf(searchable))
        searchable
        } }
    }

    private suspend fun fetch(show: Boolean, localId: Long, sourceId: Long, categoryId: Long?, remoteId: String?, title: String): Details? {
        if (remoteId.isNullOrBlank()) return null
        val source = sources.getById(sourceId) ?: return null
        if (source.type != SourceType.STALKER && source.type != SourceType.XTREAM) return null
        val key = show to localId
        if (!attempted.add(key)) return null
        try {
            return when (source.type) {
                SourceType.XTREAM -> {
                    val fields = xtream.getCatalogDetails(source, remoteId, show)
                    Details(ProviderMediaDetails.from(fields), fields["plot"] ?: fields["description"])
                }
                else -> {
                    val mac = source.mac?.let { StalkerClient.canonicalizeMac(it) } ?: return null
                    val category = categoryId?.let { categories.getById(it) }
                    val creds = source.stalkerCredentials(mac)
                    val found = auth.withAuthRetry(creds) { session ->
                        stalker.getCatalogDetails(session.apiBase, mac, session.token, creds.userAgent,
                            remoteId, category?.remoteId, title, show && category?.mediaType != MediaType.MOVIE)
                    } ?: return null
                    Details(found.details, found.plot)
                }
            }
        } catch (c: CancellationException) {
            attempted.remove(key)
            throw c
        } catch (_: Exception) {
            return null
        }
    }
}
