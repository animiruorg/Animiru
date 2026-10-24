package tachiyomi.domain.updates.model

import tachiyomi.domain.anime.model.AnimeCover
import kotlin.time.Instant

data class UpdatesWithRelations(
    val animeId: Long,
    // AM (CUSTOM_INFORMATION) -->
    val customAnimeTitle: String?,
    val ogAnimeTitle: String,
    // <-- AM (CUSTOM_INFORMATION)
    val episodeId: Long,
    val episodeName: String,
    val scanlator: String?,
    val episodeUrl: String,
    val seen: Boolean,
    val bookmark: Boolean,
    // AY -->
    val fillermark: Boolean,
    // <-- AY
    val lastSecondSeen: Int,
    // AY -->
    val totalSeconds: Int,
    // <-- AY
    val sourceId: Long,
    val dateFetch: Instant,
    val coverData: AnimeCover,
) {
    // AM (CUSTOM_INFORMATION) -->
    val animeTitle: String = customAnimeTitle ?: ogAnimeTitle
    // <-- AM (CUSTOM_INFORMATION)
}
