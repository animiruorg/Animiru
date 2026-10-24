package eu.kanade.tachiyomi.ui.anime.notes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
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
import eu.kanade.presentation.anime.AnimeNotesScreen
import eu.kanade.presentation.util.Screen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mihon.core.metro.AppCoroutineScope
import tachiyomi.domain.anime.interactor.UpdateAnimeNotes
import tachiyomi.domain.anime.model.Anime

class AnimeNotesScreen(
    private val anime: Anime,
) : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val viewModel = assistedMetroViewModel<Model, Model.Factory> { create(anime = anime) }
        val state by viewModel.state.collectAsState()

        AnimeNotesScreen(
            state = state,
            navigateUp = navigator::pop,
            onUpdate = viewModel::updateNotes,
        )
    }

    @AssistedInject
    class Model(
        @Assisted private val anime: Anime,
        @AppCoroutineScope private val appScope: CoroutineScope,
        private val updateAnimeNotes: UpdateAnimeNotes,
    ) : ViewModel() {

        val state: StateFlow<State>
            field = MutableStateFlow<State>(State(anime, anime.notes))

        @AssistedFactory
        @ManualViewModelAssistedFactoryKey
        @ContributesIntoMap(AppScope::class)
        interface Factory : ManualViewModelAssistedFactory {
            fun create(anime: Anime): Model
        }

        fun updateNotes(content: String) {
            if (content == state.value.notes) return

            state.update {
                it.copy(notes = content)
            }

            // On Main so the debounced saves and the one on leaving are written in order
            appScope.launch(Dispatchers.Main.immediate) {
                updateAnimeNotes(anime.id, content)
            }
        }
    }

    @Immutable
    data class State(
        val anime: Anime,
        val notes: String,
    )
}
