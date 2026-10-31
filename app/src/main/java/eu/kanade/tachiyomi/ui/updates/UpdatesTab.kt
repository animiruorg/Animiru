package eu.kanade.tachiyomi.ui.updates

import android.content.Context
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import animiru.domain.player.service.PlayerPreferences
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.anime.EpisodeOptionsDialogScreen
import eu.kanade.presentation.components.NavigatorAdaptiveSheet
import eu.kanade.presentation.updates.UpdateScreen
import eu.kanade.presentation.updates.UpdatesDeleteConfirmationDialog
import eu.kanade.presentation.updates.UpdatesFilterDialog
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.connection.discord.DiscordRPCService
import eu.kanade.tachiyomi.data.connection.discord.DiscordScreen
import eu.kanade.tachiyomi.ui.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.download.DownloadQueueScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.updates.UpdatesViewModel.Event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import mihon.app.di.appGraph
import mihon.feature.upcoming.UpcomingScreen
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

data object UpdatesTab : Tab {

    // AM -->
    private val openFirstUnseenEvent = Channel<Unit>()
    // <-- AM

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.animated_updates)
            return TabOptions(
                index = 1u,
                title = stringResource(MR.strings.label_recent_updates),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {
        navigator.push(DownloadQueueScreen)
    }

    // AM -->
    override suspend fun onReselectHold(navigator: Navigator) {
        openFirstUnseenEvent.send(Unit)
    }
    // <-- AM

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = metroViewModel<UpdatesViewModel>()
        val settingsViewModel = metroViewModel<UpdatesSettingsViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()
        // AM -->
        val scope = rememberCoroutineScope()
        // <-- AM

        UpdateScreen(
            state = state,
            snackbarHostState = viewModel.snackbarHostState,
            lastUpdated = viewModel.lastUpdated,
            onClickCover = { item -> navigator.push(AnimeScreen(item.update.animeId)) },
            onSelectAll = viewModel::toggleAllSelection,
            onInvertSelection = viewModel::invertSelection,
            onUpdateLibrary = viewModel::updateLibrary,
            onDownloadEpisode = viewModel::downloadEpisodes,
            onMultiBookmarkClicked = viewModel::bookmarkUpdates,
            // AM -->
            onMultiFillermarkClicked = viewModel::fillermarkUpdates,
            // <-- AM
            onMultiMarkAsSeenClicked = viewModel::markUpdatesSeen,
            onMultiDeleteClicked = viewModel::showConfirmDeleteEpisodes,
            onUpdateSelected = viewModel::toggleSelection,
            onOpenEpisode = { updateItem: UpdatesItem, altPlayer: Boolean ->
                scope.launch(Dispatchers.IO) {
                    openEpisode(context, updateItem, altPlayer)
                }
            },
            onCalendarClicked = { navigator.push(UpcomingScreen()) },
            onFilterClicked = viewModel::showFilterDialog,
            hasActiveFilters = state.hasActiveFilters,
        )

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
            // AM -->
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
            // <-- AM
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

        LaunchedEffect(Unit) {
            // AM (DISCORD_RPC) -->
            with(DiscordRPCService) {
                discordScope.launch(Dispatchers.IO) { setScreen(context.applicationContext, DiscordScreen.UPDATES) }
            }
            // <-- AM (DISCORD_RPC)
        }

        // AM -->
        LaunchedEffect(Unit) {
            openFirstUnseenEvent.receiveAsFlow().collectLatest {
                viewModel.getFirstUnseen()?.let {
                    openEpisode(context, it)
                }
            }
        }
        // <-- AM

        DisposableEffect(Unit) {
            viewModel.resetNewUpdatesCount()

            onDispose {
                viewModel.resetNewUpdatesCount()
            }
        }
    }

    private suspend fun openEpisode(context: Context, updateItem: UpdatesItem, altPlayer: Boolean = false) {
        val playerPreferences: PlayerPreferences = context.appGraph.playerPreferences
        val update = updateItem.update
        val extPlayer = playerPreferences.alwaysUseExternalPlayer.get() != altPlayer
        MainActivity.startPlayerActivity(context, update.animeId, update.episodeId, extPlayer, update.sourceId)
    }
}
