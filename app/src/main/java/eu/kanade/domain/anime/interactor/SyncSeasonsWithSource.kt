// AY -->
package eu.kanade.domain.anime.interactor

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.SAnime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import mihon.domain.anime.model.toDomainAnime
import tachiyomi.domain.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.model.AnimeRemoteUpdate
import tachiyomi.domain.anime.model.NoSeasonsException
import tachiyomi.domain.anime.repository.AnimeRepository
import tachiyomi.domain.season.interactor.ShouldUpdateDbSeason
import tachiyomi.domain.season.service.SeasonRecognition
import tachiyomi.source.local.isLocal
import kotlin.time.Clock

@Inject
class SyncSeasonsWithSource(
    private val updateAnime: UpdateAnime,
    private val animeRepository: AnimeRepository,
    private val networkToLocalAnime: NetworkToLocalAnime,
    private val shouldUpdateDbSeason: ShouldUpdateDbSeason,
) {
    suspend fun await(
        rawSourceSeasons: List<SAnime>,
        anime: Anime,
        source: AnimeSource,
        manualFetch: Boolean = false,
        fetchWindow: Pair<Long, Long> = Pair(0, 0),
    ): List<Anime> {
        if (rawSourceSeasons.isEmpty() && !source.isLocal()) {
            throw NoSeasonsException()
        }

        val timeZone = TimeZone.currentSystemDefault()
        val now = Clock.System.now().toLocalDateTime(timeZone)
        val nowMillis = now.toInstant(timeZone).toEpochMilliseconds()

        val sourceSeasons = rawSourceSeasons
            .distinctBy { it.url }
            .mapIndexed { i, sAnime ->
                networkToLocalAnime.invoke(
                    sAnime.toDomainAnime(source.id)
                        .copy(parentId = anime.id, seasonSourceOrder = i.toLong()),
                )
                    .copy(seasonSourceOrder = i.toLong())
            }

        val dbSeasons = animeRepository.getAnimeSeasonsById(anime.id)
        val dbSeasonsByUrl = dbSeasons.associateBy { it.anime.url }

        val newSeasons = mutableListOf<Anime>()
        val updatedSeasons = mutableListOf<AnimeRemoteUpdate>()
        val sourceUrls = mutableSetOf<String>()

        for (sourceSeason in sourceSeasons) {
            var season = sourceSeason

            if (!sourceUrls.add(season.url)) continue

            // Recognize season number for the season
            val seasonNumber = SeasonRecognition.parseSeasonNumber(
                anime.title,
                season.title,
                season.seasonNumber,
            )
            season = season.copy(seasonNumber = seasonNumber)

            val dbSeason = dbSeasonsByUrl[season.url]?.anime
            if (dbSeason == null) {
                newSeasons.add(season)
            } else {
                if (shouldUpdateDbSeason.await(dbSeason, season)) {
                    updatedSeasons.add(
                        AnimeRemoteUpdate(
                            id = dbSeason.id,
                            title = season.title,
                            author = season.author,
                            artist = season.artist,
                            description = season.description,
                            genre = season.genre,
                            status = season.status,
                            thumbnailUrl = season.thumbnailUrl,
                            updateStrategy = season.updateStrategy,
                            memo = season.memo,
                            initialized = season.initialized,
                            coverLastModified = season.coverLastModified,
                            backgroundUrl = season.backgroundUrl,
                            backgroundLastModified = season.backgroundLastModified,
                            parentId = season.parentId,
                            seasonNumber = season.seasonNumber,
                            seasonSourceOrder = season.seasonSourceOrder,
                        ),
                    )
                }
            }
        }

        val removedSeasons = dbSeasons.filterNot { it.anime.url in sourceUrls }

        // Return if there's nothing to add, delete, or update to avoid unnecessary db transactions.
        if (newSeasons.isEmpty() && removedSeasons.isEmpty() && updatedSeasons.isEmpty()) {
            if (manualFetch || anime.fetchInterval == 0 || anime.nextUpdate < fetchWindow.first) {
                updateAnime.awaitUpdateFetchInterval(
                    anime,
                    timeZone,
                    now,
                    fetchWindow,
                )
            }
            return sourceSeasons
        }

        val deletedSeasonNumbers = removedSeasons.map { it.anime.seasonNumber }
        val added = animeRepository.updateFromRemote(
            removedIds = removedSeasons.map { it.id },
            added = newSeasons,
            updated = updatedSeasons,
        )
        updateAnime.awaitUpdateLastUpdate(anime.id)

        return added
    }
}
// <-- AY
