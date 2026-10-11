// AY -->
package eu.kanade.tachiyomi.ui.browse.migration.season

import android.content.res.Configuration
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.map
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import eu.kanade.core.preference.asState
import eu.kanade.domain.anime.model.toSAnime
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.SAnime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import mihon.domain.anime.model.toDomainAnime
import mihon.domain.source.interactor.UpdateAnimeFromRemote
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.anime.interactor.GetAnime
import tachiyomi.domain.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.service.SourceManager

@AssistedInject
class MigrateSeasonSelectViewModel(
    @Assisted private val animeId: Long,
    @Assisted private val sourceId: Long,
    sourceManager: SourceManager,
    sourcePreferences: SourcePreferences,
    private val libraryPreferences: LibraryPreferences,
    private val getAnime: GetAnime,
    private val networkToLocalAnime: NetworkToLocalAnime,
    private val updateAnimeFromRemote: UpdateAnimeFromRemote,
) : ViewModel() {

    val state: StateFlow<MigrateSeasonSelectViewModel.State>
        field = MutableStateFlow<MigrateSeasonSelectViewModel.State>(State())

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(animeId: Long, sourceId: Long): MigrateSeasonSelectViewModel
    }

    var displayMode by sourcePreferences.sourceDisplayMode.asState(viewModelScope)
    private val source: AnimeSource? get() = state.value.source

    init {
        viewModelScope.launchIO {
            val source = sourceManager.getOrStub(sourceId)
            state.update {
                it.copy(source = source)
            }
        }
    }

    fun getColumnsPreference(orientation: Int): GridCells {
        val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE
        val columns = if (isLandscape) {
            libraryPreferences.landscapeColumns
        } else {
            libraryPreferences.portraitColumns
        }.get()
        return if (columns == 0) GridCells.Adaptive(128.dp) else GridCells.Fixed(columns)
    }

    private val hideInLibraryItems = sourcePreferences.hideInLibraryItems.get()
    val seasonPagerFlowFlow = flow { emit(getAnime.await(animeId)) }
        .map { anime ->
            Pager(
                config = PagingConfig(pageSize = 25),
                pagingSourceFactory = {
                    SeasonListPagingSource {
                        if (anime == null) return@SeasonListPagingSource emptyList()

                        updateAnimeFromRemote.awaitSeasonsUpdate(
                            anime = anime,
                            fetchSeasons = true,
                        )
                            .getOrThrow()
                            .newSeasons
                            .map { it.toSAnime() }
                    }
                },
            ).flow.map { pagingData ->
                pagingData.map {
                    networkToLocalAnime.invoke(it.toDomainAnime(sourceId))
                        .let { localAnime -> getAnime.subscribe(localAnime.url, localAnime.source) }
                        .filterNotNull()
                        .stateIn(viewModelScope)
                }
                    .filter { !hideInLibraryItems || !it.value.favorite }
            }
                .cachedIn(viewModelScope)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyFlow())

    private class SeasonListPagingSource(
        private val loadSeasonList: suspend () -> List<SAnime>,
    ) : PagingSource<Int, SAnime>() {
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, SAnime> {
            return try {
                val seasonList = loadSeasonList()

                LoadResult.Page(
                    data = seasonList,
                    prevKey = null,
                    nextKey = null,
                )
            } catch (e: Exception) {
                LoadResult.Error(e)
            }
        }

        override fun getRefreshKey(state: PagingState<Int, SAnime>): Int? {
            return null
        }
    }

    fun setDialog(dialog: Dialog?) {
        state.update { it.copy(dialog = dialog) }
    }

    sealed interface Dialog {
        data class Select(val anime: Anime) : Dialog
        data class Migrate(val newAnime: Anime, val oldAnime: Anime) : Dialog
    }

    @Immutable
    data class State(
        val dialog: Dialog? = null,
        val source: AnimeSource? = null,
    )
}
// <-- AY
