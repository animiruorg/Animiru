package tachiyomi.domain.history.model

import kotlin.time.Instant

data class HistoryUpdate(
    val episodeId: Long,
    val seenAt: Instant,
)
