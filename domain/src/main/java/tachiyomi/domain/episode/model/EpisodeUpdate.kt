package tachiyomi.domain.episode.model

import mihon.domain.common.PartialUpdate

class EpisodeUpdate(val id: Long, block: EpisodeUpdate.() -> Unit) : PartialUpdate() {
    var seen: Boolean? by field(null)
    var bookmark: Boolean? by field(null)
    var lastSecondSeen: Long? by field(null)
    var dateFetch: Long? by field(null)

    // AM -->
    var fillermark: Boolean? by field(null)
    var totalSeconds: Long? by field(null)
    var thumbnailLastModifiedAt: Long? by field(null)
    // <-- AM

    init {
        block()
    }
}
