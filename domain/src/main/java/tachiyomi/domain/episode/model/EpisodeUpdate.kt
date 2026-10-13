package tachiyomi.domain.episode.model

data class EpisodeUpdate(
    val id: Long,
    val animeId: Long? = null,
    val seen: Boolean? = null,
    val bookmark: Boolean? = null,
    // AY -->
    val fillermark: Boolean? = null,
    // <-- AY
    val lastSecondSeen: Long? = null,
    // AY -->
    val totalSeconds: Long? = null,
    // <-- AY
    val dateFetch: Long? = null,
    // AM -->
    val thumbnailLastModifiedAt: Long? = null,
    // <-- AM
)

fun Episode.toEpisodeUpdate(): EpisodeUpdate {
    return EpisodeUpdate(
        id = id,
        animeId = animeId,
        seen = seen,
        bookmark = bookmark,
        // AY -->
        fillermark = fillermark,
        // <-- AY
        lastSecondSeen = lastSecondSeen,
        // AY -->
        totalSeconds = totalSeconds,
        // <-- AY
        dateFetch = dateFetch,
        // AM -->
        thumbnailLastModifiedAt = thumbnailLastModifiedAt,
        // <-- AM
    )
}
