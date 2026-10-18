// AM -->
package eu.kanade.domain.anime.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.anime.model.AnimeCustomUpdate
import tachiyomi.domain.anime.repository.AnimeRepository

@Inject
class UpdateCustomInfo(
    private val animeRepository: AnimeRepository,
) {
    suspend fun await(customInfo: AnimeCustomUpdate) {
        animeRepository.updateCustomInfo(customInfo)
    }
}
// <-- AM
