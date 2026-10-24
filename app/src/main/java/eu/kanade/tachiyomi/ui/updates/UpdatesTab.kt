package eu.kanade.tachiyomi.ui.updates

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import animiru.domain.player.service.PlayerPreferences
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.anime.EpisodeOptionsDialogScreen
import eu.kanade.presentation.components.NavigatorAdaptiveSheet
import eu.kanade.presentation.updates.UpdateScreen
import eu.kanade.presentation.updates.UpdatesDeleteConfirmationDialog
import eu.kanade.presentation.updates.UpdatesFilterDialog
import eu.kanade.tachiyomi.ui.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.updates.UpdatesViewModel.Event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import mihon.app.di.appGraph
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR

// AM (RECENTS_FILTER_CHIP) -->
@Composable
fun AnimeUpdatesHalfTab(
    viewModel: UpdatesViewModel,
    settingsViewModel: UpdatesSettingsViewModel,
    contentPadding: PaddingValues,
) {
    val context = LocalContext.current
    val navigator = LocalNavigator.currentOrThrow
    val scope = rememberCoroutineScope()
    val state by viewModel.state.collectAsStateWithLifecycle()

    UpdateScreen(
        state = state,
        lastUpdated = viewModel.lastUpdated,
        onClickCover = { item -> navigator.push(AnimeScreen(item.update.animeId)) },
        onUpdateLibrary = viewModel::updateLibrary,
        onDownloadEpisode = viewModel::downloadEpisodes,
        onUpdateSelected = viewModel::toggleSelection,
        onOpenEpisode = { updateItem: UpdatesItem, altPlayer: Boolean ->
            scope.launch(Dispatchers.IO) {
                openEpisode(context, updateItem, altPlayer)
            }
            Unit
        },
        contentPadding = contentPadding,
        hasActiveFilters = state.hasActiveFilters,
    )
    // <-- AM (RECENTS_FILTER_CHIP)

    val onDismissDialog = { viewModel.setDialog(null) }
    when (val dialog = state.dialog) {
        is UpdatesViewModel.Dialog.DeleteConfirmation -> {
            UpdatesDeleteConfirmationDialog(
                onDismissRequest = onDismissDialog,
                onConfirm = { viewModel.deleteEpisodes(dialog.toDelete) },
            )
        }
        is UpdatesViewModel.Dialog.FilterSheet -> {
            UpdatesFilterDialog(
                onDismissRequest = onDismissDialog,
                viewModel = settingsViewModel,
            )
        }
        is UpdatesViewModel.Dialog.ShowQualities -> {
            EpisodeOptionsDialogScreen.onDismissDialog = onDismissDialog
            NavigatorAdaptiveSheet(
                screen = EpisodeOptionsDialogScreen(
                    useExternalDownloader = viewModel.useExternalDownloader,
                    episodeTitle = dialog.episodeTitle,
                    episodeId = dialog.episodeId,
                    animeId = dialog.animeId,
                    sourceId = dialog.sourceId,
                ),
                onDismissRequest = onDismissDialog,
            )
        }
        null -> {}
    }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                Event.InternalError -> viewModel.snackbarHostState.showSnackbar(
                    context.stringResource(MR.strings.internal_error),
                )
                is Event.LibraryUpdateTriggered -> {
                    val msg = if (event.started) {
                        MR.strings.updating_library
                    } else {
                        MR.strings.update_already_running
                    }
                    viewModel.snackbarHostState.showSnackbar(context.stringResource(msg))
                }
            }
        }
    }

    LaunchedEffect(state.selectionMode) {
        HomeScreen.showBottomNav(!state.selectionMode)
    }

    LaunchedEffect(state.isLoading) {
        if (!state.isLoading) {
            (context as? MainActivity)?.ready = true
        }
    }
    DisposableEffect(Unit) {
        viewModel.resetNewUpdatesCount()

        onDispose {
            viewModel.resetNewUpdatesCount()
        }
    }
}

suspend fun openEpisode(context: Context, updateItem: UpdatesItem, altPlayer: Boolean = false) {
    val playerPreferences: PlayerPreferences = context.appGraph.playerPreferences
    val update = updateItem.update
    val extPlayer = playerPreferences.alwaysUseExternalPlayer.get() != altPlayer
    MainActivity.startPlayerActivity(context, update.animeId, update.episodeId, extPlayer, update.sourceId)
}
