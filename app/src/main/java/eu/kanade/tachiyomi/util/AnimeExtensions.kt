package eu.kanade.tachiyomi.util

import animiru.core.cache.ThumbnailCache
import eu.kanade.domain.anime.interactor.UpdateAnime
import eu.kanade.domain.anime.model.toSAnime
import eu.kanade.domain.episode.model.toSEpisode
import eu.kanade.tachiyomi.data.cache.BackgroundCache
import eu.kanade.tachiyomi.data.cache.CoverCache
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.episode.interactor.UpdateEpisode
import tachiyomi.domain.episode.model.Episode
import tachiyomi.source.local.image.LocalBackgroundManager
import tachiyomi.source.local.image.LocalCoverManager
import tachiyomi.source.local.image.LocalEpisodeThumbnailManager
import tachiyomi.source.local.isLocal
import java.io.InputStream
import kotlin.time.Clock

fun Anime.removeCovers(coverCache: CoverCache) {
    if (isLocal()) return
    coverCache.deleteFromCache(this, true)
}

// AY -->
fun Anime.removeBackgrounds(backgroundCache: BackgroundCache) {
    if (isLocal()) return
    backgroundCache.deleteFromCache(this, true)
}
// <-- AY

// AM -->
fun Episode.removeThumbnail(thumbnailCache: ThumbnailCache, isLocal: Boolean) {
    if (isLocal || this.previewUrl.isNullOrEmpty()) return
    thumbnailCache.deleteFromCache(this.previewUrl!!)
}
// <-- AM

suspend fun Anime.editCover(
    coverManager: LocalCoverManager,
    stream: InputStream,
    updateAnime: UpdateAnime,
    coverCache: CoverCache,
) {
    if (isLocal()) {
        coverManager.update(toSAnime(), stream)
        updateAnime.awaitUpdateCoverLastModified(id)
    } else if (favorite) {
        coverCache.setCustomCoverToCache(this, stream)
        updateAnime.awaitUpdateCoverLastModified(id)
    }
}

// AY -->
suspend fun Anime.editBackground(
    backgroundManager: LocalBackgroundManager,
    stream: InputStream,
    updateAnime: UpdateAnime,
    backgroundCache: BackgroundCache,
) {
    if (isLocal()) {
        backgroundManager.update(toSAnime(), stream)
        updateAnime.awaitUpdateBackgroundLastModified(id)
    } else if (favorite) {
        backgroundCache.setCustomBackgroundToCache(this, stream)
        updateAnime.awaitUpdateBackgroundLastModified(id)
    }
}

suspend fun Episode.editThumbnail(
    anime: Anime,
    thumbnailManager: LocalEpisodeThumbnailManager,
    updateEpisode: UpdateEpisode,
    stream: InputStream,
) {
    if (anime.isLocal()) {
        thumbnailManager.update(anime.toSAnime(), toSEpisode(), stream)
        updateEpisode.awaitUpdateThumbnailLastModified(id)
    } else if (anime.favorite) {
        updateEpisode.awaitUpdateThumbnailLastModified(id)
    }
}
// <-- AY
