package eu.kanade.domain.anime.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.anime.repository.AnimeRepository

@Inject
class SetExcludedScanlators(
    private val animeRepository: AnimeRepository,
) {

    suspend fun await(animeId: Long, excludedScanlators: Set<String>) {
        animeRepository.setExcludedScanlators(animeId, excludedScanlators)
    }
}
