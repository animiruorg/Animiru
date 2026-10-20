package tachiyomi.domain.anime.model

import kotlin.time.Instant

/**
 * Contains the required data for AnimeCoverFetcher
 */
data class AnimeCover(
    val animeId: Long,
    val sourceId: Long,
    val isAnimeFavorite: Boolean,
    val url: String?,
    val lastModified: Instant?,
)

fun Anime.asAnimeCover(): AnimeCover {
    return AnimeCover(
        animeId = id,
        sourceId = source,
        isAnimeFavorite = favorite,
        url = thumbnailUrl,
        lastModified = coverLastModified,
    )
}
