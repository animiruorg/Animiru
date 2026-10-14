package eu.kanade.presentation.history.components

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import tachiyomi.domain.anime.model.AnimeCover
import tachiyomi.domain.history.model.HistoryWithRelations
import java.util.Date

internal class HistoryWithRelationsProvider : PreviewParameterProvider<HistoryWithRelations> {

    private val simple = HistoryWithRelations(
        id = 1L,
        episodeId = 2L,
        animeId = 3L,
        // AM (CUSTOM_INFORMATION) -->
        customTitle = null,
        ogTitle = "Test Title",
        // <-- AM (CUSTOM_INFORMATION)
        episodeNumber = 10.2,
        seenAt = Date(1697247357L),
        coverData = AnimeCover(
            animeId = 3L,
            sourceId = 4L,
            isAnimeFavorite = false,
            url = "https://example.com/cover.png",
            lastModified = 5L,
        ),
    )

    private val historyWithoutSeenAt = HistoryWithRelations(
        id = 1L,
        episodeId = 2L,
        animeId = 3L,
        // AM (CUSTOM_INFORMATION) -->
        customTitle = null,
        ogTitle = "Test Title",
        // <-- AM (CUSTOM_INFORMATION)
        episodeNumber = 10.2,
        seenAt = null,
        coverData = AnimeCover(
            animeId = 3L,
            sourceId = 4L,
            isAnimeFavorite = false,
            url = "https://example.com/cover.png",
            lastModified = 5L,
        ),
    )

    private val historyWithNegativeEpisodeNumber = HistoryWithRelations(
        id = 1L,
        episodeId = 2L,
        animeId = 3L,
        // AM (CUSTOM_INFORMATION) -->
        customTitle = null,
        ogTitle = "Test Title",
        // <-- AM (CUSTOM_INFORMATION)
        episodeNumber = -2.0,
        seenAt = Date(1697247357L),
        coverData = AnimeCover(
            animeId = 3L,
            sourceId = 4L,
            isAnimeFavorite = false,
            url = "https://example.com/cover.png",
            lastModified = 5L,
        ),
    )

    override val values: Sequence<HistoryWithRelations>
        get() = sequenceOf(simple, historyWithoutSeenAt, historyWithNegativeEpisodeNumber)
}
