package eu.kanade.domain.anime.model

import android.content.Context
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.data.cache.BackgroundCache
import eu.kanade.tachiyomi.data.cache.CoverCache
import mihon.app.di.appGraph
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.anime.model.Anime
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// TODO: move these into the domain model
val Anime.downloadedFilter: TriState
    get() {
        if (Injekt.get<Context>().appGraph.basePreferences.downloadedOnly.get()) return TriState.ENABLED_IS
        return when (downloadedFilterRaw) {
            Anime.EPISODE_SHOW_DOWNLOADED -> TriState.ENABLED_IS
            Anime.EPISODE_SHOW_NOT_DOWNLOADED -> TriState.ENABLED_NOT
            else -> TriState.DISABLED
        }
    }

// AY -->
val Anime.seasonDownloadedFilter: TriState
    get() {
        if (Injekt.get<Context>().appGraph.basePreferences.downloadedOnly.get()) return TriState.ENABLED_IS
        return when (seasonDownloadedFilterRaw) {
            Anime.SEASON_SHOW_DOWNLOADED -> TriState.ENABLED_IS
            Anime.SEASON_SHOW_NOT_DOWNLOADED -> TriState.ENABLED_NOT
            else -> TriState.DISABLED
        }
    }

fun Anime.seasonsFiltered(): Boolean {
    return seasonDownloadedFilter != TriState.DISABLED ||
        seasonUnseenFilter != TriState.DISABLED ||
        seasonStartedFilter != TriState.DISABLED ||
        seasonCompletedFilter != TriState.DISABLED ||
        seasonBookmarkedFilter != TriState.DISABLED ||
        seasonFillermarkedFilter != TriState.DISABLED
}
// <-- AY

fun Anime.episodesFiltered(): Boolean {
    return unseenFilter != TriState.DISABLED ||
        downloadedFilter != TriState.DISABLED ||
        bookmarkedFilter != TriState.DISABLED ||
        // AY -->
        fillermarkedFilter != TriState.DISABLED
    // <-- AY
}

fun Anime.toSAnime(): SAnime = SAnime.create().also {
    it.url = url
    it.title = title
    it.artist = artist
    it.author = author
    it.description = description
    it.genre = genre.orEmpty().joinToString()
    it.status = status.toInt()
    it.thumbnail_url = thumbnailUrl
    // AY -->
    it.background_url = backgroundUrl
    it.fetch_type = fetchType
    it.season_number = seasonNumber
    // <-- AY
    it.initialized = initialized
    it.memo = memo
}

fun Anime.copyFrom(other: SAnime): Anime {
    // AM (CUSTOM_INFORMATION) -->
    val author = other.author ?: ogAuthor
    val artist = other.artist ?: ogArtist
    val description = other.description ?: ogDescription
    val genres = if (other.genre != null) {
        other.getGenres()
    } else {
        ogGenre
    }
    // <-- AM (CUSTOM_INFORMATION)
    val thumbnailUrl = other.thumbnail_url ?: thumbnailUrl
    // AY -->
    val backgroundUrl = other.background_url ?: backgroundUrl
    // <-- AY
    return this.copy(
        // AM (CUSTOM_INFORMATION) -->
        ogAuthor = author,
        ogArtist = artist,
        ogDescription = description,
        ogGenre = genres,
        // <-- AM (CUSTOM_INFORMATION)
        thumbnailUrl = thumbnailUrl,
        // AM (CUSTOM_INFORMATION) -->
        ogStatus = other.status.toLong(),
        // <-- AM (CUSTOM_INFORMATION)
        // AY -->
        backgroundUrl = backgroundUrl,
        // <-- AY
        updateStrategy = other.update_strategy,
        // AY -->
        fetchType = other.fetch_type,
        seasonNumber = other.season_number,
        // <-- AY
        initialized = other.initialized && initialized,
        memo = other.memo,
    )
}

fun Anime.hasCustomCover(coverCache: CoverCache = Injekt.get<Context>().appGraph.coverCache): Boolean {
    return coverCache.getCustomCoverFile(id).exists()
}

// AY -->
fun Anime.hasCustomBackground(
    backgroundCache: BackgroundCache = Injekt.get<Context>().appGraph.backgroundCache,
): Boolean {
    return backgroundCache.getCustomBackgroundFile(id).exists()
}
// <-- AY
