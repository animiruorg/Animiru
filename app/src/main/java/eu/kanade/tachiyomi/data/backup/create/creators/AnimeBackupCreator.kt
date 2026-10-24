package eu.kanade.tachiyomi.data.backup.create.creators

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.models.BackupAnime
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.toBackupEpisode
import eu.kanade.tachiyomi.data.backup.models.toBackupTracking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import mihon.core.common.extensions.toByteArray
import mihon.core.common.extensions.toEpochMillisOrZero
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.repository.AnimeRepository
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.episode.repository.EpisodeRepository
import tachiyomi.domain.history.repository.HistoryRepository
import tachiyomi.domain.track.repository.TrackRepository

@Inject
class AnimeBackupCreator(
    private val animeRepository: AnimeRepository,
    private val episodeRepository: EpisodeRepository,
    private val categoryRepository: CategoryRepository,
    private val historyRepository: HistoryRepository,
    private val trackRepository: TrackRepository,
) {

    /**
     * Builds the entries a chunk at a time, so only one chunk's episodes are held at once.
     */
    operator fun invoke(animes: List<Anime>, options: BackupOptions): Flow<BackupAnime> = flow {
        animes.chunked(CHUNK_SIZE).forEach { chunk ->
            backupAnimes(chunk, options).forEach { emit(it) }
        }
    }

    private suspend fun backupAnimes(animes: List<Anime>, options: BackupOptions): List<BackupAnime> {
        val animeIds = animes.map { it.id }

        val excludedScanlatorsByAnimeId = animeRepository.getExcludedScanlatorsByAnimeIds(animeIds)
        // History is backed up by episode URL, so it needs the episodes too
        val episodesByAnimeId = if (options.episodes || options.history) {
            episodeRepository.getEpisodesByAnimeIds(animeIds)
        } else {
            emptyMap()
        }
        val categoriesByAnimeId = if (options.categories) {
            categoryRepository.getCategoriesByAnimeIds(animeIds)
        } else {
            emptyMap()
        }
        val tracksByAnimeId = if (options.tracking) trackRepository.getTracksByAnimeIds(animeIds) else emptyMap()
        val historyByAnimeId = if (options.history) historyRepository.getHistoryByAnimeIds(animeIds) else emptyMap()

        return animes.map { anime ->
            val animeObject = anime.toBackupAnime()
            val episodes = episodesByAnimeId[anime.id].orEmpty()

            animeObject.excludedScanlators = excludedScanlatorsByAnimeId[anime.id].orEmpty()

            if (options.episodes) {
                animeObject.episodes = episodes.map { it.toBackupEpisode() }
            }

            if (options.categories) {
                animeObject.categories = categoriesByAnimeId[anime.id].orEmpty().map { it.order }
            }

            if (options.tracking) {
                animeObject.tracking = tracksByAnimeId[anime.id].orEmpty().map { it.toBackupTracking() }
            }

            if (options.history) {
                val episodeUrlsById = episodes.associate { it.id to it.url }
                animeObject.history = historyByAnimeId[anime.id].orEmpty().mapNotNull { history ->
                    // An episode removed since its history was seen takes that history with it
                    val url = episodeUrlsById[history.episodeId] ?: return@mapNotNull null
                    BackupHistory(url, history.seenAt.toEpochMillisOrZero())
                }
            }

            animeObject
        }
    }

    private companion object {
        const val CHUNK_SIZE = 100
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
        dateAdded = this.favoriteAt.toEpochMillisOrZero(),
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
        nextUpdate = this.nextUpdate.toEpochMillisOrZero(),
        // AM -->
        customArtist = this.customArtist,
        customAuthor = this.customAuthor,
        customDescription = this.customDescription,
        customGenre = this.customGenre,
        customTitle = this.customTitle,
        customStatus = this.customStatus?.toInt() ?: 0,
        // <-- AM
    )
