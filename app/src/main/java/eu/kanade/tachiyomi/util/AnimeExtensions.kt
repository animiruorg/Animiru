package eu.kanade.tachiyomi.util

import animiru.core.cache.ThumbnailCache
import eu.kanade.domain.anime.interactor.UpdateAnime
import eu.kanade.domain.anime.model.toSAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.data.cache.BackgroundCache
import eu.kanade.tachiyomi.data.cache.CoverCache
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.episode.model.Episode
import tachiyomi.source.local.image.LocalBackgroundManager
import tachiyomi.source.local.image.LocalCoverManager
import tachiyomi.source.local.image.LocalEpisodeThumbnailManager
import tachiyomi.source.local.isLocal
import java.io.InputStream
import kotlin.time.Clock

fun Anime.removeCovers(coverCache: CoverCache): Anime {
    if (isLocal()) return this
    return if (coverCache.deleteFromCache(this, true) > 0) {
        copy(coverLastModified = Clock.System.now().toEpochMilliseconds())
    } else {
        this
    }
}

// AY -->
fun Anime.removeBackgrounds(backgroundCache: BackgroundCache): Anime {
    if (isLocal()) return this
    return if (backgroundCache.deleteFromCache(this, true) > 0) {
        copy(backgroundLastModified = Clock.System.now().toEpochMilliseconds())
    } else {
        this
    }
}
// <-- AY

// AM -->
fun Episode.removeThumbnail(thumbnailCache: ThumbnailCache, isLocal: Boolean): Episode {
    if (isLocal || this.previewUrl.isNullOrEmpty()) return this
    return if (thumbnailCache.deleteFromCache(this.previewUrl!!) > 0) {
        copy(thumbnailLastModifiedAt = Clock.System.now().toEpochMilliseconds())
    } else {
        this
    }
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

fun SEpisode.editThumbnail(
    anime: Anime,
    thumbnailManager: LocalEpisodeThumbnailManager,
    stream: InputStream,
) {
    if (anime.isLocal()) {
        thumbnailManager.update(anime.toSAnime(), this, stream)
    }
}
// <-- AY
