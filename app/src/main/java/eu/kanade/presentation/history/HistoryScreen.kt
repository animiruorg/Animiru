package eu.kanade.presentation.history

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.presentation.components.relativeDateText
import eu.kanade.presentation.history.components.HistoryItem
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import eu.kanade.tachiyomi.ui.history.HistoryViewModel
import kotlinx.datetime.LocalDate
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.DeleteSweep
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.ListGroupHeader
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

@Composable
fun HistoryScreen(
    state: HistoryViewModel.State,
    onSearchQueryChange: (String?) -> Unit,
    onClickCover: (animeId: Long) -> Unit,
    onClickResume: (animeId: Long, episodeId: Long) -> Unit,
    onClickFavorite: (animeId: Long) -> Unit,
    onDialogChange: (HistoryViewModel.Dialog?) -> Unit,
    contentPadding: PaddingValues,
) {
    // AM (RECENTS_FILTER_CHIP) -->
    state.list.let {
        if (it == null) {
            LoadingScreen(Modifier.padding(contentPadding))
        } else if (it.isEmpty()) {
            val msg = if (!state.searchQuery.isNullOrEmpty()) {
                MR.strings.no_results_found
            } else {
                AYMR.strings.information_no_recent_anime
            }
            EmptyScreen(
                stringRes = msg,
                modifier = Modifier.padding(contentPadding),
            )
        } else {
            HistoryScreenContent(
                history = it,
                contentPadding = contentPadding,
                onClickCover = { history -> onClickCover(history.animeId) },
                onClickResume = { history -> onClickResume(history.animeId, history.episodeId) },
                onClickDelete = { item -> onDialogChange(HistoryViewModel.Dialog.Delete(item)) },
                onClickFavorite = { history -> onClickFavorite(history.animeId) },
            )
        }
    }
}

@Composable
fun HistoryTopBar(
    state: HistoryViewModel.State,
    onSearchQueryChange: (String?) -> Unit,
    onDialogChange: (HistoryViewModel.Dialog?) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    SearchToolbar(
        titleContent = { AppBarTitle(stringResource(MR.strings.history)) },
        searchQuery = state.searchQuery,
        onChangeSearchQuery = onSearchQueryChange,
        actions = {
            AppBarActions(
                listOf(
                    AppBar.Action(
                        title = stringResource(MR.strings.pref_clear_history),
                        icon = MaterialSymbols.Rounded.DeleteSweep,
                        onClick = {
                            onDialogChange(HistoryViewModel.Dialog.DeleteAll)
                        },
                    ),
                ),
            )
        },
        scrollBehavior = scrollBehavior,
    )
}
// <-- AM (RECENTS_FILTER_CHIP)

@Composable
private fun HistoryScreenContent(
    history: List<HistoryUiModel>,
    contentPadding: PaddingValues,
    onClickCover: (HistoryWithRelations) -> Unit,
    onClickResume: (HistoryWithRelations) -> Unit,
    onClickDelete: (HistoryWithRelations) -> Unit,
    onClickFavorite: (HistoryWithRelations) -> Unit,
) {
    FastScrollLazyColumn(
        contentPadding = contentPadding,
    ) {
        items(
            items = history,
            key = { "history-${it.hashCode()}" },
            contentType = {
                when (it) {
                    is HistoryUiModel.Header -> "header"
                    is HistoryUiModel.Item -> "item"
                }
            },
        ) { item ->
            when (item) {
                is HistoryUiModel.Header -> {
                    ListGroupHeader(
                        modifier = Modifier.animateItem(),
                        text = relativeDateText(item.date),
                    )
                }
                is HistoryUiModel.Item -> {
                    val value = item.item
                    HistoryItem(
                        modifier = Modifier.animateItem(),
                        history = value,
                        onClickCover = { onClickCover(value) },
                        onClickResume = { onClickResume(value) },
                        onClickDelete = { onClickDelete(value) },
                        onClickFavorite = { onClickFavorite(value) },
                    )
                }
            }
        }
    }
}

sealed interface HistoryUiModel {
    data class Header(val date: LocalDate) : HistoryUiModel
    data class Item(val item: HistoryWithRelations) : HistoryUiModel
}

@PreviewLightDark
@Composable
internal fun HistoryScreenPreviews(
    @PreviewParameter(HistoryViewModelStateProvider::class)
    historyState: HistoryViewModel.State,
) {
    TachiyomiPreviewTheme {
        HistoryScreen(
            state = historyState,
            onSearchQueryChange = {},
            onClickCover = {},
            onClickResume = { _, _ -> run {} },
            onDialogChange = {},
            onClickFavorite = {},
            contentPadding = PaddingValues(),
        )
    }
}
