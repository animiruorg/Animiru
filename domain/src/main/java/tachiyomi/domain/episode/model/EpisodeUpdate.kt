package tachiyomi.domain.episode.model

import mihon.domain.common.PartialUpdate
import kotlin.time.Instant

class EpisodeUpdate(val id: Long, block: EpisodeUpdate.() -> Unit) : PartialUpdate() {
    var seen: Boolean? by field(null)
    var bookmark: Boolean? by field(null)
    var lastSecondSeen: Int? by field(null)
    var dateFetch: Instant? by field(null)

    // AM -->
    var fillermark: Boolean? by field(null)
    var totalSeconds: Int? by field(null)
    var thumbnailLastModifiedAt: Instant? by field(null)
    // <-- AM

    init {
        block()
    }
}
