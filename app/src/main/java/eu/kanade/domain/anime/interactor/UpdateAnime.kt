package eu.kanade.domain.anime.interactor

import dev.zacsweers.metro.Inject
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import tachiyomi.domain.anime.interactor.FetchInterval
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.model.AnimeUpdate
import tachiyomi.domain.anime.repository.AnimeRepository
import kotlin.time.Clock
import kotlin.time.Instant

@Inject
class UpdateAnime(
    private val animeRepository: AnimeRepository,
    private val fetchInterval: FetchInterval,
) {

    suspend fun await(animeUpdate: AnimeUpdate): Boolean {
        return animeRepository.update(animeUpdate)
    }

    suspend fun awaitAll(animeUpdates: List<AnimeUpdate>): Boolean {
        return animeRepository.updateAll(animeUpdates)
    }

    suspend fun awaitUpdateFetchInterval(
        anime: Anime,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
        dateTime: LocalDateTime = Clock.System.now().toLocalDateTime(timeZone),
        window: ClosedRange<Instant>? = fetchInterval.getWindow(dateTime.date, timeZone),
    ): Boolean {
        return animeRepository.update(
            fetchInterval.withFetchInterval(anime, dateTime, timeZone, window),
        )
    }

    suspend fun awaitUpdateLastUpdate(animeId: Long): Boolean {
        return animeRepository.update(
            AnimeUpdate(animeId) {
                lastUpdate = Clock.System.now()
            },
        )
    }

    suspend fun awaitUpdateCoverLastModified(animeId: Long): Boolean {
        return animeRepository.update(
            AnimeUpdate(animeId) {
                coverLastModified = Clock.System.now()
            },
        )
    }

    // AY -->
    suspend fun awaitUpdateBackgroundLastModified(animeId: Long): Boolean {
        return animeRepository.update(
            AnimeUpdate(animeId) {
                backgroundLastModified = Clock.System.now()
            },
        )
    }
    // <-- AY

    suspend fun awaitUpdateFavorite(animeId: Long, favorite: Boolean): Boolean {
        val update = when (favorite) {
            true -> AnimeUpdate(animeId) { favoriteAt = Clock.System.now() }
            false -> AnimeUpdate(animeId) { favoriteAt = null }
        }
        return animeRepository.update(update)
    }
}
