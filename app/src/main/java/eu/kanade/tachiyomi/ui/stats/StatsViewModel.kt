package eu.kanade.tachiyomi.ui.stats

import androidx.compose.ui.util.fastDistinctBy
import androidx.compose.ui.util.fastFilter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.core.util.fastCountNot
import eu.kanade.presentation.more.stats.StatsScreenState
import eu.kanade.presentation.more.stats.data.StatsData
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.track.TrackerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.anime.interactor.GetLibraryAnime
import tachiyomi.domain.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.library.model.LibraryAnime
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ANIME_HAS_UNSEEN
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ANIME_NON_COMPLETED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ANIME_NON_SEEN
import tachiyomi.domain.track.interactor.GetTracksPerAnime
import tachiyomi.domain.track.model.Track
import tachiyomi.source.local.isLocal

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class StatsViewModel(
    private val downloadManager: DownloadManager,
    private val getLibraryAnime: GetLibraryAnime,
    // AY -->
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId,
    // <-- AY
    private val getTracksPerAnime: GetTracksPerAnime,
    private val preferences: LibraryPreferences,
    private val trackerManager: TrackerManager,
) : ViewModel() {

    val state: StateFlow<StatsScreenState>
        field = MutableStateFlow<StatsScreenState>(StatsScreenState.Loading)

    private val loggedInTrackers by lazy { trackerManager.loggedInTrackers() }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val libraryAnime = getLibraryAnime.await()

            val distinctLibraryAnime = libraryAnime.fastDistinctBy { it.id }

            val animeTrackMap = getAnimeTrackMap(distinctLibraryAnime)
            val scoredAnimeTrackerMap = getScoredAnimeTrackMap(animeTrackMap)

            val meanScore = getTrackMeanScore(scoredAnimeTrackerMap)

            val overviewStatData = StatsData.Overview(
                libraryAnimeCount = distinctLibraryAnime.size,
                completedAnimeCount = distinctLibraryAnime.count {
                    it.anime.status.toInt() == SAnime.COMPLETED && it.unseenCount == 0L
                },
                // AY -->
                totalSeenDuration = getWatchTime(distinctLibraryAnime),
                // <-- AY
            )

            val titlesStatData = StatsData.Titles(
                globalUpdateItemCount = getGlobalUpdateItemCount(libraryAnime),
                startedAnimeCount = distinctLibraryAnime.count { it.hasStarted },
                localAnimeCount = distinctLibraryAnime.count { it.anime.isLocal() },
            )

            val episodesStatData = StatsData.Episodes(
                totalEpisodeCount = distinctLibraryAnime.sumOf { it.totalCount }.toInt(),
                seenEpisodeCount = distinctLibraryAnime.sumOf { it.seenCount }.toInt(),
                downloadCount = downloadManager.getDownloadCount(),
            )

            val trackersStatData = StatsData.Trackers(
                trackedTitleCount = animeTrackMap.count { it.value.isNotEmpty() },
                meanScore = meanScore,
                trackerCount = loggedInTrackers.size,
            )

            state.update {
                StatsScreenState.Success(
                    overview = overviewStatData,
                    titles = titlesStatData,
                    episodes = episodesStatData,
                    trackers = trackersStatData,
                )
            }
        }
    }

    private fun getGlobalUpdateItemCount(libraryAnime: List<LibraryAnime>): Int {
        val includedCategories = preferences.updateCategories.get().map { it.toLong() }
        val excludedCategories = preferences.updateCategoriesExclude.get().map { it.toLong() }
        val updateRestrictions = preferences.autoUpdateAnimeRestrictions.get()

        return libraryAnime.filter {
            val included = includedCategories.isEmpty() || it.categories.intersect(includedCategories).isNotEmpty()
            val excluded = it.categories.intersect(excludedCategories).isNotEmpty()
            included && !excluded
        }
            .fastCountNot {
                (ANIME_NON_COMPLETED in updateRestrictions && it.anime.status.toInt() == SAnime.COMPLETED) ||
                    (ANIME_HAS_UNSEEN in updateRestrictions && it.unseenCount != 0L) ||
                    (ANIME_NON_SEEN in updateRestrictions && it.totalCount > 0 && !it.hasStarted)
            }
    }

    private suspend fun getAnimeTrackMap(libraryAnime: List<LibraryAnime>): Map<Long, List<Track>> {
        val loggedInTrackerIds = loggedInTrackers.map { it.id }.toHashSet()
        if (loggedInTrackerIds.isEmpty()) return emptyMap()

        val tracksPerAnime = getTracksPerAnime.await()
        return libraryAnime.associate { anime ->
            val tracks = tracksPerAnime[anime.id].orEmpty()
                .fastFilter { it.trackerId in loggedInTrackerIds }

            anime.id to tracks
        }
    }

    // AY -->
    private suspend fun getWatchTime(libraryAnimeList: List<LibraryAnime>): Long {
        var watchTime = 0L
        libraryAnimeList.forEach { libraryAnime ->
            getEpisodesByAnimeId.await(libraryAnime.anime.id).forEach { episode ->
                watchTime += if (episode.seen) {
                    episode.totalSeconds
                } else {
                    episode.lastSecondSeen
                }
            }
        }

        return watchTime
    }
    // <-- AY

    private fun getScoredAnimeTrackMap(animeTrackMap: Map<Long, List<Track>>): Map<Long, List<Track>> {
        return animeTrackMap.mapNotNull { (animeId, tracks) ->
            val trackList = tracks.mapNotNull { track ->
                track.takeIf { it.score > 0.0 }
            }
            if (trackList.isEmpty()) return@mapNotNull null
            animeId to trackList
        }.toMap()
    }

    private fun getTrackMeanScore(scoredAnimeTrackMap: Map<Long, List<Track>>): Double {
        return scoredAnimeTrackMap
            .map { (_, tracks) ->
                tracks.map(::get10PointScore).average()
            }
            .fastFilter { !it.isNaN() }
            .average()
    }

    private fun get10PointScore(track: Track): Double {
        val service = trackerManager.get(track.trackerId)!!
        return service.get10PointScore(track)
    }
}
