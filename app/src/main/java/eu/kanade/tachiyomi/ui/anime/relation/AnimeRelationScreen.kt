// AM -->
package eu.kanade.tachiyomi.ui.anime.relation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import aniyomi.domain.anime.interactor.GetRelatedAnime
import aniyomi.domain.anime.model.AnimeRelationGroup
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.domain.anime.interactor.SyncRelatedAnimeWithSource
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.anime.AnimeRelationScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.anime.model.Anime
import kotlin.time.Duration.Companion.seconds

class AnimeRelationScreen(
    private val anime: Anime,
) : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val viewModel = assistedMetroViewModel<Model, Model.Factory> { create(anime = anime) }
        val state by viewModel.state.collectAsState()

        AnimeRelationScreen(
            state = state,
            displayMode = viewModel.displayMode,
            navigateUp = navigator::pop,
            onRefresh = viewModel::refresh,
            onRelatedClick = { r ->
                navigator.push(AnimeScreen(r.id, fromSource = !r.favorite))
            },
            onRelatedLongClick = { r ->
                navigator.push(GlobalSearchScreen(r.title))
            },
        )
    }

    @AssistedInject
    class Model(
        @Assisted private val anime: Anime,
        private val getRelatedAnime: GetRelatedAnime,
        private val syncRelatedAnimeWithSource: SyncRelatedAnimeWithSource,
        private val sourcePreferences: SourcePreferences,
    ) : ViewModel() {

        @AssistedFactory
        @ManualViewModelAssistedFactoryKey
        @ContributesIntoMap(AppScope::class)
        interface Factory : ManualViewModelAssistedFactory {
            fun create(anime: Anime): Model
        }

        val displayMode = sourcePreferences.sourceDisplayMode.get()

        private val isRefreshing = MutableStateFlow(false)

        private val relations = getRelatedAnime.subscribe(anime.id)

        val state = combine(
            isRefreshing,
            relations,
        ) { refresh, relations ->
            State(refresh, relations)
        }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5.seconds),
                State(false, emptyList()),
            )

        fun refresh() {
            viewModelScope.launch(Dispatchers.IO) {
                isRefreshing.update { true }
                try {
                    syncRelatedAnimeWithSource.await(anime, true)
                } catch (e: Throwable) {
                    logcat(LogPriority.ERROR, e) { "Failed to fetch related anime" }
                }
                isRefreshing.update { false }
            }
        }
    }

    @Immutable
    data class State(
        val refreshing: Boolean,
        val relations: List<AnimeRelationGroup>,
    )
}
// <-- AM
