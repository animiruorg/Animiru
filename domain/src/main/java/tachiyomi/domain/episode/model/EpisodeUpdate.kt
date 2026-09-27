package tachiyomi.domain.episode.model

import kotlinx.serialization.json.JsonObject

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
    val sourceOrder: Long? = null,
    val url: String? = null,
    val name: String? = null,
    val dateUpload: Long? = null,
    val episodeNumber: Double? = null,
    val scanlator: String? = null,
    // AY -->
    val summary: String? = null,
    val previewUrl: String? = null,
    // <-- AY
    val version: Long? = null,
    val memo: JsonObject? = null,
    // AM -->
    val thumbnailLastModifiedAt: Long? = null,
    // <-- AM
)

fun Episode.toEpisodeUpdate(): EpisodeUpdate {
    return EpisodeUpdate(
        id,
        animeId,
        seen,
        bookmark,
        // AY -->
        fillermark,
        // <-- AY
        lastSecondSeen,
        // AY -->
        totalSeconds,
        // <-- AY
        dateFetch,
        sourceOrder,
        url,
        name,
        dateUpload,
        episodeNumber,
        scanlator,
        // AY -->
        summary,
        previewUrl,
        // <-- AY
        version,
        memo,
        // AM -->
        thumbnailLastModifiedAt,
        // <-- AM
    )
}
