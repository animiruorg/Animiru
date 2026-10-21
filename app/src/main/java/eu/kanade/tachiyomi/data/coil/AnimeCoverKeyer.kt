package eu.kanade.tachiyomi.data.coil

import coil3.key.Keyer
import coil3.request.Options
import eu.kanade.domain.anime.model.hasCustomBackground
import eu.kanade.domain.anime.model.hasCustomCover
import eu.kanade.tachiyomi.data.cache.CoverCache
import tachiyomi.domain.anime.model.AnimeCover
import tachiyomi.domain.anime.model.Anime as DomainAnime

class AnimeKeyer : Keyer<DomainAnime> {
    override fun key(data: DomainAnime, options: Options): String {
        // AY -->
        return when {
            options.useBackground && data.hasCustomBackground() -> "anime;${data.id};${data.backgroundLastModified}"
            options.useBackground -> "anime;${data.backgroundUrl};${data.backgroundLastModified}"
            data.hasCustomCover() -> "anime;${data.id};${data.coverLastModified}"
            else -> "anime;${data.thumbnailUrl};${data.coverLastModified}"
        }
        // <-- AY
    }
}

class AnimeCoverKeyer(
    private val coverCache: CoverCache,
) : Keyer<AnimeCover> {
    override fun key(data: AnimeCover, options: Options): String {
        return if (coverCache.hasCustomCover(data.animeId)) {
            "anime;${data.animeId};${data.lastModified}"
        } else {
            "anime;${data.url};${data.lastModified}"
        }
    }
}
