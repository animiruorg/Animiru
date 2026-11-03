package mihon.domain.migration.usecases

import dev.zacsweers.metro.Inject
import eu.kanade.domain.anime.interactor.UpdateAnime
import eu.kanade.domain.anime.model.hasCustomBackground
import eu.kanade.domain.anime.model.hasCustomCover
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.data.cache.BackgroundCache
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.track.EnhancedTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mihon.core.metro.AppCoroutineScope
import mihon.domain.migration.models.MigrationFlag
import mihon.domain.source.interactor.UpdateAnimeFromRemote
import mihon.domain.source.interactor.models.RemoteAnimeEpisodeUpdate
import mihon.domain.source.interactor.models.RemoteAnimeSeasonUpdate
import tachiyomi.domain.anime.interactor.GetAnimeWithEpisodesAndSeasons
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.model.AnimeUpdate
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetAnimeCategories
import tachiyomi.domain.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.episode.interactor.UpdateEpisode
import tachiyomi.domain.episode.model.EpisodeUpdate
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.UpsertTrack
import kotlin.math.abs
import kotlin.time.Clock

@Inject
class MigrateAnimeUseCase(
    @AppCoroutineScope private val scope: CoroutineScope,
    private val sourcePreferences: SourcePreferences,
    private val trackerManager: TrackerManager,
    private val sourceManager: SourceManager,
    private val downloadManager: DownloadManager,
    private val updateAnime: UpdateAnime,
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId,
    // AM -->
    private val getAnimeWithEpisodesAndSeasons: GetAnimeWithEpisodesAndSeasons,
    // <-- AM
    private val updateEpisode: UpdateEpisode,
    private val getCategories: GetCategories,
    private val setAnimeCategories: SetAnimeCategories,
    private val getTracks: GetTracks,
    private val upsertTrack: UpsertTrack,
    private val coverCache: CoverCache,
    // AY -->
    private val backgroundCache: BackgroundCache,
    // <-- AY
    private val updateAnimeFromRemote: UpdateAnimeFromRemote,
) {
    private val enhancedServices by lazy { trackerManager.trackers.filterIsInstance<EnhancedTracker>() }

    suspend operator fun invoke(current: Anime, target: Anime, replace: Boolean, isSeason: Boolean = false) {
        val targetSource = sourceManager.get(target.source) ?: return
        val currentSource = sourceManager.get(current.source)
        val flags = sourcePreferences.migrationFlags.get()

        try {
            val fetchResult = when (target.fetchType) {
                // AY -->
                FetchType.Seasons -> {
                    updateAnimeFromRemote.awaitSeasonsUpdate(target, fetchSeasons = true).getOrThrow()
                        .let { FetchResult.Seasons(it) }
                }
                // <-- AY
                FetchType.Episodes -> {
                    updateAnimeFromRemote.awaitEpisodesUpdate(target, fetchEpisodes = true).getOrThrow()
                        .let { FetchResult.Episodes(it) }
                }
            }

            // Update episodes seen, bookmark, filler, and dateFetch
            // AY -->
            if (MigrationFlag.EPISODE in flags && target.fetchType == FetchType.Episodes) {
                // <-- AY
                val prevAnimeEpisodes = getEpisodesByAnimeId.await(current.id)
                val animeEpisodes = getEpisodesByAnimeId.await(target.id)

                val maxEpisodeSeen = prevAnimeEpisodes
                    .filter { it.seen }
                    .maxOfOrNull { it.episodeNumber }

                val episodeUpdates = animeEpisodes
                    .filter { it.isRecognizedNumber }
                    .map { animeEpisode ->
                        val prevEpisode = prevAnimeEpisodes
                            .find { it.isRecognizedNumber && it.episodeNumber == animeEpisode.episodeNumber }

                        EpisodeUpdate(animeEpisode.id) {
                            if (prevEpisode != null) {
                                dateFetch = prevEpisode.dateFetch
                                bookmark = prevEpisode.bookmark
                                // AM -->
                                fillermark = prevEpisode.fillermark
                                totalSeconds = prevEpisode.totalSeconds
                                lastSecondSeen = prevEpisode.lastSecondSeen
                                // <-- AM
                            }
                            if (maxEpisodeSeen != null && animeEpisode.episodeNumber <= maxEpisodeSeen) {
                                seen = true
                            }
                        }
                    }

                updateEpisode.awaitAll(episodeUpdates)
            }

            // AM -->
            // Update seasons
            if (MigrationFlag.SEASON in flags && target.fetchType == FetchType.Seasons) {
                val currentSeasons = getAnimeWithEpisodesAndSeasons.awaitSeasons(current.id)
                val targetSeasons = (fetchResult as? FetchResult.Seasons)?.update?.newSeasons.orEmpty()

                currentSeasons.forEach { c ->
                    val match = targetSeasons.find { t -> abs(c.anime.seasonNumber - t.seasonNumber) < 0.001 }
                    match?.let { invoke(c.anime, it, replace, isSeason = true) }
                }
            }
            // <-- AM

            // Update categories
            if (MigrationFlag.CATEGORY in flags) {
                val categoryIds = getCategories.await(current.id).map { it.id }
                setAnimeCategories.await(target.id, categoryIds)
            }

            // Update track
            getTracks.await(current.id).mapNotNull { track ->
                val updatedTrack = track.copy(animeId = target.id)

                val service = enhancedServices
                    .firstOrNull { it.isTrackFrom(updatedTrack, current, currentSource) }

                if (service != null) {
                    service.migrateTrack(updatedTrack, target, targetSource)
                } else {
                    updatedTrack
                }
            }
                .takeIf { it.isNotEmpty() }
                ?.let { upsertTrack.awaitAll(it) }

            // Delete downloaded
            // AY -->
            if (MigrationFlag.REMOVE_DOWNLOAD in flags && currentSource != null &&
                current.fetchType == FetchType.Episodes
            ) {
                // <-- AY
                scope.launch { downloadManager.deleteAnime(current, currentSource) }
            }

            // Update custom cover (recheck if custom cover exists)
            if (MigrationFlag.CUSTOM_COVER in flags && current.hasCustomCover()) {
                coverCache.getCustomCoverFile(current.id).inputStream().use {
                    coverCache.setCustomCoverToCache(target, it)
                }
            }

            // AM -->
            // Update custom background (recheck if custom background exists)
            if (MigrationFlag.CUSTOM_BACKGROUND in flags && current.hasCustomBackground()) {
                backgroundCache.getCustomBackgroundFile(current.id).inputStream().use {
                    backgroundCache.setCustomBackgroundToCache(target, it)
                }
            }
            // <-- AM

            val currentAnimeUpdate = AnimeUpdate(current.id) {
                favoriteAt = null
            }
                .takeIf { replace }
            val targetAnimeUpdate = AnimeUpdate(target.id) {
                // AM -->
                if (isSeason) {
                    current.favoriteAt?.takeIf { replace }?.let {
                        favoriteAt = it
                    }
                } else {
                    favoriteAt = current.favoriteAt?.takeIf { replace } ?: Clock.System.now()
                }
                when (current.fetchType) {
                    FetchType.Seasons -> seasonFlags = current.seasonFlags
                    FetchType.Episodes -> episodeFlags = current.episodeFlags
                }
                // <-- AM
                viewerFlags = current.viewerFlags
                if (MigrationFlag.NOTES in flags) notes = current.notes
            }

            updateAnime.awaitAll(listOfNotNull(currentAnimeUpdate, targetAnimeUpdate))
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
        }
    }

    private sealed interface FetchResult {
        data class Seasons(val update: RemoteAnimeSeasonUpdate) : FetchResult
        data class Episodes(val update: RemoteAnimeEpisodeUpdate) : FetchResult
    }
}
