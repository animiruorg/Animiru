package eu.kanade.tachiyomi.util.episode

import eu.kanade.tachiyomi.data.download.DownloadCache
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.episode.model.Episode
import tachiyomi.source.local.isLocal

/**
 * Returns a copy of the list with not downloaded episodes removed.
 */
fun List<Episode>.filterDownloaded(anime: Anime, downloadCache: DownloadCache): List<Episode> {
    if (anime.isLocal()) return this

    return filter {
        downloadCache.isEpisodeDownloaded(
            it.name,
            it.scanlator,
            it.url,
            // AM (CUSTOM_INFORMATION) -->
            anime.ogTitle,
            // <-- AM (CUSTOM_INFORMATION)
            anime.source,
        )
    }
}
