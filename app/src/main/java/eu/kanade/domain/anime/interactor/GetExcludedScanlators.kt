package eu.kanade.domain.anime.interactor

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.anime.repository.AnimeRepository

@Inject
class GetExcludedScanlators(
    private val animeRepository: AnimeRepository,
) {

    suspend fun await(animeId: Long): Set<String> {
        return animeRepository.getExcludedScanlators(animeId)
    }

    fun subscribe(animeId: Long): Flow<Set<String>> {
        return animeRepository.getExcludedScanlatorsAsFlow(animeId)
    }
}
