package tachiyomi.domain.anime.interactor

import dev.zacsweers.metro.Inject
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.model.AnimeUpdate
import tachiyomi.domain.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.episode.model.Episode
import kotlin.math.absoluteValue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

@Inject
class FetchInterval(
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId,
) {

    suspend fun withFetchInterval(
        anime: Anime,
        dateTime: LocalDateTime,
        timeZone: TimeZone,
        window: ClosedRange<Instant>?,
    ): AnimeUpdate {
        val interval = anime.fetchInterval.takeIf { it < 0 } ?: calculateInterval(
            episodes = getEpisodesByAnimeId.await(anime.id, applyScanlatorFilter = true),
            zone = timeZone,
        )
        val currentWindow = window ?: getWindow(Clock.System.now().toLocalDateTime(timeZone).date, timeZone)
        val nextUpdate = calculateNextUpdate(anime, interval, dateTime, timeZone, currentWindow)

        return AnimeUpdate(anime.id) {
            this.nextUpdate = nextUpdate
            fetchInterval = interval
        }
    }

    fun getWindow(localDateTime: LocalDate, timeZone: TimeZone): ClosedRange<Instant> {
        val today = localDateTime.atStartOfDayIn(timeZone)
        return (today - GRACE_PERIOD.days)..(today + GRACE_PERIOD.days)
    }

    internal fun calculateInterval(episodes: List<Episode>, zone: TimeZone): Int {
        val episodeWindow = if (episodes.size <= 8) 3 else 10

        val uploadDates = episodes.asSequence()
            .mapNotNull { it.dateUpload }
            .sortedDescending()
            .map {
                it.toLocalDateTime(zone)
                    .date
                    .atStartOfDayIn(zone)
            }
            .distinct()
            .take(episodeWindow)
            .toList()

        val fetchDates = episodes.asSequence()
            .map { it.dateFetch }
            .sortedDescending()
            .map {
                it.toLocalDateTime(zone)
                    .date
                    .atStartOfDayIn(zone)
            }
            .distinct()
            .take(episodeWindow)
            .toList()

        val interval = when {
            // Enough upload date from source
            uploadDates.size >= 3 -> {
                val ranges = uploadDates.windowed(2).map { x -> x[1].daysUntil(x[0], zone) }.sorted()
                ranges[(ranges.size - 1) / 2]
            }
            // Enough fetch date from client
            fetchDates.size >= 3 -> {
                val ranges = fetchDates.windowed(2).map { x -> x[1].daysUntil(x[0], zone) }.sorted()
                ranges[(ranges.size - 1) / 2]
            }
            // Default to 7 days
            else -> 7
        }

        return interval.coerceIn(1, MAX_INTERVAL)
    }

    private fun calculateNextUpdate(
        anime: Anime,
        interval: Int,
        dateTime: LocalDateTime,
        timeZone: TimeZone,
        window: ClosedRange<Instant>,
    ): Instant {
        if (anime.nextUpdate != null && anime.nextUpdate in window.start..window.endInclusive + 1.milliseconds) {
            return anime.nextUpdate
        }

        val instant = anime.nextUpdate ?: Clock.System.now()
        val latestDate = instant.toLocalDateTime(timeZone).date.atStartOfDayIn(timeZone)

        val daysSinceLatest = (dateTime.toInstant(timeZone) - latestDate).inWholeDays
        val cycle = daysSinceLatest.floorDiv(
            interval.absoluteValue.takeIf { interval < 0 }
                ?: increaseInterval(interval, daysSinceLatest, increaseWhenOver = 10),
        )

        val offsetDays = ((cycle + 1) * interval.absoluteValue.toLong()).days
        return latestDate.plus(offsetDays)
    }

    private fun increaseInterval(delta: Int, daysSinceLatest: Long, increaseWhenOver: Int): Int {
        if (delta >= MAX_INTERVAL) return MAX_INTERVAL

        // double delta again if missed more than 9 check in new delta
        val cycle = daysSinceLatest.floorDiv(delta) + 1
        return if (cycle > increaseWhenOver) {
            increaseInterval(delta * 2, daysSinceLatest, increaseWhenOver)
        } else {
            delta
        }
    }

    companion object {
        const val MAX_INTERVAL = 28

        private const val GRACE_PERIOD = 1L
    }
}
