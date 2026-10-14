package tachiyomi.domain.anime.model

import mihon.domain.common.PartialUpdate

class AnimeUpdate(val id: Long, block: AnimeUpdate.() -> Unit) : PartialUpdate() {
    var favoriteAt: Long? by field(null)
    var lastUpdate: Long? by field(null)
    var nextUpdate: Long? by field(null)
    var fetchInterval: Int? by field(null)
    var viewerFlags: Long? by field(null)
    var episodeFlags: Long? by field(null)
    var coverLastModified: Long? by field(null)
    var notes: String? by field(null)

    // AM -->
    var backgroundLastModified: Long? by field(null)
    var seasonFlags: Long? by field(null)
    // <-- AM

    init {
        block()
    }
}
