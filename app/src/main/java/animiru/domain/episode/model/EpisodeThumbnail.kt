package animiru.domain.episode.model

import kotlin.time.Instant

/**
 * Contains the required data for EpisodeThumbnailFetcher
 */
data class EpisodeThumbnail(
    val animeId: Long,
    val sourceId: Long,
    val isLibraryAnime: Boolean,
    val url: String?,
    val lastModified: Instant?,
)
