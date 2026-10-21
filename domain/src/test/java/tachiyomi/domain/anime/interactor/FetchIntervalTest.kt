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

    private val fetchInterval = FetchInterval(mockk())

    @Test
    fun `returns default interval of 7 days when not enough distinct days`() {
        val episodesWithUploadDate = (1..50).map {
            episodeWithTime(episode, 1.days)
        }
        fetchInterval.calculateInterval(episodesWithUploadDate, testTimeZone) shouldBe 7

        val episodesWithoutUploadDate = episodesWithUploadDate.map {
            it.copy(dateUpload = null)
        }
        fetchInterval.calculateInterval(episodesWithoutUploadDate, testTimeZone) shouldBe 7
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

        fetchInterval.calculateInterval(episodes, testTimeZone) shouldBe 1
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

        fetchInterval.calculateInterval(episodes, testTimeZone) shouldBe 7
    }

    @Test
    fun `returns interval of 7 days when multiple episodes in 1 day`() {
        val episodes = (1..10).map {
            episodeWithTime(episode, 10.hours)
        }
        fetchInterval.calculateInterval(episodes, testTimeZone) shouldBe 7
    }

    @Test
    fun `returns interval of 7 days when multiple episodes in 2 days`() {
        val episodes = (1..2).map {
            episodeWithTime(episode, 1.days)
        } + (1..5).map {
            episodeWithTime(episode, 2.days)
        }
        fetchInterval.calculateInterval(episodes, testTimeZone) shouldBe 7
    }

    @Test
    fun `returns interval of 1 day when episodes are released every 1 day`() {
        val episodes = (1..20).map {
            episodeWithTime(episode, it.days)
        }
        fetchInterval.calculateInterval(episodes, testTimeZone) shouldBe 1
    }

    @Test
    fun `returns interval of 1 day when delta is less than 1 day`() {
        val episodes = (1..20).map {
            episodeWithTime(episode, (15 * it).hours)
        }
        fetchInterval.calculateInterval(episodes, testTimeZone) shouldBe 1
    }

    @Test
    fun `returns interval of 2 days when episodes are released every 2 days`() {
        val episodes = (1..20).map {
            episodeWithTime(episode, (2 * it).days)
        }
        fetchInterval.calculateInterval(episodes, testTimeZone) shouldBe 2
    }

    @Test
    fun `returns interval with floored value when interval is decimal`() {
        val episodesWithUploadDate = (1..5).map {
            episodeWithTime(episode, (25 * it).hours)
        }
        fetchInterval.calculateInterval(episodesWithUploadDate, testTimeZone) shouldBe 1

        val episodesWithoutUploadDate = episodesWithUploadDate.map {
            it.copy(dateUpload = null)
        }
        fetchInterval.calculateInterval(episodesWithoutUploadDate, testTimeZone) shouldBe 1
    }

    @Test
    fun `returns interval of 2 days when episodes are released just below every 2 days`() {
        val episodes = (1..20).map {
            episodeWithTime(episode, (43 * it).hours)
        }
        fetchInterval.calculateInterval(episodes, testTimeZone) shouldBe 2
    }

    @Test
    fun `follows releases on a few fixed weekdays`() = runTest {
        val mondaysAndThursdays = generateSequence(LocalDate.parse("2019-12-02")) { it.plus(DatePeriod(days = 1)) }
            .takeWhile { it <= LocalDate.parse("2020-01-20") }
            .filter { it.dayOfWeek == DayOfWeek.MONDAY || it.dayOfWeek == DayOfWeek.THURSDAY }
            .toList()
        val episodes = episodesOn(mondaysAndThursdays)

        fetchInterval.estimateSchedule(episodes, testTimeZone, LocalDate.parse("2020-01-21")).pattern shouldBe
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
        )
        return update.nextUpdate!!.toLocalDateTime(testTimeZone).date
    }

    private fun weekly(from: String, to: String): List<LocalDate> {
        return generateSequence(LocalDate.parse(from)) { it.plus(DatePeriod(days = 7)) }
            .takeWhile { it <= LocalDate.parse(to) }
            .toList()
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
