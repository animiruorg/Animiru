package tachiyomi.data.episode

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject
import logcat.LogPriority
import tachiyomi.core.common.util.lang.toLong
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.Database
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.subscribeToList
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.episode.model.EpisodeUpdate
import tachiyomi.domain.episode.repository.EpisodeRepository

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class EpisodeRepositoryImpl(
    private val database: Database,
) : EpisodeRepository {

    override suspend fun addAll(episodes: List<Episode>): List<Episode> {
        return try {
            database.transactionWithResult {
                episodes.map { episode ->
                    val episodeId = database.episodesQueries.insertReturningId(
                        episode.animeId,
                        episode.url,
                        episode.name,
                        episode.scanlator,
                        episode.seen,
                        episode.bookmark,
                        // AY -->
                        episode.fillermark,
                        // <-- AY
                        episode.lastSecondSeen,
                        // AY -->
                        episode.totalSeconds,
                        // <-- AY
                        episode.episodeNumber,
                        episode.sourceOrder,
                        episode.dateFetch,
                        episode.dateUpload,
                        episode.version,
                        // AY -->
                        episode.summary,
                        episode.previewUrl,
                        // <-- AY
                        episode.memo,
                    )
                        .awaitAsOne()
                    episode.copy(id = episodeId)
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
    }

    override suspend fun update(episodeUpdate: EpisodeUpdate) {
        partialUpdate(episodeUpdate)
    }

    override suspend fun updateAll(episodeUpdates: List<EpisodeUpdate>) {
        partialUpdate(*episodeUpdates.toTypedArray())
    }

    private suspend fun partialUpdate(vararg episodeUpdates: EpisodeUpdate) {
        database.transaction {
            episodeUpdates.forEach { episodeUpdate ->
                database.episodesQueries.update(
                    animeId = episodeUpdate.animeId,
                    url = episodeUpdate.url,
                    name = episodeUpdate.name,
                    scanlator = episodeUpdate.scanlator,
                    seen = episodeUpdate.seen,
                    bookmark = episodeUpdate.bookmark,
                    // AY -->
                    fillermark = episodeUpdate.fillermark,
                    // <-- AY
                    lastSecondSeen = episodeUpdate.lastSecondSeen,
                    // AY -->
                    totalSeconds = episodeUpdate.totalSeconds,
                    // <-- AY
                    episodeNumber = episodeUpdate.episodeNumber,
                    sourceOrder = episodeUpdate.sourceOrder,
                    dateFetch = episodeUpdate.dateFetch,
                    dateUpload = episodeUpdate.dateUpload,
                    episodeId = episodeUpdate.id,
                    version = episodeUpdate.version,
                    isSyncing = 0,
                    // AY -->
                    summary = episodeUpdate.summary,
                    previewUrl = episodeUpdate.previewUrl,
                    // <-- AY
                    memo = episodeUpdate.memo?.let(MemoColumnAdapter::encode),
                    // AM -->
                    thumbnailLastModifiedAt = episodeUpdate.thumbnailLastModifiedAt,
                    // <-- AM
                )
            }
        }
    }

    override suspend fun removeEpisodesWithIds(episodeIds: List<Long>) {
        try {
            database.episodesQueries.removeEpisodesWithIds(episodeIds)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }

    override suspend fun getEpisodeByAnimeId(animeId: Long, applyScanlatorFilter: Boolean): List<Episode> {
        return database.episodesQueries.getEpisodesByAnimeId(
            animeId,
            applyScanlatorFilter.toLong(),
            ::mapEpisode,
        ).awaitAsList()
    }

    override suspend fun getScanlatorsByAnimeId(animeId: Long): List<String> {
        return database.episodesQueries.getScanlatorsByAnimeId(animeId) { it.orEmpty() }.awaitAsList()
    }

    override fun getScanlatorsByAnimeIdAsFlow(animeId: Long): Flow<List<String>> {
        return database.episodesQueries.getScanlatorsByAnimeId(animeId) { it.orEmpty() }.subscribeToList()
    }

    override suspend fun getBookmarkedEpisodesByAnimeId(animeId: Long): List<Episode> {
        return database.episodesQueries.getBookmarkedEpisodesByAnimeId(
            animeId,
            ::mapEpisode,
        ).awaitAsList()
    }

    override suspend fun getEpisodeById(id: Long): Episode? {
        return database.episodesQueries.getEpisodeById(id, ::mapEpisode).awaitAsOneOrNull()
    }

    override suspend fun getEpisodeByAnimeIdAsFlow(animeId: Long, applyScanlatorFilter: Boolean): Flow<List<Episode>> {
        return database.episodesQueries.getEpisodesByAnimeId(
            animeId,
            applyScanlatorFilter.toLong(),
            ::mapEpisode,
        ).subscribeToList()
    }

    override suspend fun getEpisodeByUrlAndAnimeId(url: String, animeId: Long): Episode? {
        return database.episodesQueries.getEpisodeByUrlAndAnimeId(
            url,
            animeId,
            ::mapEpisode,
        ).awaitAsOneOrNull()
    }

    @Suppress("UNUSED_PARAMETER")
    private fun mapEpisode(
        id: Long,
        animeId: Long,
        url: String,
        name: String,
        scanlator: String?,
        seen: Boolean,
        bookmark: Boolean,
        // AY -->
        fillermark: Boolean,
        // <-- AY
        lastSecondSeen: Long,
        // AY -->
        totalSeconds: Long,
        // <-- AY
        episodeNumber: Double,
        sourceOrder: Long,
        dateFetch: Long,
        dateUpload: Long,
        lastModifiedAt: Long,
        version: Long,
        isSyncing: Long,
        // AY -->
        summary: String?,
        previewUrl: String?,
        // <-- AY
        memo: JsonObject,
        // AM -->
        thumbnailLastModifiedAt: Long,
        // <-- AM
    ): Episode = Episode(
        id = id,
        animeId = animeId,
        seen = seen,
        bookmark = bookmark,
        // AY -->
        fillermark = fillermark,
        // <-- AY
        lastSecondSeen = lastSecondSeen,
        // AY -->
        totalSeconds = totalSeconds,
        // <-- AY
        dateFetch = dateFetch,
        sourceOrder = sourceOrder,
        url = url,
        name = name,
        dateUpload = dateUpload,
        episodeNumber = episodeNumber,
        scanlator = scanlator,
        // AY -->
        summary = summary,
        previewUrl = previewUrl,
        // <-- AY
        lastModifiedAt = lastModifiedAt,
        version = version,
        memo = memo,
        // AM -->
        thumbnailLastModifiedAt = thumbnailLastModifiedAt,
        // <-- AM
    )
}
