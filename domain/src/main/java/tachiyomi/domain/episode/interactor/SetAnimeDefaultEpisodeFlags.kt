package tachiyomi.domain.episode.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.domain.anime.interactor.GetFavorites
import tachiyomi.domain.anime.interactor.SetAnimeEpisodeFlags
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.library.service.LibraryPreferences

@Inject
class SetAnimeDefaultEpisodeFlags(
    private val libraryPreferences: LibraryPreferences,
    private val setAnimeEpisodeFlags: SetAnimeEpisodeFlags,
    private val getFavorites: GetFavorites,
) {

    suspend fun await(anime: Anime) {
        withNonCancellableContext {
            with(libraryPreferences) {
                setAnimeEpisodeFlags.awaitSetAllFlags(
                    animeId = anime.id,
                    unseenFilter = filterEpisodeBySeen.get(),
                    downloadedFilter = filterEpisodeByDownloaded.get(),
                    bookmarkedFilter = filterEpisodeByBookmarked.get(),
                    // AY -->
                    fillermarkedFilter = filterEpisodeByFillermarked.get(),
                    // <-- AY
                    sortingMode = sortEpisodeBySourceOrNumber.get(),
                    sortingDirection = sortEpisodeByAscendingOrDescending.get(),
                    displayMode = displayEpisodeByNameOrNumber.get(),
                    // AY -->
                    showPreviews = showEpisodeThumbnailPreviews.get(),
                    showSummaries = showEpisodeSummaries.get(),
                    // <-- AY
                    // AM -->
                    thumbnailSize = episodeThumbnailSize.get(),
                    // <-- AM
                )
            }
        }
    }

    suspend fun awaitAll() {
        withNonCancellableContext {
            getFavorites.await().forEach { await(it) }
        }
    }
}
