package eu.kanade.domain.track.interactor

import dev.zacsweers.metro.Inject
import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.domain.track.model.toDomainTrack
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.EnhancedTracker
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.util.lang.convertEpochMillisZone
import kotlinx.datetime.TimeZone
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.season.interactor.GetAnimeSeasonsByParentId
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.InsertTrack

@Inject
class AddTracks(
    private val insertTrack: InsertTrack,
    private val syncEpisodeProgressWithTrack: SyncEpisodeProgressWithTrack,
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId,
    private val trackerManager: TrackerManager,
    private val getHistory: GetHistory,
    // AM -->
    private val getAnimeSeasonsByParentId: GetAnimeSeasonsByParentId,
    private val sourceManager: SourceManager,
    // <-- AM
) {

    // TODO: update all trackers based on common data
    suspend fun bind(tracker: Tracker, item: Track, anime: Anime) = withNonCancellableContext {
        withIOContext {
            val allEpisodes = getEpisodesByAnimeId.await(anime.id)
            val hasSeenEpisodes = allEpisodes.any { it.seen }
            tracker.bind(item, hasSeenEpisodes)

            var track = item.toDomainTrack(idRequired = false) ?: return@withIOContext

            insertTrack.await(track)

            // AM -->
            when (anime.fetchType) {
                FetchType.Seasons -> { }
                // <-- AM
                FetchType.Episodes -> {
                    // TODO: merge into [SyncEpisodeProgressWithTrack]?
                    // Update episode progress if newer episodes marked seen locally
                    if (hasSeenEpisodes) {
                        val latestLocalSeenEpisodeNumber = allEpisodes
                            .sortedBy { it.episodeNumber }
                            .takeWhile { it.seen }
                            .lastOrNull()
                            ?.episodeNumber ?: -1.0

                        if (latestLocalSeenEpisodeNumber > track.lastEpisodeSeen) {
                            track = track.copy(
                                lastEpisodeSeen = latestLocalSeenEpisodeNumber,
                            )
                            tracker.setRemoteLastEpisodeSeen(track.toDbTrack(), latestLocalSeenEpisodeNumber.toInt())
                        }

                        if (track.startDate <= 0) {
                            val firstSeenEpisodeDate = getHistory.await(anime.id)
                                .mapNotNull { it.seenAt?.takeIf { seenAt -> seenAt.time > 0 } }
                                .minOrNull()

                            firstSeenEpisodeDate?.let {
                                val startDate = firstSeenEpisodeDate.time.convertEpochMillisZone(
                                    TimeZone.currentSystemDefault(),
                                    TimeZone.UTC,
                                )
                                track = track.copy(
                                    startDate = startDate,
                                )
                                tracker.setRemoteStartDate(track.toDbTrack(), startDate)
                            }
                        }
                    }

                    syncEpisodeProgressWithTrack.await(anime.id, track, tracker)
                }
            }

            // AM -->
            val source = sourceManager.getOrStub(anime.source)
            bindEnhancedTrackers(anime, source)
            // <-- AM
        }
    }

    suspend fun bindEnhancedTrackers(anime: Anime, source: AnimeSource) {
        withNonCancellableContext {
            withIOContext {
                trackerManager.loggedInTrackers()
                    .filterIsInstance<EnhancedTracker>()
                    .filter { it.accept(source) }
                    .forEach { service ->
                        try {
                            // AM -->
                            val match = when (anime.fetchType) {
                                FetchType.Seasons -> service.matchSeason(anime)
                                FetchType.Episodes -> service.match(anime)
                            }
                            // <-- AM

                            match?.let { track ->
                                track.anime_id = anime.id
                                (service as Tracker).bind(track)
                                insertTrack.await(track.toDomainTrack(idRequired = false)!!)

                                when (anime.fetchType) {
                                    // AM -->
                                    FetchType.Seasons -> {
                                        val seasons = getAnimeSeasonsByParentId.await(anime.id)
                                        seasons.filter { it.anime.fetchType == FetchType.Episodes }.forEach { s ->
                                            bindEnhancedTrackers(s.anime, source)
                                        }
                                    }
                                    // <-- AM
                                    FetchType.Episodes -> {
                                        syncEpisodeProgressWithTrack.await(
                                            anime.id,
                                            track.toDomainTrack(idRequired = false)!!,
                                            service,
                                        )
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            logcat(
                                LogPriority.WARN,
                                e,
                            ) { "Could not match anime: ${anime.title} with service $service" }
                        }
                    }
            }
        }
    }
}
