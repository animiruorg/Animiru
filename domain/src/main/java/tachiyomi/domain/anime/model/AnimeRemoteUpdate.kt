package tachiyomi.domain.anime.model

import eu.kanade.tachiyomi.animesource.model.AnimeUpdateStrategy
import kotlinx.serialization.json.JsonObject
import kotlin.time.Instant

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
    val coverLastModified: Instant?,
    // AM -->
    val backgroundUrl: String?,
    val backgroundLastModified: Instant?,
    val parentId: Long?,
    val seasonNumber: Double?,
    val seasonSourceOrder: Long?,
    // <-- AM
)
