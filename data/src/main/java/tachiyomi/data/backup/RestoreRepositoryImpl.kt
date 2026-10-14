package tachiyomi.data.backup

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.JsonObject
import tachiyomi.data.Database
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.model.AnimeUpdate
import tachiyomi.domain.anime.repository.AnimeRepository
import tachiyomi.domain.backup.model.RestoredAnime
import tachiyomi.domain.backup.model.RestoredHistory
import tachiyomi.domain.backup.repository.RestoreRepository
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.episode.repository.EpisodeRepository
import tachiyomi.domain.track.model.Track
import tachiyomi.domain.track.repository.TrackRepository
import java.util.Date
import kotlin.math.max

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RestoreRepositoryImpl(
    private val database: Database,
    private val animeRepository: AnimeRepository,
    private val episodeRepository: EpisodeRepository,
    private val trackRepository: TrackRepository,
) : RestoreRepository {

    override suspend fun getAnimeUrlsBySourceId(): Map<Long, List<String>> {
        return database.animesQueries
            .getAllAnimeSourceAndUrl()
            .awaitAsList()
            .groupBy({ it.source }, { it.url })
    }

    override suspend fun restoreAnime(entries: List<RestoredAnime>, update: suspend (Anime) -> AnimeUpdate) {
        database.transaction {
            entries.forEach { entry ->
                // A cancelled restore stops between entries and rolls the whole batch back
                currentCoroutineContext().ensureActive()
                animeRepository.update(update(restore(entry)))
            }
        }
    }

    private suspend fun restore(entry: RestoredAnime): Anime {
        val dbAnime = animeRepository.getAnimeByUrlAndSourceId(entry.anime.url, entry.anime.source)
        // AM -->
        val parent = entry.parent?.let {
            animeRepository.getAnimeByUrlAndSourceId(it.url, it.sourceId)?.id
        }
        // <-- AM
        val anime = if (dbAnime == null) {
            entry.anime.copy(id = insertAnime(entry.anime))
        } else {
            updateAnime(mergeAnime(entry.anime, dbAnime))
        }
            .copy(parentId = parent)

        if (entry.categoryIds.isNotEmpty()) {
            animeRepository.setAnimeCategories(anime.id, entry.categoryIds)
        }
        restoreEpisodes(anime, entry.episodes)
        restoreTracking(anime, entry.tracks)
        restoreHistory(anime, entry.history)
        restoreExcludedScanlators(anime, entry.excludedScanlators)
        return anime
    }

    private fun mergeAnime(anime: Anime, dbAnime: Anime): Anime {
        val details = if (dbAnime.initialized || !anime.initialized) dbAnime else anime
        return dbAnime.copy(
            favoriteAt = if (dbAnime.favorite || anime.favorite) {
                listOfNotNull(dbAnime.favoriteAt, anime.favoriteAt).filter { it > 0 }.minOrNull() ?: 0L
            } else {
                null
            },
            ogTitle = details.title,
            ogArtist = details.artist,
            ogAuthor = details.artist,
            ogDescription = details.description,
            ogGenre = details.genre,
            ogStatus = details.status,
            thumbnailUrl = details.thumbnailUrl,
            updateStrategy = details.updateStrategy,
            initialized = dbAnime.initialized || anime.initialized,
            // Merge both backup and local data with local winning
            memo = JsonObject(anime.memo + dbAnime.memo),
            // AM -->
            backgroundUrl = details.backgroundUrl,
            fetchType = details.fetchType,
            seasonNumber = details.seasonNumber,
            seasonSourceOrder = details.seasonSourceOrder,
            // <-- AM
        )
    }

    private suspend fun updateAnime(anime: Anime): Anime {
        database.animesQueries.updateFromBackup(
            favoriteAt = anime.favoriteAt,
            artist = anime.artist,
            author = anime.author,
            description = anime.description,
            genre = anime.genre,
            title = anime.title,
            status = anime.status,
            thumbnailUrl = anime.thumbnailUrl,
            lastUpdate = anime.lastUpdate,
            initialized = anime.initialized,
            viewer = anime.viewerFlags,
            episodeFlags = anime.episodeFlags,
            coverLastModified = anime.coverLastModified,
            updateStrategy = anime.updateStrategy,
            notes = anime.notes,
            // AM -->
            fetchType = anime.fetchType,
            parentId = anime.parentId,
            seasonFlags = anime.seasonFlags,
            seasonNumber = anime.seasonNumber,
            seasonSourceOrder = anime.seasonSourceOrder,
            backgroundUrl = anime.backgroundUrl,
            backgroundLastModified = anime.backgroundLastModified,
            // <-- AM
            memo = anime.memo,
            animeId = anime.id,
        )
        return anime
    }

    private suspend fun insertAnime(anime: Anime): Long {
        return database.animesQueries.insertReturningId(
            favoriteAt = anime.favoriteAt,
            source = anime.source,
            url = anime.url,
            artist = anime.artist,
            author = anime.author,
            description = anime.description,
            genre = anime.genre,
            title = anime.title,
            status = anime.status,
            thumbnailUrl = anime.thumbnailUrl,
            lastUpdate = anime.lastUpdate,
            nextUpdate = 0L,
            initialized = anime.initialized,
            viewerFlags = anime.viewerFlags,
            episodeFlags = anime.episodeFlags,
            coverLastModified = anime.coverLastModified,
            updateStrategy = anime.updateStrategy,
            calculateInterval = 0L,
            notes = anime.notes,
            // AM -->
            fetchType = anime.fetchType,
            parentId = anime.parentId,
            seasonFlags = anime.seasonFlags,
            seasonNumber = anime.seasonNumber,
            seasonSourceOrder = anime.seasonSourceOrder,
            backgroundUrl = anime.backgroundUrl,
            backgroundLastModified = anime.backgroundLastModified,
            // <-- AM
            memo = anime.memo,
        )
            .awaitAsOne()
    }

    private suspend fun restoreEpisodes(anime: Anime, restoredEpisodes: List<Episode>) {
        val dbEpisodesByUrl = episodeRepository.getEpisodeByAnimeId(anime.id)
            .associateBy { it.url }

        val (existingEpisodes, newEpisodes) = restoredEpisodes
            .groupBy { it.url }
            .map { (_, copies) ->
                copies.reduce { kept, other ->
                    kept.copy(
                        seen = kept.seen || other.seen,
                        bookmark = kept.bookmark || other.bookmark,
                        // AM -->
                        fillermark = kept.fillermark || other.fillermark,
                        // <-- AM
                        lastSecondSeen = max(kept.lastSecondSeen, other.lastSecondSeen),
                    )
                }
            }
            .mapNotNull {
                val episode = it.copy(animeId = anime.id)

                val dbEpisode = dbEpisodesByUrl[episode.url]
                    ?: // New episode
                    return@mapNotNull episode

                if (episode.forComparison() == dbEpisode.forComparison()) {
                    // Same state; skip
                    return@mapNotNull null
                }

                episode
                    .copyFrom(dbEpisode)
                    .copy(
                        id = dbEpisode.id,
                        seen = episode.seen || dbEpisode.seen,
                        bookmark = episode.bookmark || dbEpisode.bookmark,
                        lastSecondSeen = max(episode.lastSecondSeen, dbEpisode.lastSecondSeen),
                        dateFetch = dbEpisode.dateFetch,
                        sourceOrder = dbEpisode.sourceOrder,
                        // AY -->
                        fillermark = episode.fillermark || dbEpisode.fillermark,
                        // <-- AY
                        // Merge both backup and local data with local winning
                        memo = JsonObject(episode.memo + dbEpisode.memo),
                    )
            }
            .partition { it.id > 0 }

        newEpisodes.forEach { episode ->
            database.episodesQueries.insert(
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
        }
        existingEpisodes.forEach { episode ->
            database.episodesQueries.updateFromBackup(
                seen = episode.seen,
                bookmark = episode.bookmark,
                // AM -->
                fillermark = episode.fillermark,
                totalSeconds = episode.totalSeconds,
                thumbnailLastModifiedAt = episode.thumbnailLastModifiedAt,
                // <-- AM
                lastSecondSeen = episode.lastSecondSeen,
                episodeId = episode.id,
                memo = episode.memo,
            )
        }
    }

    private fun Episode.forComparison() =
        this.copy(id = 0L, animeId = 0L, dateFetch = 0L, dateUpload = 0L, thumbnailLastModifiedAt = 0L)

    private suspend fun restoreHistory(anime: Anime, restoredHistory: List<RestoredHistory>) {
        val toUpdate = restoredHistory
            .groupBy { it.episodeUrl }
            .mapNotNull { (episodeUrl, copies) ->
                val seenAt = copies.maxOf { it.seenAt?.time ?: 0L }
                val dbHistory = database.historyQueries
                    .getHistoryByEpisodeUrlAndAnimeId(episodeUrl, anime.id)
                    .awaitAsOneOrNull()

                if (dbHistory == null) {
                    val episode = database.episodesQueries
                        .getEpisodeByUrlAndAnimeId(episodeUrl, anime.id)
                        .awaitAsOneOrNull()
                        // Episode doesn't exist; skip
                        ?: return@mapNotNull null
                    // New history entry
                    return@mapNotNull Pair(episode._id, Date(seenAt))
                }

                // Update history entry
                Pair(
                    dbHistory.episode_id,
                    Date(max(seenAt, dbHistory.last_seen?.time ?: 0L)),
                )
            }

        toUpdate.forEach { (episodeId, seenAt) ->
            database.historyQueries.upsert(episodeId, seenAt)
        }
    }

    private suspend fun restoreTracking(anime: Anime, restoredTracks: List<Track>) {
        val dbTrackByTrackerId = trackRepository.getTracksByAnimeId(anime.id).associateBy { it.trackerId }

        val (existingTracks, newTracks) = restoredTracks
            .mapNotNull { track ->
                val dbTrack = dbTrackByTrackerId[track.trackerId]
                    ?: // New track
                    return@mapNotNull track.copy(
                        id = 0, // Let DB assign new ID
                        animeId = anime.id,
                    )

                if (track.forComparison() == dbTrack.forComparison()) {
                    // Same state; skip
                    return@mapNotNull null
                }

                // Update to an existing track
                dbTrack.copy(
                    remoteId = track.remoteId,
                    libraryId = track.libraryId,
                    lastEpisodeSeen = max(dbTrack.lastEpisodeSeen, track.lastEpisodeSeen),
                )
            }
            .partition { it.id > 0 }

        if (newTracks.isNotEmpty()) {
            trackRepository.insertAll(newTracks)
        }

        existingTracks.forEach { track ->
            database.anime_syncQueries.update(
                track.animeId,
                track.trackerId,
                track.remoteId,
                track.libraryId,
                track.title,
                track.lastEpisodeSeen,
                track.totalEpisodes,
                track.status,
                track.score,
                track.remoteUrl,
                track.startDate,
                track.finishDate,
                track.private,
                track.id,
            )
        }
    }

    private fun Track.forComparison() = this.copy(id = 0L, animeId = 0L)

    private suspend fun restoreExcludedScanlators(anime: Anime, excludedScanlators: List<String>) {
        if (excludedScanlators.isEmpty()) return
        val existingExcludedScanlators = database.excluded_scanlatorsQueries
            .getExcludedScanlatorsByAnimeId(anime.id)
            .awaitAsList()
        val toInsert = excludedScanlators.filter { it !in existingExcludedScanlators }
        toInsert.forEach { database.excluded_scanlatorsQueries.insert(anime.id, it) }
    }
}
