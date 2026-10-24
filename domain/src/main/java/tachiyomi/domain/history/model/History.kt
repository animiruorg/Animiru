package tachiyomi.domain.history.model

import kotlin.time.Instant

data class History(
    val id: Long,
    val episodeId: Long,
    val seenAt: Instant?,
) {
    companion object {
        fun create() = History(
            id = -1L,
            episodeId = -1L,
            seenAt = null,
        )
    }
}
