package eu.kanade.domain.track.interactor

import dev.zacsweers.metro.Inject
import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.domain.track.model.toDomainTrack
import eu.kanade.tachiyomi.data.track.EnhancedTracker
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.UpsertTrack
import tachiyomi.domain.track.model.Track

@Inject
class RefreshTracks(
    private val getTracks: GetTracks,
    private val trackerManager: TrackerManager,
    private val upsertTrack: UpsertTrack,
    private val syncEpisodeProgressWithTrack: SyncEpisodeProgressWithTrack,
) {

    /**
     * Fetches updated tracking data from all logged in trackers.
     *
     * @return Failed updates.
     */
    suspend fun await(
        animeId: Long,
        // AM -->
        enhancedOnly: Boolean = false,
        skipCompleted: Boolean = false,
        // <-- AM
    ): List<RefreshResult> {
        return supervisorScope {
            return@supervisorScope getTracks.await(animeId)
                .map { it to trackerManager.get(it.trackerId) }
                .filter { (_, service) -> service?.isLoggedIn == true }
                // AM -->
                .filter { (_, service) -> !enhancedOnly || service is EnhancedTracker }
                // <-- AM
                .map { (track, service) ->
                    async(Dispatchers.IO) {
                        return@async try {
                            // AM -->
                            if (!(skipCompleted && track.totalEpisodes == track.lastEpisodeSeen.toLong())) {
                                // <-- AM
                                val updatedTrack = service!!.refresh(track.toDbTrack()).toDomainTrack()!!
                                upsertTrack.await(updatedTrack)
                                syncEpisodeProgressWithTrack.await(animeId, updatedTrack, service)
                            }

                            RefreshResult.Success(track)
                        } catch (e: Throwable) {
                            RefreshResult.Failure(service!!, e)
                        }
                    }
                }
                .awaitAll()
        }
    }
}

// AM -->
sealed interface RefreshResult {
    data class Failure(val tracker: Tracker, val error: Throwable) : RefreshResult
    data class Success(val track: Track) : RefreshResult
}
// <-- AM
