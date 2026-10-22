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
import tachiyomi.domain.episode.model.EpisodeSummary
import tachiyomi.domain.episode.model.EpisodeUpdate
import tachiyomi.domain.episode.repository.EpisodeRepository
import kotlin.time.Instant

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
                database.episodeQueries.removeEpisodesWithIds(removedIds)
            }
            val existing = added.map { it.animeId }
                .distinct()
                .flatMap { animeId ->
                    database.episodeQueries
                        .getEpisodesByAnimeId(
                            animeId = animeId,
                            applyScanlatorFilter = false.toLong(),
                            mapper = ::mapEpisode,
                        )
                        .awaitAsList()
                        .map { animeId to it.url }
                }
                .toMutableSet()
            val stored = added.filter { existing.add(it.animeId to it.url) }.map { episode ->
                val episodeId = database.episodeQueries.insertReturningId(
                    animeId = episode.animeId,
                    remoteUrl = episode.url,
                    remoteName = episode.name,
                    remoteScanlator = episode.scanlator,
                    userSeen = episode.seen,
                    userBookmark = episode.bookmark,
                    // AM -->
                    remoteFillermark = episode.fillermark,
                    // <-- AM
                    userLastSecondSeen = episode.lastSecondSeen,
                    // AM -->
                    stateTotalSeconds = episode.totalSeconds,
                    // <-- AM
                    remoteEpisodeNumber = episode.episodeNumber,
                    remoteOrder = episode.sourceOrder,
                    stateDateFetch = episode.dateFetch,
                    remoteDateUpload = episode.dateUpload,
                    // AM -->
                    remoteSummary = episode.summary,
                    remotePreviewUrl = episode.previewUrl,
                    // <-- AM
                    remoteMemo = episode.memo,
                )
                    .awaitAsOne()
                episode.copy(id = episodeId)
            }
            updated.forEach { episodeUpdate ->
                database.episodeQueries.updateRemote(
                    remoteName = episodeUpdate.name,
                    remoteScanlator = episodeUpdate.scanlator,
                    remoteEpisodeNumber = episodeUpdate.episodeNumber,
                    remoteOrder = episodeUpdate.sourceOrder,
                    remoteDateUpload = episodeUpdate.dateUpload,
                    id = episodeUpdate.id,
                    remoteMemo = episodeUpdate.memo,
                    // AM -->
                    remoteFillermark = episodeUpdate.fillerMark,
                    remoteSummary = episodeUpdate.summary,
                    remotePreviewUrl = episodeUpdate.previewUrl,
                    stateThumbnailLastModifiedAt = episodeUpdate.thumbnailLastModified,
                    // <-- AM
                )
            }
            stored
        }
    }

    private suspend fun partialUpdate(vararg episodeUpdates: EpisodeUpdate) {
        database.transaction {
            episodeUpdates.forEach { episodeUpdate ->
                database.episodeQueries.update(
                    userSeen = episodeUpdate.seen,
                    userBookmark = episodeUpdate.bookmark,
                    // AY -->
                    remoteFillermark = episodeUpdate.fillermark,
                    // <-- AY
                    userLastSecondSeen = episodeUpdate.lastSecondSeen,
                    // AY -->
                    stateTotalSeconds = episodeUpdate.totalSeconds,
                    // <-- AY
                    stateDateFetch = episodeUpdate.dateFetch,
                    id = episodeUpdate.id,
                    // AM -->
                    stateThumbnailLastModifiedAt = episodeUpdate.thumbnailLastModifiedAt,
                    // <-- AM
                )
            }
        }
    }

    override suspend fun getEpisodeByAnimeId(animeId: Long, applyScanlatorFilter: Boolean): List<Episode> {
        return database.episodeQueries.getEpisodesByAnimeId(
            animeId = animeId,
            applyScanlatorFilter = applyScanlatorFilter.toLong(),
            mapper = ::mapEpisode,
        ).awaitAsList()
    }

    override suspend fun getEpisodeSummaryByAnimeId(animeId: Long): EpisodeSummary {
        return database.episodeQueries
            .getEpisodeSummaryByAnimeId(animeId) { total, latestEpisodeNumber ->
                EpisodeSummary(episodeCount = total, latestEpisodeNumber = latestEpisodeNumber)
            }
            .awaitAsOne()
    }

    override suspend fun getScanlatorsByAnimeId(animeId: Long): List<String> {
        return database.episodeQueries.getScanlatorsByAnimeId(animeId) { it.orEmpty() }.awaitAsList()
    }

    override fun getScanlatorsByAnimeIdAsFlow(animeId: Long): Flow<List<String>> {
        return database.episodeQueries.getScanlatorsByAnimeId(animeId) { it.orEmpty() }.subscribeToList()
    }

    override suspend fun getBookmarkedEpisodesByAnimeId(animeId: Long): List<Episode> {
        return database.episodeQueries.getBookmarkedEpisodesByAnimeId(
            animeId,
            ::mapEpisode,
        ).awaitAsList()
    }

    override suspend fun getEpisodeById(id: Long): Episode? {
        return database.episodeQueries.getEpisodeById(id, ::mapEpisode).awaitAsOneOrNull()
    }

    override suspend fun getEpisodeByAnimeIdAsFlow(animeId: Long, applyScanlatorFilter: Boolean): Flow<List<Episode>> {
        return database.episodeQueries.getEpisodesByAnimeId(
            animeId = animeId,
            applyScanlatorFilter = applyScanlatorFilter.toLong(),
            mapper = ::mapEpisode,
        ).subscribeToList()
    }

    override suspend fun getEpisodeByUrlAndAnimeId(url: String, animeId: Long): Episode? {
        return database.episodeQueries.getEpisodeByUrlAndAnimeId(
            animeId = animeId,
            remoteUrl = url,
            mapper = ::mapEpisode,
        ).awaitAsOneOrNull()
    }

    private fun mapEpisode(
        id: Long,
        animeId: Long,
        remoteUrl: String,
        remoteName: String,
        remoteScanlator: String?,
        remoteEpisodeNumber: Double,
        remoteDateUpload: Instant?,
        remoteOrder: Long,
        // AM -->
        remoteFillermark: Boolean,
        remoteSummary: String?,
        remotePreviewUrl: String?,
        // <-- AM
        remoteMemo: JsonObject,
        userSeen: Boolean,
        userBookmark: Boolean,
        userLastSecondSeen: Int,
        stateDateFetch: Instant,
        // AM -->
        stateThumbnailLastModifiedAt: Instant?,
        stateTotalSeconds: Int,
        // <-- AM
    ): Episode = Episode(
        id = id,
        animeId = animeId,
        seen = userSeen,
        bookmark = userBookmark,
        // AY -->
        fillermark = remoteFillermark,
        // <-- AY
        lastSecondSeen = userLastSecondSeen,
        // AY -->
        totalSeconds = stateTotalSeconds,
        // <-- AY
        dateFetch = stateDateFetch,
        sourceOrder = remoteOrder,
        url = remoteUrl,
        name = remoteName,
        dateUpload = remoteDateUpload,
        episodeNumber = remoteEpisodeNumber,
        scanlator = remoteScanlator,
        // AY -->
        summary = remoteSummary,
        previewUrl = remotePreviewUrl,
        // <-- AY
        memo = remoteMemo,
        // AM -->
        thumbnailLastModifiedAt = stateThumbnailLastModifiedAt,
        // <-- AM
    )
}
