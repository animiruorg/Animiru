package tachiyomi.domain.anime.interactor

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.episode.model.Episode
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

@Execution(ExecutionMode.CONCURRENT)
class FetchIntervalTest {

    private val testTime = LocalDateTime.parse("2020-01-01T00:00:00")
    private val testTimeZone = TimeZone.UTC
    private var episode = Episode.create().copy(
        dateFetch = testTime.toInstant(testTimeZone),
        dateUpload = testTime.toInstant(testTimeZone),
    )

    @Test
    fun `returns default interval of 7 days when not enough distinct days`() {
        val episodesWithUploadDate = (1..50).map {
            episodeWithTime(episode, 1.days)
        }
        interval(episodesWithUploadDate) shouldBe 7

        val episodesWithoutUploadDate = episodesWithUploadDate.map {
            it.copy(dateUpload = null)
        }
        interval(episodesWithoutUploadDate) shouldBe 7
    }

    @Test
    fun `returns interval based on more recent episodes`() {
        val oldEpisodes = (1..5).map {
            episodeWithTime(episode, (it * 7).days) // Would have interval of 7 days
        }
        val newEpisodes = (1..10).map {
            episodeWithTime(episode, oldEpisodes.lastUploadDate() + it.days)
        }

        val episodes = oldEpisodes + newEpisodes

        interval(episodes) shouldBe 1
    }

    @Test
    fun `returns interval based on smaller subset of recent episodes if very few episodes`() {
        val oldEpisodes = (1..3).map {
            episodeWithTime(episode, (it * 7).days)
        }
        // Significant gap between episodes
        val newEpisodes = (1..3).map {
            episodeWithTime(episode, oldEpisodes.lastUploadDate() + 365.days + (it * 7).days)
        }

        val episodes = oldEpisodes + newEpisodes

        interval(episodes) shouldBe 7
    }

    @Test
    fun `returns interval of 7 days when multiple episodes in 1 day`() {
        val episodes = (1..10).map {
            episodeWithTime(episode, 10.hours)
        }
        interval(episodes) shouldBe 7
    }

    @Test
    fun `returns interval of 7 days when multiple episodes in 2 days`() {
        val episodes = (1..2).map {
            episodeWithTime(episode, 1.days)
        } + (1..5).map {
            episodeWithTime(episode, 2.days)
        }
        interval(episodes) shouldBe 7
    }

    @Test
    fun `returns interval of 1 day when episodes are released every 1 day`() {
        val episodes = (1..20).map {
            episodeWithTime(episode, it.days)
        }
        interval(episodes) shouldBe 1
    }

    @Test
    fun `returns interval of 1 day when delta is less than 1 day`() {
        val episodes = (1..20).map {
            episodeWithTime(episode, (15 * it).hours)
        }
        interval(episodes) shouldBe 1
    }

    @Test
    fun `returns interval of 2 days when episodes are released every 2 days`() {
        val episodes = (1..20).map {
            episodeWithTime(episode, (2 * it).days)
        }
        interval(episodes) shouldBe 2
    }

    @Test
    fun `returns interval with floored value when interval is decimal`() {
        val episodesWithUploadDate = (1..5).map {
            episodeWithTime(episode, (25 * it).hours)
        }
        interval(episodesWithUploadDate) shouldBe 1

        val episodesWithoutUploadDate = episodesWithUploadDate.map {
            it.copy(dateUpload = null)
        }
        interval(episodesWithoutUploadDate) shouldBe 1
    }

    @Test
    fun `returns interval of 2 days when episodes are released just below every 2 days`() {
        val episodes = (1..20).map {
            episodeWithTime(episode, (43 * it).hours)
        }
        interval(episodes) shouldBe 2
    }

    @Test
    fun `follows releases on a few fixed weekdays`() = runTest {
        val mondaysAndThursdays = generateSequence(LocalDate.parse("2019-12-02")) { it.plus(DatePeriod(days = 1)) }
            .takeWhile { it <= LocalDate.parse("2020-01-20") }
            .filter { it.dayOfWeek == DayOfWeek.MONDAY || it.dayOfWeek == DayOfWeek.THURSDAY }
            .toList()
        val episodes = episodesOn(mondaysAndThursdays)

        ReleaseSchedule.estimate(episodes, testTimeZone, LocalDate.parse("2020-01-21")).pattern shouldBe
            ReleasePattern.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY))
        nextUpdate(today = "2020-01-21", episodes) shouldBe LocalDate.parse("2020-01-23")
    }

    @Test
    fun `follows releases on a day of the month`() = runTest {
        val episodes = episodesOn(
            listOf("2019-09-15", "2019-10-15", "2019-11-14", "2019-12-15", "2020-01-16").map(LocalDate::parse),
            perDay = 2,
        )

        nextUpdate(today = "2020-01-20", episodes) shouldBe LocalDate.parse("2020-02-15")
    }

    @Test
    fun `follows releases on the last day of the month`() = runTest {
        val episodes = episodesOn(
            listOf("2019-09-30", "2019-10-31", "2019-11-30", "2019-12-31", "2020-01-31").map(LocalDate::parse),
            perDay = 2,
        )

        nextUpdate(today = "2020-02-05", episodes) shouldBe LocalDate.parse("2020-02-29")
    }

    @Test
    fun `follows releases on two days of the month`() = runTest {
        val days =
            listOf("2019-10-01", "2019-10-15", "2019-11-01", "2019-11-16", "2019-12-01", "2019-12-15", "2020-01-01")
        val episodes = episodesOn((days + "2020-01-15").map(LocalDate::parse), perDay = 2)

        ReleaseSchedule.estimate(episodes, testTimeZone, LocalDate.parse("2020-01-20")).pattern shouldBe
            ReleasePattern.DaysOfMonth(setOf(1, 15))
        nextUpdate(today = "2020-01-20", episodes) shouldBe LocalDate.parse("2020-02-01")
    }

    @Test
    fun `follows releases on a weekday of the month`() = runTest {
        val firstSaturdays = listOf("2019-09-07", "2019-10-05", "2019-11-02", "2019-12-07", "2020-01-04")
        val episodes = episodesOn(firstSaturdays.map(LocalDate::parse), perDay = 2)

        nextUpdate(today = "2020-01-10", episodes) shouldBe LocalDate.parse("2020-02-01")
    }

    @Test
    fun `follows releases on a weekday of the last week of the month`() = runTest {
        val lastFridays = listOf("2019-09-27", "2019-10-25", "2019-11-29", "2019-12-27", "2020-01-31")
        val episodes = episodesOn(lastFridays.map(LocalDate::parse), perDay = 2)

        nextUpdate(today = "2020-02-03", episodes) shouldBe LocalDate.parse("2020-02-28")
    }

    @Test
    fun `follows releases in batches`() = runTest {
        // Three days of releases every three weeks
        val batches = every(days = 21, from = "2019-09-02", to = "2019-12-16")
            .flatMap { start -> (0..2).map { start.plus(DatePeriod(days = it)) } }
        val episodes = episodesOn(batches.filter { it <= LocalDate.parse("2019-12-17") })

        nextUpdate(today = "2019-12-17", episodes, foundRelease = true) shouldBe LocalDate.parse("2019-12-18")
        nextUpdate(today = "2019-12-18", episodesOn(batches), foundRelease = true) shouldBe
            LocalDate.parse("2020-01-06")
    }

    @Test
    fun `checks again around when an earlier break ended`() = runTest {
        // A season of weekly releases, a 98 day break, and another season
        val episodes = episodesOn(weekly(from = "2019-01-02", to = "2019-03-27") + weekly("2019-07-03", "2019-09-25"))

        nextUpdate(today = "2019-12-20", episodes) shouldBe LocalDate.parse("2019-12-25")
        nextUpdate(today = "2019-12-26", episodes) shouldBe LocalDate.parse("2020-01-01")
    }

    @Test
    fun `never waits more than 28 days between checks`() = runTest {
        val lastDays = listOf("2019-09-30", "2019-10-31", "2019-11-30", "2019-12-31", "2020-01-31")
        val episodes = episodesOn(lastDays.map(LocalDate::parse), perDay = 2)

        nextUpdate(today = "2020-01-31", episodes, foundRelease = true) shouldBe LocalDate.parse("2020-02-28")
    }

    @Test
    fun `keeps checking on the expected day`() = runTest {
        val episodes = episodesOn(weekly(from = "2019-12-04", to = "2019-12-25"))

        nextUpdate(today = "2020-01-01", episodes) shouldBe LocalDate.parse("2020-01-01")
    }

    @Test
    fun `keeps a next update in the window while nothing new came out`() = runTest {
        val episodes = episodesOn(weekly(from = "2019-12-04", to = "2019-12-25"))
        val anime = Anime.create().copy(nextUpdate = LocalDate.parse("2020-01-01").atStartOfDayIn(testTimeZone))

        nextUpdate(today = "2020-01-02", episodes, anime) shouldBe LocalDate.parse("2020-01-01")
    }

    @Test
    fun `counts from a release on the expected day`() = runTest {
        val episodes = episodesOn(weekly(from = "2019-12-04", to = "2020-01-01"))
        val anime = Anime.create().copy(nextUpdate = LocalDate.parse("2020-01-01").atStartOfDayIn(testTimeZone))

        nextUpdate(today = "2020-01-01", episodes, anime) shouldBe LocalDate.parse("2020-01-08")
    }

    @Test
    fun `keeps the schedule for a release uploaded the day before it was found`() = runTest {
        val episodes = episodesOn(weekly(from = "2019-10-02", to = "2019-12-25") + LocalDate.parse("2019-12-31"))

        nextUpdate(today = "2020-01-01", episodes, expecting("2020-01-01"), foundRelease = true) shouldBe
            LocalDate.parse("2020-01-08")
    }

    @Test
    fun `keeps the schedule after a release shortly before the expected day`() = runTest {
        val episodes = episodesOn(weekly(from = "2019-10-02", to = "2019-12-25") + LocalDate.parse("2019-12-30"))

        nextUpdate(today = "2019-12-30", episodes, expecting("2020-01-01"), foundRelease = true) shouldBe
            LocalDate.parse("2020-01-08")
    }

    @Test
    fun `keeps the schedule after a late release`() = runTest {
        val episodes = episodesOn(weekly(from = "2019-12-04", to = "2019-12-25") + LocalDate.parse("2020-01-03"))

        nextUpdate(today = "2020-01-03", episodes, expecting("2020-01-01"), foundRelease = true) shouldBe
            LocalDate.parse("2020-01-08")
    }

    @Test
    fun `keeps expecting the release after an extra one well before it`() = runTest {
        val episodes = episodesOn(weekly(from = "2019-10-02", to = "2020-01-01") + LocalDate.parse("2020-01-02"))

        nextUpdate(today = "2020-01-02", episodes, expecting("2020-01-08"), foundRelease = true) shouldBe
            LocalDate.parse("2020-01-08")
    }

    @Test
    fun `follows the schedule again when releases resume after a hiatus`() = runTest {
        val episodes = episodesOn(weekly(from = "2019-09-11", to = "2019-10-02") + LocalDate.parse("2020-01-01"))

        nextUpdate(today = "2020-01-01", episodes, expecting("2020-01-22"), foundRelease = true) shouldBe
            LocalDate.parse("2020-01-08")
    }

    @Test
    fun `follows the schedule once releases keep coming early`() = runTest {
        val history = every(days = 5, from = "2019-10-01", to = "2019-12-15")
        val episodes = episodesOn(history + every(days = 5, from = "2019-12-19", to = "2019-12-29"))

        nextUpdate(today = "2019-12-29", episodes, expecting("2019-12-30"), foundRelease = true) shouldBe
            LocalDate.parse("2020-01-03")
    }

    @Test
    fun `keeps checking for an overdue release while recent ones came that late`() = runTest {
        val episodes = episodesOn(afterGaps("2019-10-18", 6, 5, 8, 6, 5, 6, 8, 6, 5))

        // Due on the 18th from a median gap of 6, and gaps have run to 8
        nextUpdate(today = "2019-12-20", episodes, expecting("2019-12-18")) shouldBe LocalDate.parse("2019-12-20")
    }

    @Test
    fun `counts from an early release with a custom interval`() = runTest {
        val anime = expecting("2020-01-01").copy(fetchInterval = -7)

        nextUpdate(today = "2019-12-31", emptyList(), anime, foundRelease = true) shouldBe
            LocalDate.parse("2020-01-07")
    }

    @Test
    fun `spreads out checks during a hiatus`() = runTest {
        val episodes = episodesOn(weekly(from = "2019-09-11", to = "2019-10-02"))

        nextUpdate(today = "2020-01-01", episodes) shouldBe LocalDate.parse("2020-01-22")
    }

    @Test
    fun `ignores episodes dated in the future`() = runTest {
        val episodes = episodesOn(weekly(from = "2019-12-04", to = "2019-12-25") + LocalDate.parse("2020-06-01"))

        nextUpdate(today = "2020-01-01", episodes) shouldBe LocalDate.parse("2020-01-01")
    }

    private suspend fun nextUpdate(
        today: String,
        episodes: List<Episode>,
        anime: Anime = Anime.create(),
        foundRelease: Boolean = false,
    ): LocalDate {
        val getEpisodesByAnimeId = mockk<GetEpisodesByAnimeId>()
        coEvery { getEpisodesByAnimeId.await(any(), any()) } returns episodes
        val fetchInterval = FetchInterval(getEpisodesByAnimeId)
        val date = LocalDate.parse(today)
        val update = fetchInterval.withFetchInterval(
            anime = anime,
            dateTime = date.atTime(10, 0),
            timeZone = testTimeZone,
            window = fetchInterval.getWindow(date, testTimeZone),
            foundRelease = foundRelease,
        )
        return update.nextUpdate!!.toLocalDateTime(testTimeZone).date
    }

    private fun expecting(day: String): Anime {
        return Anime.create().copy(nextUpdate = LocalDate.parse(day).atStartOfDayIn(testTimeZone))
    }

    private fun interval(episodes: List<Episode>): Int {
        return ReleaseSchedule.estimate(
            episodes,
            testTimeZone,
            Clock.System.now().toLocalDateTime(testTimeZone).date,
        ).interval
    }

    private fun weekly(from: String, to: String) = every(days = 7, from, to)

    private fun every(days: Int, from: String, to: String): List<LocalDate> {
        return generateSequence(LocalDate.parse(from)) { it.plus(DatePeriod(days = days)) }
            .takeWhile { it <= LocalDate.parse(to) }
            .toList()
    }

    private fun afterGaps(from: String, vararg gaps: Int): List<LocalDate> {
        return gaps.runningFold(LocalDate.parse(from)) { day, gap -> day.plus(DatePeriod(days = gap)) }
    }

    private fun episodesOn(days: List<LocalDate>, perDay: Int = 1): List<Episode> {
        return days.flatMap { day ->
            val time = day.atStartOfDayIn(testTimeZone) + 12.hours
            List(perDay) { episode.copy(dateFetch = time, dateUpload = time) }
        }
    }

    private fun episodeWithTime(episode: Episode, duration: Duration): Episode {
        val newTime = testTime.toInstant(testTimeZone) + duration
        return episode.copy(dateFetch = newTime, dateUpload = newTime)
    }

    private fun List<Episode>.lastUploadDate() =
        last().dateUpload!! - testTime.toInstant(testTimeZone)
}
