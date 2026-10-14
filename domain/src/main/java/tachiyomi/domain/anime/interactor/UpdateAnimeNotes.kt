package tachiyomi.domain.anime.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.anime.model.AnimeUpdate
import tachiyomi.domain.anime.repository.AnimeRepository

@Inject
class UpdateAnimeNotes(
    private val animeRepository: AnimeRepository,
) {

    suspend operator fun invoke(animeId: Long, notes: String): Boolean {
        return animeRepository.update(
            AnimeUpdate(animeId) {
                this.notes = notes
            },
        )
    }
}
