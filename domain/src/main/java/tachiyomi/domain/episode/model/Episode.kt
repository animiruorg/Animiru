package tachiyomi.domain.episode.model

import kotlinx.serialization.json.JsonObject
import mihon.core.common.extensions.EMPTY
import kotlin.time.Instant

data class Episode(
    val id: Long,
    val animeId: Long,
    val seen: Boolean,
    val bookmark: Boolean,
    // AY -->
    val fillermark: Boolean,
    // <-- AY
    val lastSecondSeen: Long,
    // AY -->
    val totalSeconds: Long,
    // <-- AY
    val dateFetch: Instant,
    val sourceOrder: Long,
    val url: String,
    val name: String,
    val dateUpload: Instant?,
    val episodeNumber: Double,
    val scanlator: String?,
    // AY -->
    val summary: String?,
    val previewUrl: String?,
    // <-- AY
    val memo: JsonObject,
    // AM -->
    val thumbnailLastModifiedAt: Instant?,
    // <-- AM
) {
    val isRecognizedNumber: Boolean
        get() = episodeNumber >= 0f

    fun copyFrom(other: Episode): Episode {
        return copy(
            name = other.name,
            url = other.url,
            dateUpload = other.dateUpload,
            episodeNumber = other.episodeNumber,
            // AY -->
            fillermark = other.fillermark,
            // <-- AY
            scanlator = other.scanlator?.ifBlank { null },
            // AY -->
            summary = other.summary?.ifBlank { null },
            previewUrl = other.previewUrl?.ifBlank { null },
            // <-- AY
        )
    }

    companion object {
        fun create() = Episode(
            id = -1,
            animeId = -1,
            seen = false,
            bookmark = false,
            // AY -->
            fillermark = false,
            // <-- AY
            lastSecondSeen = 0,
            // AY -->
            totalSeconds = 0,
            // <-- AY
            dateFetch = Instant.fromEpochMilliseconds(0),
            sourceOrder = 0,
            url = "",
            name = "",
            dateUpload = null,
            episodeNumber = -1.0,
            scanlator = null,
            // AY -->
            summary = null,
            previewUrl = null,
            // <-- AY
            memo = JsonObject.EMPTY,
            // AM -->
            thumbnailLastModifiedAt = null,
            // <-- AM
        )
    }
}
