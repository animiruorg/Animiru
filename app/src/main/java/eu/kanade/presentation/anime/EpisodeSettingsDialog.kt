package eu.kanade.presentation.anime

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PeopleAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.kanade.domain.anime.model.downloadedFilter
import eu.kanade.presentation.components.TabbedDialog
import eu.kanade.presentation.components.TabbedDialogPaddings
import mihon.app.di.appGraph
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.anime.model.Anime
import tachiyomi.i18n.MR
import tachiyomi.i18n.animiru.AMMR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.CheckboxItem
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.RadioItem
import tachiyomi.presentation.core.components.SliderItem
import tachiyomi.presentation.core.components.SortItem
import tachiyomi.presentation.core.components.TriStateItem
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.theme.active
import java.util.Locale

@Composable
fun EpisodeSettingsDialog(
    onDismissRequest: () -> Unit,
    anime: Anime? = null,
    onDownloadFilterChanged: (TriState) -> Unit,
    onUnseenFilterChanged: (TriState) -> Unit,
    onBookmarkedFilterChanged: (TriState) -> Unit,
    // AY -->
    onFillermarkedFilterChanged: (TriState) -> Unit,
    // <-- AY
    scanlatorFilterActive: Boolean,
    onScanlatorFilterClicked: (() -> Unit),
    onSortModeChanged: (Long) -> Unit,
    onDisplayModeChanged: (Long) -> Unit,
    // AY -->
    onShowPreviewsEnabled: (Long) -> Unit,
    onShowSummariesEnabled: (Long) -> Unit,
    // <-- AY
    // AM -->
    onSetThumbnailSize: (Int) -> Unit,
    // <-- AM
    onSetAsDefault: (applyToExistingAnime: Boolean) -> Unit,
    onResetToDefault: () -> Unit,
) {
    var showSetAsDefaultDialog by rememberSaveable { mutableStateOf(false) }
    if (showSetAsDefaultDialog) {
        SetAsDefaultDialog(
            onDismissRequest = { showSetAsDefaultDialog = false },
            onConfirmed = onSetAsDefault,
        )
    }

    val context = LocalContext.current
    val downloadedOnly = remember { context.appGraph.basePreferences.downloadedOnly.get() }

    TabbedDialog(
        onDismissRequest = onDismissRequest,
        tabTitles = listOf(
            stringResource(MR.strings.action_filter),
            stringResource(MR.strings.action_sort),
            stringResource(MR.strings.action_display),
        ),
        tabOverflowMenuContent = { closeMenu ->
            DropdownMenuItem(
                text = { Text(stringResource(MR.strings.set_chapter_settings_as_default)) },
                onClick = {
                    showSetAsDefaultDialog = true
                    closeMenu()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(MR.strings.action_reset)) },
                onClick = {
                    onResetToDefault()
                    closeMenu()
                },
            )
        },
    ) { page ->
        Column(
            modifier = Modifier
                .padding(vertical = TabbedDialogPaddings.Vertical)
                .verticalScroll(rememberScrollState()),
        ) {
            when (page) {
                0 -> {
                    FilterPage(
                        downloadFilter = anime?.downloadedFilter ?: TriState.DISABLED,
                        onDownloadFilterChanged = onDownloadFilterChanged
                            .takeUnless { downloadedOnly },
                        unseenFilter = anime?.unseenFilter ?: TriState.DISABLED,
                        onUnseenFilterChanged = onUnseenFilterChanged,
                        bookmarkedFilter = anime?.bookmarkedFilter ?: TriState.DISABLED,
                        onBookmarkedFilterChanged = onBookmarkedFilterChanged,
                        // AY -->
                        fillermarkedFilter = anime?.fillermarkedFilter ?: TriState.DISABLED,
                        onFillermarkedFilterChanged = onFillermarkedFilterChanged,
                        // <-- AY
                        scanlatorFilterActive = scanlatorFilterActive,
                        onScanlatorFilterClicked = onScanlatorFilterClicked,
                    )
                }
                1 -> {
                    SortPage(
                        sortingMode = anime?.sorting ?: 0,
                        sortDescending = anime?.sortDescending() ?: false,
                        onItemSelected = onSortModeChanged,
                    )
                }
                2 -> {
                    DisplayPage(
                        displayMode = anime?.displayMode ?: 0,
                        // AY -->
                        onDisplayModeChanged = onDisplayModeChanged,
                        showPreviews = anime?.showPreviews() ?: true,
                        onShowPreviewsEnabled = onShowPreviewsEnabled,
                        showSummaries = anime?.showSummaries() ?: true,
                        onShowSummariesEnabled = onShowSummariesEnabled,
                        // <-- AY
                        // AM -->
                        thumbnailSize = anime?.episodeThumbnailSize ?: 0,
                        // <-- AM
                        onSetThumbnailSize = onSetThumbnailSize,
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.FilterPage(
    downloadFilter: TriState,
    onDownloadFilterChanged: ((TriState) -> Unit)?,
    unseenFilter: TriState,
    onUnseenFilterChanged: (TriState) -> Unit,
    bookmarkedFilter: TriState,
    onBookmarkedFilterChanged: (TriState) -> Unit,
    // AY -->
    fillermarkedFilter: TriState,
    onFillermarkedFilterChanged: (TriState) -> Unit,
    // <-- AY
    scanlatorFilterActive: Boolean,
    onScanlatorFilterClicked: (() -> Unit),
) {
    TriStateItem(
        label = stringResource(MR.strings.label_downloaded),
        state = downloadFilter,
        onClick = onDownloadFilterChanged,
    )
    TriStateItem(
        label = stringResource(AYMR.strings.action_filter_unseen),
        state = unseenFilter,
        onClick = onUnseenFilterChanged,
    )
    TriStateItem(
        label = stringResource(MR.strings.action_filter_bookmarked),
        state = bookmarkedFilter,
        onClick = onBookmarkedFilterChanged,
    )
    // AY -->
    TriStateItem(
        label = stringResource(AYMR.strings.action_filter_fillermarked),
        state = fillermarkedFilter,
        onClick = onFillermarkedFilterChanged,
    )
    // <-- AY
    ScanlatorFilterItem(
        active = scanlatorFilterActive,
        onClick = onScanlatorFilterClicked,
    )
}

@Composable
fun ScanlatorFilterItem(
    active: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .fillMaxWidth()
            .padding(horizontal = TabbedDialogPaddings.Horizontal, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.PeopleAlt,
            contentDescription = null,
            tint = if (active) {
                MaterialTheme.colorScheme.active
            } else {
                LocalContentColor.current
            },
        )
        Text(
            text = stringResource(MR.strings.scanlator),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ColumnScope.SortPage(
    sortingMode: Long,
    sortDescending: Boolean,
    onItemSelected: (Long) -> Unit,
) {
    listOf(
        MR.strings.sort_by_source to Anime.EPISODE_SORTING_SOURCE,
        AYMR.strings.sort_by_episode_number to Anime.EPISODE_SORTING_NUMBER,
        MR.strings.sort_by_upload_date to Anime.EPISODE_SORTING_UPLOAD_DATE,
        MR.strings.action_sort_alpha to Anime.EPISODE_SORTING_ALPHABET,
    ).map { (titleRes, mode) ->
        SortItem(
            label = stringResource(titleRes),
            sortDescending = sortDescending.takeIf { sortingMode == mode },
            onClick = { onItemSelected(mode) },
        )
    }
}

@Composable
private fun ColumnScope.DisplayPage(
    displayMode: Long,
    // AY -->
    onDisplayModeChanged: (Long) -> Unit,
    showPreviews: Boolean,
    onShowPreviewsEnabled: (Long) -> Unit,
    showSummaries: Boolean,
    onShowSummariesEnabled: (Long) -> Unit,
    // <-- AY
    // AM -->
    thumbnailSize: Int,
    onSetThumbnailSize: (Int) -> Unit,
    // <-- AM
) {
    listOf(
        MR.strings.show_title to Anime.EPISODE_DISPLAY_NAME,
        AYMR.strings.show_episode_number to Anime.EPISODE_DISPLAY_NUMBER,
    ).map { (titleRes, mode) ->
        RadioItem(
            label = stringResource(titleRes),
            selected = displayMode == mode,
            onClick = { onDisplayModeChanged(mode) },
        )
    }
    // AY -->
    val showPreviewsFlag = if (showPreviews) Anime.EPISODE_SHOW_NOT_PREVIEWS else Anime.EPISODE_SHOW_PREVIEWS
    CheckboxItem(
        label = stringResource(AYMR.strings.show_episode_previews),
        checked = showPreviews,
        onClick = { onShowPreviewsEnabled(showPreviewsFlag) },
    )
    val showSummariesFlag = if (showSummaries) Anime.EPISODE_SHOW_NOT_SUMMARIES else Anime.EPISODE_SHOW_SUMMARIES
    CheckboxItem(
        label = stringResource(AYMR.strings.show_episode_summaries),
        checked = showSummaries,
        onClick = { onShowSummariesEnabled(showSummariesFlag) },
    )
    // <-- AY

    // AM -->
    SliderItem(
        value = thumbnailSize,
        valueRange = 0 until 10,
        label = stringResource(AMMR.strings.animeScreen_thumbnailSize),
        valueString = "${ String.format(Locale.US, "%.1f", (thumbnailSize + 1) / 5.0) }x",
        onChange = { onSetThumbnailSize(it) },
        pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )
    // <-- AM
}

@Composable
internal fun SetAsDefaultDialog(
    // AY -->
    isEpisode: Boolean = true,
    // <-- AY
    onDismissRequest: () -> Unit,
    onConfirmed: (optionalChecked: Boolean) -> Unit,
) {
    var optionalChecked by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                // AY -->
                text = if (isEpisode) {
                    stringResource(
                        AYMR.strings.episode_settings,
                    )
                } else {
                    stringResource(AYMR.strings.season_settings)
                },
                // <-- AY
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = stringResource(MR.strings.confirm_set_chapter_settings))

                LabeledCheckbox(
                    label = stringResource(MR.strings.also_set_chapter_settings_for_library),
                    checked = optionalChecked,
                    onCheckedChange = { optionalChecked = it },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirmed(optionalChecked)
                    onDismissRequest()
                },
            ) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
    )
}
