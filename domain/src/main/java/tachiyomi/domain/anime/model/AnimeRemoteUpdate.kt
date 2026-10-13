package tachiyomi.domain.anime.model

import eu.kanade.tachiyomi.animesource.model.AnimeUpdateStrategy
import kotlinx.serialization.json.JsonObject

data class AnimeRemoteUpdate(
    val id: Long,
    val title: String?,
    val author: String?,
    val artist: String?,
    val description: String?,
    val genre: List<String>?,
    val status: Long,
    val thumbnailUrl: String?,
    val updateStrategy: AnimeUpdateStrategy,
    val memo: JsonObject,
    val initialized: Boolean,
    val coverLastModified: Long?,
    // AM -->
    val backgroundUrl: String?,
    val backgroundLastModified: Long?,
    val parentId: Long?,
    val seasonNumber: Double?,
    val seasonSourceOrder: Long?,
    // <-- AM
)
