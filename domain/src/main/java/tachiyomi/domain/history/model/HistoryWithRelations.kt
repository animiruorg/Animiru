package tachiyomi.domain.history.model

import tachiyomi.domain.anime.model.AnimeCover
import kotlin.time.Instant

data class HistoryWithRelations(
    val id: Long,
    val episodeId: Long,
    val animeId: Long,
    // AM (CUSTOM_INFORMATION) -->
    private val customTitle: String?,
    private val ogTitle: String,
    // <-- AM (CUSTOM_INFORMATION)
    val episodeNumber: Double,
    val seenAt: Instant?,
    val coverData: AnimeCover,
) {
    // AM (CUSTOM_INFORMATION) -->
    val title: String = customTitle ?: ogTitle
    // <-- AM (CUSTOM_INFORMATION)
}
