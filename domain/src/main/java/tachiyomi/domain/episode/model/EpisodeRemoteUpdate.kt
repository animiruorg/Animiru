package tachiyomi.domain.episode.model

import kotlinx.serialization.json.JsonObject
import kotlin.time.Instant

data class EpisodeRemoteUpdate(
    val id: Long,
    val name: String,
    val scanlator: String?,
    val episodeNumber: Double,
    val dateUpload: Instant?,
    val sourceOrder: Long,
    val memo: JsonObject,
    // AM -->
    val fillerMark: Boolean?,
    val summary: String?,
    val previewUrl: String?,
    val thumbnailLastModified: Instant?,
    // <-- AM
)
