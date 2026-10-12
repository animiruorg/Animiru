package tachiyomi.domain.anime.model

import eu.kanade.tachiyomi.animesource.model.AnimeUpdateStrategy
import eu.kanade.tachiyomi.animesource.model.FetchType
import kotlinx.serialization.json.JsonObject

data class AnimeUpdate(
    val id: Long,
    val source: Long? = null,
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
    val url: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val author: String? = null,
    val description: String? = null,
    val genre: List<String>? = null,
    val status: Long? = null,
    val thumbnailUrl: String? = null,
    // AY -->
    val backgroundUrl: String? = null,
    // <-- AY
    val updateStrategy: AnimeUpdateStrategy? = null,
    val initialized: Boolean? = null,
    val notes: String? = null,
    // AY -->
    val fetchType: FetchType? = null,
    val parentId: Long? = null,
    val seasonFlags: Long? = null,
    val seasonNumber: Double? = null,
    val seasonSourceOrder: Long? = null,
    // <-- AY
    val memo: JsonObject? = null,
)

fun Anime.toAnimeUpdate(): AnimeUpdate {
    return AnimeUpdate(
        id = id,
        source = source,
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
        url = url,
        title = title,
        artist = artist,
        author = author,
        description = description,
        genre = genre,
        status = status,
        thumbnailUrl = thumbnailUrl,
        // AY -->
        backgroundUrl = backgroundUrl,
        // <-- AY
        updateStrategy = updateStrategy,
        initialized = initialized,
        notes = notes,
        // AY -->
        fetchType = fetchType,
        parentId = parentId,
        seasonFlags = seasonFlags,
        seasonNumber = seasonNumber,
        seasonSourceOrder = seasonSourceOrder,
        // <-- AY
        memo = memo,
    )
}
