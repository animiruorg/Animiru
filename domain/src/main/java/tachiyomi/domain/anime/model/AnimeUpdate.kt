package tachiyomi.domain.anime.model

data class AnimeUpdate(
    val id: Long,
    val favorite: Boolean? = null,
    val lastUpdate: Long? = null,
    val nextUpdate: Long? = null,
    val fetchInterval: Int? = null,
    val dateAdded: Long? = null,
    val viewerFlags: Long? = null,
    val episodeFlags: Long? = null,
    val coverLastModified: Long? = null,
    // AY -->
    val backgroundLastModified: Long? = null,
    // <-- AY
    val notes: String? = null,
    // AY -->
    val seasonFlags: Long? = null,
    // <-- AY
)

fun Anime.toAnimeUpdate(): AnimeUpdate {
    return AnimeUpdate(
        id = id,
        favorite = favorite,
        lastUpdate = lastUpdate,
        nextUpdate = nextUpdate,
        fetchInterval = fetchInterval,
        dateAdded = dateAdded,
        viewerFlags = viewerFlags,
        episodeFlags = episodeFlags,
        coverLastModified = coverLastModified,
        // AY -->
        backgroundLastModified = backgroundLastModified,
        // <-- AY
        notes = notes,
        // AY -->
        seasonFlags = seasonFlags,
        // <-- AY
    )
}
