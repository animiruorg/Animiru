package eu.kanade.presentation.anime.components

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.anime.DownloadAction
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.components.DownloadDropdownMenu
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.automirroredrounded.Label
import mihon.icons.materialsymbols.rounded.Download
import mihon.icons.materialsymbols.rounded.EditDocument
import mihon.icons.materialsymbols.rounded.FilterList
import mihon.icons.materialsymbols.rounded.FlipToBack
import mihon.icons.materialsymbols.rounded.Forward10
import mihon.icons.materialsymbols.rounded.NetworkNode
import mihon.icons.materialsymbols.rounded.Notes
import mihon.icons.materialsymbols.rounded.Refresh
import mihon.icons.materialsymbols.rounded.SelectAll
import mihon.icons.materialsymbols.rounded.Settings
import mihon.icons.materialsymbols.rounded.Share
import mihon.icons.materialsymbols.rounded.SwapCalls
import tachiyomi.i18n.MR
import tachiyomi.i18n.animiru.AMMR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.theme.active

@Composable
fun AnimeToolbar(
    title: String,
    hasFilters: Boolean,
    navigateUp: () -> Unit,
    onClickFilter: () -> Unit,
    onClickShare: (() -> Unit)?,
    onClickDownload: ((DownloadAction) -> Unit)?,
    onClickEditCategory: (() -> Unit)?,
    onClickRefresh: () -> Unit,
    onClickMigrate: (() -> Unit)?,
    // AY -->
    onClickSettings: (() -> Unit)?,
    onClickSkipIntro: (() -> Unit)?,
    onMoreRelatedClicked: (() -> Unit)?,
    // <-- AY
    onClickEditNotes: () -> Unit,
    // AM (CUSTOM_INFORMATION) -->
    onClickEditInfo: (() -> Unit)?,
    // <-- AM (CUSTOM_INFORMATION)

    // For action mode
    actionModeCounter: Int,
    onCancelActionMode: () -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,

    titleAlphaProvider: () -> Float,
    backgroundAlphaProvider: () -> Float,
    modifier: Modifier = Modifier,
    // AM -->
    longNavigateUp: (() -> Unit)? = null,
    // <-- AM
) {
    val isActionMode = actionModeCounter > 0
    AppBar(
        titleContent = {
            if (isActionMode) {
                AppBarTitle(actionModeCounter.toString())
            } else {
                AppBarTitle(title, modifier = Modifier.alpha(titleAlphaProvider()))
            }
        },
        modifier = modifier,
        backgroundColor = MaterialTheme.colorScheme
            .surfaceColorAtElevation(3.dp)
            .copy(alpha = if (isActionMode) 1f else backgroundAlphaProvider()),
        navigateUp = navigateUp,
        // AM -->
        longNavigateUp = longNavigateUp,
        // <-- AM
        actions = {
            var downloadExpanded by remember { mutableStateOf(false) }
            if (onClickDownload != null) {
                val onDismissRequest = { downloadExpanded = false }
                DownloadDropdownMenu(
                    expanded = downloadExpanded,
                    onDismissRequest = onDismissRequest,
                    onDownloadClicked = onClickDownload,
                )
            }

            val filterTint = if (hasFilters) MaterialTheme.colorScheme.active else LocalContentColor.current
            AppBarActions(
                actions = buildList {
                    if (isActionMode) {
                        add(
                            AppBar.Action(
                                title = stringResource(MR.strings.action_select_all),
                                icon = MaterialSymbols.Rounded.SelectAll,
                                onClick = onSelectAll,
                            ),
                        )
                        add(
                            AppBar.Action(
                                title = stringResource(MR.strings.action_select_inverse),
                                icon = MaterialSymbols.Rounded.FlipToBack,
                                onClick = onInvertSelection,
                            ),
                        )
                        return@buildList
                    }
                    if (onClickDownload != null) {
                        add(
                            AppBar.Action(
                                title = stringResource(MR.strings.manga_download),
                                icon = MaterialSymbols.Rounded.Download,
                                onClick = { downloadExpanded = !downloadExpanded },
                            ),
                        )
                    }
                    add(
                        AppBar.Action(
                            title = stringResource(MR.strings.action_filter),
                            icon = MaterialSymbols.Rounded.FilterList,
                            iconTint = filterTint,
                            onClick = onClickFilter,
                        ),
                    )
                    add(
                        AppBar.OverflowAction.Item(
                            // AM -->
                            icon = MaterialSymbols.Rounded.Refresh,
                            // <-- AM
                            title = stringResource(MR.strings.action_webview_refresh),
                            onClick = onClickRefresh,
                        ),
                    )
                    if (onClickMigrate != null) {
                        add(
                            AppBar.OverflowAction.Item(
                                // AM -->
                                icon = MaterialSymbols.Rounded.SwapCalls,
                                // <-- AM
                                title = stringResource(MR.strings.action_migrate),
                                onClick = onClickMigrate,
                            ),
                        )
                    }
                    if (onClickShare != null) {
                        add(
                            AppBar.OverflowAction.Item(
                                // AM -->
                                icon = MaterialSymbols.Rounded.Share,
                                // <-- AM
                                title = stringResource(MR.strings.action_share),
                                onClick = onClickShare,
                            ),
                        )
                    }
                    // AM -->
                    if (onMoreRelatedClicked != null) {
                        add(
                            AppBar.OverflowAction.Item(
                                icon = MaterialSymbols.Rounded.NetworkNode,
                                title = stringResource(AMMR.strings.animeScreen_relation_header),
                                onClick = onMoreRelatedClicked,
                            ),
                        )
                    }
                    add(AppBar.OverflowAction.Separator)
                    if (onClickSkipIntro != null) {
                        add(
                            AppBar.OverflowAction.Item(
                                icon = MaterialSymbols.Rounded.Forward10,
                                title = stringResource(AYMR.strings.action_change_intro_length),
                                onClick = onClickSkipIntro,
                            ),
                        )
                    }
                    // <-- AM
                    if (onClickEditCategory != null) {
                        add(
                            AppBar.OverflowAction.Item(
                                // AM -->
                                icon = MaterialSymbols.AutoMirroredRounded.Label,
                                // <-- AM
                                title = stringResource(MR.strings.action_edit_categories),
                                onClick = onClickEditCategory,
                            ),
                        )
                    }
                    // AM (CUSTOM_INFORMATION) -->
                    if (onClickEditInfo != null) {
                        add(
                            AppBar.OverflowAction.Item(
                                icon = MaterialSymbols.Rounded.EditDocument,
                                title = stringResource(AMMR.strings.action_edit_info),
                                onClick = onClickEditInfo,
                            ),
                        )
                    }
                    // <-- AM (CUSTOM_INFORMATION)
                    add(
                        AppBar.OverflowAction.Item(
                            // AM -->
                            icon = MaterialSymbols.Rounded.Notes,
                            // <-- AM
                            title = stringResource(MR.strings.action_notes),
                            onClick = onClickEditNotes,
                        ),
                    )
                    // AM -->
                    if (onClickSettings != null) {
                        add(AppBar.OverflowAction.Separator)
                        add(
                            AppBar.OverflowAction.Item(
                                icon = MaterialSymbols.Rounded.Settings,
                                title = stringResource(AYMR.strings.settings),
                                onClick = onClickSettings,
                            ),
                        )
                    }
                    // <-- AM
                },
            )
        },
        isActionMode = isActionMode,
        onCancelActionMode = onCancelActionMode,
    )
}
