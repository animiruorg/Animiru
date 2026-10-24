package tachiyomi.domain.episode.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.episode.model.EpisodeSummary
import tachiyomi.domain.episode.repository.EpisodeRepository

@Inject
class GetEpisodeSummaryByAnimeId(
    private val episodeRepository: EpisodeRepository,
) {

    suspend fun await(animeId: Long): EpisodeSummary {
        return episodeRepository.getEpisodeSummaryByAnimeId(animeId)
    }
}
