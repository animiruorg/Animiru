package tachiyomi.data.history

import tachiyomi.domain.anime.model.AnimeCover
import tachiyomi.domain.history.model.History
import tachiyomi.domain.history.model.HistoryWithRelations
import kotlin.time.Instant

object HistoryMapper {
    fun mapHistory(
        id: Long,
        episodeId: Long,
        seenAt: Instant?,
    ): History = History(
        id = id,
        episodeId = episodeId,
        seenAt = seenAt,
    )

    fun mapHistoryWithRelations(
        historyId: Long,
        animeId: Long,
        episodeId: Long,
        // AM -->
        customTitle: String?,
        // <-- AM
        title: String,
        thumbnailUrl: String?,
        sourceId: Long,
        isFavorite: Boolean,
        coverLastModified: Instant?,
        episodeNumber: Double,
        seenAt: Instant?,
    ): HistoryWithRelations = HistoryWithRelations(
        id = historyId,
        episodeId = episodeId,
        animeId = animeId,
        // AM (CUSTOM_INFORMATION) -->
        customTitle = customTitle,
        ogTitle = title,
        // <-- AM (CUSTOM_INFORMATION)
        episodeNumber = episodeNumber,
        seenAt = seenAt,
        coverData = AnimeCover(
            animeId = animeId,
            sourceId = sourceId,
            isAnimeFavorite = isFavorite,
            url = thumbnailUrl,
            lastModified = coverLastModified,
        ),
    )
}
