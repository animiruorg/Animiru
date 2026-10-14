package eu.kanade.presentation.more.settings.screen.advanced

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.browse.components.SourceIcon
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.FlipToBack
import mihon.icons.materialsymbols.rounded.SelectAll
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchUI
import tachiyomi.core.common.util.lang.toLong
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.data.Database
import tachiyomi.data.source.mapSourceToDomainSource
import tachiyomi.domain.anime.repository.AnimeRepository
import tachiyomi.domain.history.repository.HistoryRepository
import tachiyomi.domain.source.interactor.GetSourcesWithNonLibraryAnime
import tachiyomi.domain.source.model.Source
import tachiyomi.domain.source.model.SourceWithIds
import tachiyomi.domain.source.model.StubSource
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.animiru.AMMR
import tachiyomi.presentation.core.components.LazyColumnWithAction
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.selectedBackground

class ClearDatabaseScreen : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val model = metroViewModel<ClearDatabaseViewModel>()
        val state by model.state.collectAsState()
        val scope = rememberCoroutineScope()

        when (val s = state) {
            is ClearDatabaseViewModel.State.Loading -> LoadingScreen()
            is ClearDatabaseViewModel.State.Ready -> {
                if (s.showConfirmation) {
                    var keepSeenAnime by remember { mutableStateOf(true) }
                    AlertDialog(
                        title = {
                            Text(text = stringResource(MR.strings.are_you_sure))
                        },
                        text = {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                            ) {
                                Text(text = stringResource(MR.strings.clear_database_text))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = stringResource(AMMR.strings.am_clear_db_exclude_seen),
                                        modifier = Modifier.weight(1f),
                                    )
                                    Switch(
                                        checked = keepSeenAnime,
                                        onCheckedChange = { keepSeenAnime = it },
                                    )
                                }
                                if (!keepSeenAnime) {
                                    Text(
                                        text = stringResource(AMMR.strings.am_clear_database_history_warning),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        },
                        onDismissRequest = model::hideConfirmation,
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    scope.launchUI {
                                        model.removeAnimeBySourceId(keepSeenAnime)
                                        model.clearSelection()
                                        model.hideConfirmation()
                                        context.toast(MR.strings.clear_database_completed)
                                    }
                                },
                            ) {
                                Text(text = stringResource(MR.strings.action_ok))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = model::hideConfirmation) {
                                Text(text = stringResource(MR.strings.action_cancel))
                            }
                        },
                    )
                }

                Scaffold(
                    topBar = { scrollBehavior ->
                        AppBar(
                            title = stringResource(MR.strings.pref_clear_database),
                            navigateUp = navigator::pop,
                            actions = {
                                if (s.items.isNotEmpty()) {
                                    AppBarActions(
                                        actions = listOf(
                                            AppBar.Action(
                                                title = stringResource(MR.strings.action_select_all),
                                                icon = MaterialSymbols.Rounded.SelectAll,
                                                onClick = model::selectAll,
                                            ),
                                            AppBar.Action(
                                                title = stringResource(MR.strings.action_select_inverse),
                                                icon = MaterialSymbols.Rounded.FlipToBack,
                                                onClick = model::invertSelection,
                                            ),
                                        ),
                                    )
                                }
                            },
                            scrollBehavior = scrollBehavior,
                        )
                    },
                ) { contentPadding ->
                    if (s.items.isEmpty()) {
                        EmptyScreen(
                            message = stringResource(MR.strings.database_clean),
                            modifier = Modifier.padding(contentPadding),
                        )
                    } else {
                        LazyColumnWithAction(
                            contentPadding = contentPadding,
                            actionLabel = stringResource(MR.strings.action_delete),
                            actionEnabled = s.selection.isNotEmpty(),
                            onClickAction = model::showConfirmation,
                        ) {
                            items(s.items) { sourceWithCount ->
                                ClearDatabaseItem(
                                    source = sourceWithCount.source,
                                    count = sourceWithCount.count,
                                    isSelected = s.selection.contains(sourceWithCount.id),
                                    onClickSelect = { model.toggleSelection(sourceWithCount.source) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ClearDatabaseItem(
        source: Source,
        count: Long,
        isSelected: Boolean,
        onClickSelect: () -> Unit,
    ) {
        Row(
            modifier = Modifier
                .selectedBackground(isSelected)
                .clickable(onClick = onClickSelect)
                .padding(horizontal = 8.dp)
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SourceIcon(source = source)
            Column(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .weight(1f),
            ) {
                Text(
                    text = source.visualName,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(text = stringResource(MR.strings.clear_database_source_item_count, count))
            }
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClickSelect() },
            )
        }
    }
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class ClearDatabaseViewModel(
    // AY -->
    private val getSourcesWithNonLibraryAnime: GetSourcesWithNonLibraryAnime,
    // <-- AY
    private val animeRepository: AnimeRepository,
    private val historyRepository: HistoryRepository,
    private val sourceManager: SourceManager,
) : ViewModel() {

    val state: StateFlow<State>
        field = MutableStateFlow<ClearDatabaseViewModel.State>(State.Loading)

    init {
        viewModelScope.launchIO {
            getSourcesWithNonLibraryAnime.subscribe()
                .collectLatest { list ->
                    // AY -->
                    val items = list.groupBy { it.sourceId }
                        .map { (sourceId, deletableAnime) ->
                            val source = sourceManager.getOrStub(sourceId)
                            val domainSource = mapSourceToDomainSource(source).copy(
                                isStub = source is StubSource,
                            )

                            val ids = mutableListOf<Long>()
                            val orphaned = mutableListOf<Long>()

                            deletableAnime.forEach {
                                ids.add(it.animeId)
                                if (it.fetchType == FetchType.Seasons) {
                                    val (childrenIds, orphanedIds) = getDeletableChildren(it.animeId)
                                    ids.addAll(childrenIds)
                                    orphaned.addAll(orphanedIds)
                                }
                            }

                            SourceWithIds(domainSource, ids, orphaned)
                        }
                    // <-- AY

                    state.update { old ->
                        val items = items.sortedBy { it.name }
                        when (old) {
                            State.Loading -> State.Ready(items)
                            is State.Ready -> old.copy(items = items)
                        }
                    }
                }
        }
    }

    // AY -->

    /**
     * Get all children of an anime that can be deleted, as well as any orphans.
     * Children that are favorited needs their parentId removed or else they won't be
     * able to be removed later.
     */
    private suspend fun getDeletableChildren(animeId: Long): Pair<List<Long>, List<Long>> {
        val ids = mutableListOf<Long>()
        val orphaned = mutableListOf<Long>()
        val children = getSourcesWithNonLibraryAnime.getDeletableChildren(animeId)
        children.forEach { c ->
            if (c.favorite) {
                orphaned.add(c.id)
            } else {
                ids.add(c.id)
                if (c.fetchType == FetchType.Seasons) {
                    val (childrenIds, orphanedIds) = getDeletableChildren(c.id)
                    ids.addAll(childrenIds)
                    orphaned.addAll(orphanedIds)
                }
            }
        }
        return Pair(ids, orphaned)
    }
    // <-- AY

    suspend fun removeAnimeBySourceId(keepSeenAnime: Boolean) = withNonCancellableContext {
        val state = state.value as? State.Ready ?: return@withNonCancellableContext
        // AY -->
        val selected = state.items.filter { it.id in state.selection }

        val animeIds = selected.flatMap { it.ids }
        val orphaned = selected.flatMap { it.orphaned }
            .filterNot { it in animeIds }

        animeRepository.deleteNonLibraryAnime(animeIds, keepSeenAnime)
        animeRepository.removeParentIdByIds(orphaned)
        // <-- AY
        historyRepository.deleteResetHistory()
    }

    fun toggleSelection(source: Source) = state.update { state ->
        if (state !is State.Ready) return@update state
        val mutableList = state.selection.toMutableList()
        if (mutableList.contains(source.id)) {
            mutableList.remove(source.id)
        } else {
            mutableList.add(source.id)
        }
        state.copy(selection = mutableList)
    }

    fun clearSelection() = state.update { state ->
        if (state !is State.Ready) return@update state
        state.copy(selection = emptyList())
    }

    fun selectAll() = state.update { state ->
        if (state !is State.Ready) return@update state
        state.copy(selection = state.items.fastMap { it.id })
    }

    fun invertSelection() = state.update { state ->
        if (state !is State.Ready) return@update state
        state.copy(
            selection = state.items
                .fastMap { it.id }
                .filterNot { it in state.selection },
        )
    }

    fun showConfirmation() = state.update { state ->
        if (state !is State.Ready) return@update state
        state.copy(showConfirmation = true)
    }

    fun hideConfirmation() = state.update { state ->
        if (state !is State.Ready) return@update state
        state.copy(showConfirmation = false)
    }

    sealed interface State {
        @Immutable
        data object Loading : State

        @Immutable
        data class Ready(
            val items: List<SourceWithIds>,
            val selection: List<Long> = emptyList(),
            val showConfirmation: Boolean = false,
        ) : State
    }
}
