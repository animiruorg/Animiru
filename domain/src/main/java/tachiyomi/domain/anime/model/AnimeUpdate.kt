package tachiyomi.domain.anime.model

import mihon.domain.common.PartialUpdate
import kotlin.time.Instant

class AnimeUpdate(val id: Long, block: AnimeUpdate.() -> Unit) : PartialUpdate() {
    var favoriteAt: Instant? by field(null)
    var lastUpdate: Instant? by field(null)
    var nextUpdate: Instant? by field(null)
    var fetchInterval: Int? by field(null)
    var viewerFlags: Long? by field(null)
    var episodeFlags: Long? by field(null)
    var coverLastModified: Instant? by field(null)
    var notes: String? by field(null)

    // AM -->
    var backgroundLastModified: Instant? by field(null)
    var seasonFlags: Long? by field(null)
    var parentId: Long? by field(null)
    // <-- AM

    init {
        block()
    }
}
