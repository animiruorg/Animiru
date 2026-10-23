package tachiyomi.domain.anime.interactor

import dev.zacsweers.metro.Inject
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
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

    /**
     * Works out the entry's fetch interval and the next day to check it for new episodes.
     *
     * @param foundRelease whether the check that led here found new episodes.
     */
    suspend fun withFetchInterval(
        anime: Anime,
        dateTime: LocalDateTime,
        timeZone: TimeZone,
        window: ClosedRange<Instant>?,
        foundRelease: Boolean = false,
    ): AnimeUpdate {
        val today = dateTime.date
        val currentWindow = window ?: getWindow(Clock.System.now().toLocalDateTime(timeZone).date, timeZone)
        val schedule = if (anime.fetchInterval < 0) {
            // The check that found a release is the entry's last update
            val lastUpdate = if (foundRelease) today else anime.lastUpdate?.toLocalDateTime(timeZone)?.date
            ReleaseSchedule.fixed(-anime.fetchInterval, lastUpdate ?: today)
        } else {
            val episodes = getEpisodesByAnimeId.await(anime.id, applyScanlatorFilter = true)
            ReleaseSchedule.estimate(episodes, timeZone, today)
        }

        val expected = anime.nextUpdate
        val expectedDay = expected?.toLocalDateTime(timeZone)?.date
        // A release on or after the expected day means the next one is counted from it instead
        val isAwaitingRelease = expected != null &&
            expectedDay != null &&
            expected in currentWindow.start..currentWindow.endInclusive + 1.milliseconds &&
            (schedule.lastRelease == null || schedule.lastRelease < expectedDay)
        val nextUpdate = when {
            foundRelease -> schedule.nextCheckAfterRelease(today).atStartOfDayIn(timeZone)
            isAwaitingRelease -> expected
            else -> schedule.nextCheck(today).atStartOfDayIn(timeZone)
        }

        return AnimeUpdate(anime.id) {
            this.nextUpdate = nextUpdate
            fetchInterval = if (anime.fetchInterval < 0) anime.fetchInterval else schedule.interval
        }
    }

    fun getWindow(localDateTime: LocalDate, timeZone: TimeZone): ClosedRange<Instant> {
        val today = localDateTime.atStartOfDayIn(timeZone)
        return (today - GRACE_PERIOD.days)..(today + GRACE_PERIOD.days)
    }

    companion object {
        const val MAX_INTERVAL = 28

        private const val GRACE_PERIOD = 1L
    }
}
