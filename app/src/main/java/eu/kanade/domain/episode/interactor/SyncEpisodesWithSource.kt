package eu.kanade.domain.episode.interactor

import animiru.core.cache.ThumbnailCache
import dev.zacsweers.metro.Inject
import eu.kanade.domain.anime.interactor.GetExcludedScanlators
import eu.kanade.domain.anime.interactor.UpdateAnime
import eu.kanade.domain.anime.model.toSAnime
import eu.kanade.domain.episode.model.copyFromSEpisode
import eu.kanade.domain.episode.model.toSEpisode
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.episode.interactor.ShouldUpdateDbEpisode
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.episode.model.EpisodeRemoteUpdate
import tachiyomi.domain.episode.model.NoEpisodesException
import tachiyomi.domain.episode.repository.EpisodeRepository
import tachiyomi.domain.episode.service.EpisodeRecognition
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.source.local.isLocal
import java.lang.Long.max
import java.util.TreeSet
import kotlin.time.Clock

@Inject
class SyncEpisodesWithSource(
    private val downloadManager: DownloadManager,
    private val downloadProvider: DownloadProvider,
    private val episodeRepository: EpisodeRepository,
    private val shouldUpdateDbEpisode: ShouldUpdateDbEpisode,
    private val updateAnime: UpdateAnime,
    private val getExcludedScanlators: GetExcludedScanlators,
    private val libraryPreferences: LibraryPreferences,
    // AM -->
    private val thumbnailCache: ThumbnailCache,
    // <-- AM
) {

    /**
     * Method to synchronize db episodes with source ones
     *
     * @param sourceEpisodes the episodes from the source.
     * @param anime the anime the episodes belong to.
     * @param source the source the anime belongs to.
     * @return Newly added episodes
     */
    suspend fun await(
        sourceEpisodes: List<Episode>,
        anime: Anime,
        source: AnimeSource,
        manualFetch: Boolean = false,
        fetchWindow: Pair<Long, Long> = Pair(0, 0),
    ): List<Episode> {
        if (sourceEpisodes.isEmpty() && !source.isLocal()) {
            throw NoEpisodesException()
        }

        val timeZone = TimeZone.currentSystemDefault()
        val now = Clock.System.now().toLocalDateTime(timeZone)
        val nowMillis = now.toInstant(timeZone).toEpochMilliseconds()

        val dbEpisodes = episodeRepository.getEpisodeByAnimeId(anime.id)
        val dbEpisodesByUrl = dbEpisodes.associateBy { it.url }

        val newEpisodes = mutableListOf<Episode>()
        val updatedEpisodes = mutableListOf<EpisodeRemoteUpdate>()
        val sourceUrls = mutableSetOf<String>()

        // Used to not set upload date of older episodes
        // to a higher value than newer episodes
        var maxSeenUploadDate = 0L

        for (sourceEpisode in sourceEpisodes) {
            var episode = sourceEpisode

            // Update metadata from source if necessary.
            if (source is AnimeHttpSource) {
                val sEpisode = episode.toSEpisode()
                @Suppress("DEPRECATION")
                source.prepareNewEpisode(sEpisode, anime.toSAnime())
                episode = episode.copyFromSEpisode(sEpisode)
            }

            if (!sourceUrls.add(episode.url)) continue

            // Recognize episode number for the episode.
            val episodeNumber = EpisodeRecognition.parseEpisodeNumber(anime.title, episode.name, episode.episodeNumber)
            episode = episode.copy(episodeNumber = episodeNumber)

            val dbEpisode = dbEpisodesByUrl[episode.url]

            if (dbEpisode == null) {
                val toAddEpisode = if (episode.dateUpload == 0L) {
                    val altDateUpload = if (maxSeenUploadDate == 0L) nowMillis else maxSeenUploadDate
                    episode.copy(dateUpload = altDateUpload)
                } else {
                    maxSeenUploadDate = max(maxSeenUploadDate, sourceEpisode.dateUpload)
                    episode
                }
                newEpisodes.add(toAddEpisode)
            } else {
                if (shouldUpdateDbEpisode.await(dbEpisode, episode)) {
                    val shouldRenameEpisode = downloadProvider.isEpisodeDirNameChanged(dbEpisode, episode) &&
                        downloadManager.isEpisodeDownloaded(
                            dbEpisode.name,
                            dbEpisode.scanlator,
                            dbEpisode.url,
                            // AM (CUSTOM_INFORMATION) -->
                            anime.ogTitle,
                            // <-- AM (CUSTOM_INFORMATION)
                            anime.source,
                        )

                    if (shouldRenameEpisode) {
                        downloadManager.renameEpisode(source, anime, dbEpisode, episode)
                    }

                    updatedEpisodes.add(
                        EpisodeRemoteUpdate(
                            id = dbEpisode.id,
                            name = episode.name,
                            episodeNumber = episode.episodeNumber,
                            scanlator = episode.scanlator,
                            sourceOrder = episode.sourceOrder,
                            dateUpload = episode.dateUpload.takeIf { it != 0L },
                            memo = episode.memo,
                            // AM -->
                            fillerMark = if (episode.fillermark) episode.fillermark else sourceEpisode.fillermark,
                            summary = episode.summary,
                            previewUrl = episode.previewUrl.takeUnless { it.isNullOrBlank() },
                            thumbnailLastModified = episode.thumbnailLastModifiedAt,
                            // <-- AM
                        ),
                    )
                }
            }
        }

        val removedEpisodes = dbEpisodes.filterNot { it.url in sourceUrls }

        // Return if there's nothing to add, delete, or update to avoid unnecessary db transactions.
        if (newEpisodes.isEmpty() && removedEpisodes.isEmpty() && updatedEpisodes.isEmpty()) {
            if (manualFetch || anime.fetchInterval == 0 || anime.nextUpdate < fetchWindow.first) {
                updateAnime.awaitUpdateFetchInterval(
                    anime,
                    timeZone,
                    now,
                    fetchWindow,
                )
            }
            return emptyList()
        }

        val changedOrDuplicateSeenUrls = mutableSetOf<String>()

        val deletedEpisodeNumbers = TreeSet<Double>()
        val deletedSeenEpisodeNumbers = TreeSet<Double>()
        val deletedBookmarkedEpisodeNumbers = TreeSet<Double>()

        val seenEpisodeNumbers = dbEpisodes
            .asSequence()
            .filter { it.seen && it.isRecognizedNumber }
            .map { it.episodeNumber }
            .toSet()

        removedEpisodes.forEach { episode ->
            if (episode.seen) deletedSeenEpisodeNumbers.add(episode.episodeNumber)
            if (episode.bookmark) deletedBookmarkedEpisodeNumbers.add(episode.episodeNumber)
            deletedEpisodeNumbers.add(episode.episodeNumber)
        }

        val deletedEpisodeNumberDateFetchMap = removedEpisodes.sortedByDescending { it.dateFetch }
            .associate { it.episodeNumber to it.dateFetch }

        val markDuplicateAsSeen = libraryPreferences.markDuplicateSeenEpisodeAsSeen.get()
            .contains(LibraryPreferences.MARK_DUPLICATE_EPISODE_SEEN_NEW)

        // Date fetch is set in such a way that the upper ones will have bigger value than the lower ones
        // Sources MUST return the episodes from most to less recent, which is common.
        var itemCount = newEpisodes.size
        val toAdd = newEpisodes.map { toAddItem ->
            var episode = toAddItem.copy(dateFetch = nowMillis + itemCount--)

            if (episode.episodeNumber in seenEpisodeNumbers && markDuplicateAsSeen) {
                changedOrDuplicateSeenUrls.add(episode.url)
                episode = episode.copy(seen = true)
            }

            if (!episode.isRecognizedNumber || episode.episodeNumber !in deletedEpisodeNumbers) return@map episode

            episode = episode.copy(
                seen = episode.episodeNumber in deletedSeenEpisodeNumbers,
                bookmark = episode.episodeNumber in deletedBookmarkedEpisodeNumbers,
            )

            // Try to to use the fetch date of the original entry to not pollute 'Updates' tab
            deletedEpisodeNumberDateFetchMap[episode.episodeNumber]?.let {
                episode = episode.copy(dateFetch = it)
            }

            changedOrDuplicateSeenUrls.add(episode.url)

            episode
        }

        val added = episodeRepository.updateFromRemote(
            removedIds = removedEpisodes.map { it.id },
            added = toAdd,
            updated = updatedEpisodes,
        )
        updateAnime.awaitUpdateFetchInterval(anime, timeZone, now, fetchWindow)

        // Set this anime as updated since episodes were changed
        // Note that last_update actually represents last time the episode list changed at all
        updateAnime.awaitUpdateLastUpdate(anime.id)

        val excludedScanlators = getExcludedScanlators.await(anime.id).toHashSet()

        return added.filterNot { it.url in changedOrDuplicateSeenUrls || it.scanlator in excludedScanlators }
    }
}
