package eu.kanade.tachiyomi.data.backup.create.creators

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.models.BackupAnime
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.toBackupEpisode
import eu.kanade.tachiyomi.data.backup.models.toBackupTracking
import mihon.core.common.extensions.toByteArray
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.repository.AnimeRepository
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.episode.repository.EpisodeRepository
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.track.repository.TrackRepository

@Inject
class AnimeBackupCreator(
    private val animeRepository: AnimeRepository,
    private val episodeRepository: EpisodeRepository,
    private val trackRepository: TrackRepository,
    private val getCategories: GetCategories,
    private val getHistory: GetHistory,
) {

    suspend operator fun invoke(animes: List<Anime>, options: BackupOptions): List<BackupAnime> {
        return animes.map {
            backupAnime(it, options)
        }
    }

    private suspend fun backupAnime(anime: Anime, options: BackupOptions): BackupAnime {
        // Entry for this anime
        val animeObject = anime.toBackupAnime()

        animeObject.excludedScanlators = animeRepository.getExcludedScanlators(anime.id).toList()

        if (options.episodes) {
            // Backup all the episodes
            episodeRepository.getEpisodeByAnimeId(anime.id, applyScanlatorFilter = false)
                .map { it.toBackupEpisode() }
                .takeUnless { it.isEmpty() }
                ?.let { animeObject.episodes = it }
        }

        if (options.categories) {
            // Backup categories for this anime
            val categoriesForAnime = getCategories.await(anime.id)
            if (categoriesForAnime.isNotEmpty()) {
                animeObject.categories = categoriesForAnime.map { it.order }
            }
        }

        if (options.tracking) {
            val tracks = trackRepository.getTracksByAnimeId(anime.id).map { it.toBackupTracking() }
            if (tracks.isNotEmpty()) {
                animeObject.tracking = tracks
            }
        }

        if (options.history) {
            val historyByAnimeId = getHistory.await(anime.id)
            if (historyByAnimeId.isNotEmpty()) {
                val history = historyByAnimeId.map { history ->
                    val episode = checkNotNull(episodeRepository.getEpisodeById(history.episodeId))
                    BackupHistory(episode.url, history.seenAt?.time ?: 0L)
                }
                if (history.isNotEmpty()) {
                    animeObject.history = history
                }
            }
        }

        return animeObject
    }
}

private fun Anime.toBackupAnime() =
    BackupAnime(
        url = this.url,
        // AM (CUSTOM_INFORMATION) -->
        title = this.ogTitle,
        artist = this.ogArtist,
        author = this.ogAuthor,
        description = this.ogDescription,
        genre = this.ogGenre.orEmpty(),
        status = this.ogStatus.toInt(),
        // <-- AM (CUSTOM_INFORMATION)
        thumbnailUrl = this.thumbnailUrl,
        favorite = this.favorite,
        source = this.source,
        dateAdded = this.favoriteAt ?: 0L,
        viewer_flags = this.viewerFlags.toInt(),
        episodeFlags = this.episodeFlags.toInt(),
        updateStrategy = this.updateStrategy,
        notes = this.notes,
        initialized = this.initialized,
        // AY -->
        fetchType = this.fetchType,
        parentId = this.parentId,
        id = this.id,
        seasonFlags = this.seasonFlags,
        seasonNumber = this.seasonNumber,
        seasonSourceOrder = this.seasonSourceOrder,
        // <-- AY
        memo = this.memo.toByteArray(),
        // AM -->
        customArtist = this.customArtist,
        customAuthor = this.customAuthor,
        customDescription = this.customDescription,
        customGenre = this.customGenre,
        customTitle = this.customTitle,
        customStatus = this.customStatus?.toInt() ?: 0,
        // <-- AM
    )
