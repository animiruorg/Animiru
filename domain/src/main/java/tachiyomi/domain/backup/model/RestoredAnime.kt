package tachiyomi.domain.backup.model

import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.track.model.Track
import java.util.Date

data class RestoredAnime(
    val anime: Anime,
    val episodes: List<Episode>,
    val categoryIds: List<Long>,
    val history: List<RestoredHistory>,
    val tracks: List<Track>,
    val excludedScanlators: List<String>,
    // AM -->
    val parent: ParentAnime?,
    // <-- AM
)

data class RestoredHistory(
    val episodeUrl: String,
    val seenAt: Date?,
)

// AM -->
data class ParentAnime(
    val url: String,
    val sourceId: Long,
)
// <-- AM
