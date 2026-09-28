package tachiyomi.domain.episode.interactor

import dev.zacsweers.metro.Inject
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.episode.model.EpisodeUpdate
import tachiyomi.domain.episode.repository.EpisodeRepository
import kotlin.time.Clock

@Inject
class UpdateEpisode(
    private val episodeRepository: EpisodeRepository,
) {

    suspend fun await(episodeUpdate: EpisodeUpdate) {
        try {
            episodeRepository.update(episodeUpdate)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }

    suspend fun awaitAll(episodeUpdates: List<EpisodeUpdate>) {
        try {
            episodeRepository.updateAll(episodeUpdates)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }

    suspend fun awaitUpdateThumbnailLastModified(episodeId: Long) {
        episodeRepository.update(
            EpisodeUpdate(
                id = episodeId,
                thumbnailLastModifiedAt = Clock.System.now().toEpochMilliseconds(),
            ),
        )
    }
}
