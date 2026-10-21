// AY -->
package tachiyomi.domain.season.interactor

import aniyomi.domain.anime.model.SeasonDisplayMode
import dev.zacsweers.metro.Inject
import tachiyomi.domain.anime.interactor.GetFavorites
import tachiyomi.domain.anime.interactor.SetAnimeSeasonFlags
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.library.service.LibraryPreferences

@Inject
class SetAnimeDefaultSeasonFlags(
    private val libraryPreferences: LibraryPreferences,
    private val setAnimeSeasonFlags: SetAnimeSeasonFlags,
    private val getFavorites: GetFavorites,
) {
    suspend fun await(anime: Anime) {
        setDefaultFlags(listOf(anime.id))
    }

    suspend fun awaitAll() {
        setDefaultFlags(getFavorites.await().map { it.id })
    }

    private suspend fun setDefaultFlags(animeIds: List<Long>) {
        with(libraryPreferences) {
            setAnimeSeasonFlags.awaitSetAllFlags(
                animeIds = animeIds,
                downloadFilter = filterSeasonByDownload.get(),
                unseenFilter = filterSeasonByUnseen.get(),
                startedFilter = filterSeasonByStarted.get(),
                completedFilter = filterSeasonByCompleted.get(),
                bookmarkedFilter = filterSeasonByBookmarked.get(),
                fillermarkedFilter = filterSeasonByFillermarked.get(),
                sortingMode = sortSeasonBySourceOrNumber.get(),
                sortingDirection = sortSeasonByAscendingOrDescending.get(),
                displayGridMode = SeasonDisplayMode.fromLong(seasonDisplayGridMode.get()),
                displayGridSize = seasonDisplayGridSize.get(),
                downloadedOverlay = seasonDownloadOverlay.get(),
                unseenOverlay = seasonUnseenOverlay.get(),
                localOverlay = seasonLocalOverlay.get(),
                langOverlay = seasonLangOverlay.get(),
                continueOverlay = seasonContinueOverlay.get(),
                displayMode = seasonDisplayMode.get(),
            )
        }
    }
}
// <-- AY
