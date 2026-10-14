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
import tachiyomi.core.common.util.lang.toLong
import tachiyomi.data.Database
import tachiyomi.data.subscribeToList
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.episode.model.EpisodeRemoteUpdate
import tachiyomi.domain.episode.model.EpisodeUpdate
import tachiyomi.domain.episode.repository.EpisodeRepository

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class EpisodeRepositoryImpl(
    private val database: Database,
) : EpisodeRepository {

    override suspend fun update(episodeUpdate: EpisodeUpdate) {
        partialUpdate(episodeUpdate)
    }

    override suspend fun updateAll(episodeUpdates: List<EpisodeUpdate>) {
        partialUpdate(*episodeUpdates.toTypedArray())
    }

    override suspend fun updateFromRemote(
        removedIds: List<Long>,
        added: List<Episode>,
        updated: List<EpisodeRemoteUpdate>,
    ): List<Episode> {
        return database.transactionWithResult {
            if (removedIds.isNotEmpty()) {
                database.episodesQueries.removeEpisodesWithIds(removedIds)
            }
            val existing = added.map { it.animeId }
                .distinct()
                .flatMap { animeId ->
                    database.episodesQueries
                        .getEpisodesByAnimeId(animeId, applyScanlatorFilter = false.toLong(), ::mapEpisode)
                        .awaitAsList()
                        .map { animeId to it.url }
                }
                .toMutableSet()
            val stored = added.filter { existing.add(it.animeId to it.url) }.map { episode ->
                val episodeId = database.episodesQueries.insertReturningId(
                    episode.animeId,
                    episode.url,
                    episode.name,
                    episode.scanlator,
                    episode.seen,
                    episode.bookmark,
                    // AM -->
                    episode.fillermark,
                    // <-- AM
                    episode.lastSecondSeen,
                    // AM -->
                    episode.totalSeconds,
                    // <-- AM
                    episode.episodeNumber,
                    episode.sourceOrder,
                    episode.dateFetch,
                    episode.dateUpload,
                    // AM -->
                    episode.summary,
                    episode.previewUrl,
                    // <-- AM
                    episode.memo,
                )
                    .awaitAsOne()
                episode.copy(id = episodeId)
            }
            updated.forEach { episodeUpdate ->
                database.episodesQueries.updateRemote(
                    name = episodeUpdate.name,
                    scanlator = episodeUpdate.scanlator,
                    episodeNumber = episodeUpdate.episodeNumber,
                    sourceOrder = episodeUpdate.sourceOrder,
                    dateUpload = episodeUpdate.dateUpload,
                    episodeId = episodeUpdate.id,
                    memo = episodeUpdate.memo,
                    // AM -->
                    fillermark = episodeUpdate.fillerMark,
                    summary = episodeUpdate.summary,
                    previewUrl = episodeUpdate.previewUrl,
                    thumbnailLastModifiedAt = episodeUpdate.thumbnailLastModified,
                    // <-- AM
                )
            }
            stored
        }
    }

    private suspend fun partialUpdate(vararg episodeUpdates: EpisodeUpdate) {
        database.transaction {
            episodeUpdates.forEach { episodeUpdate ->
                database.episodesQueries.update(
                    seen = episodeUpdate.seen,
                    bookmark = episodeUpdate.bookmark,
                    // AY -->
                    fillermark = episodeUpdate.fillermark,
                    // <-- AY
                    lastSecondSeen = episodeUpdate.lastSecondSeen,
                    // AY -->
                    totalSeconds = episodeUpdate.totalSeconds,
                    // <-- AY
                    dateFetch = episodeUpdate.dateFetch,
                    episodeId = episodeUpdate.id,
                    // AM -->
                    thumbnailLastModifiedAt = episodeUpdate.thumbnailLastModifiedAt,
                    // <-- AM
                )
            }
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
        memo = memo,
        // AM -->
        thumbnailLastModifiedAt = thumbnailLastModifiedAt,
        // <-- AM
    )
}
