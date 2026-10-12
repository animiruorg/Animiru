// AM (RECENTS) -->
package eu.kanade.tachiyomi.ui.recents

import android.content.Context
import androidx.compose.animation.Crossfade
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import animiru.domain.player.service.PlayerPreferences
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.history.HistoryTopBar
import eu.kanade.presentation.updates.UpdatesBottomBar
import eu.kanade.presentation.updates.UpdatesTopBar
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.connection.discord.DiscordRPCService
import eu.kanade.tachiyomi.data.connection.discord.DiscordScreen
import eu.kanade.tachiyomi.ui.download.DownloadQueueScreen
import eu.kanade.tachiyomi.ui.history.HistoryHalfTab
import eu.kanade.tachiyomi.ui.history.HistoryViewModel
import eu.kanade.tachiyomi.ui.history.resumeLastEpisodeSeenEvent
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.updates.AnimeUpdatesHalfTab
import eu.kanade.tachiyomi.ui.updates.UpdatesSettingsViewModel
import eu.kanade.tachiyomi.ui.updates.UpdatesViewModel
import eu.kanade.tachiyomi.ui.updates.openEpisode
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import mihon.app.di.appGraph
import mihon.feature.upcoming.UpcomingScreen
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.History
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.episode.model.Episode
import tachiyomi.i18n.MR
import tachiyomi.i18n.animiru.AMMR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

data object RecentsTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_recents_enter)
            return TabOptions(
                index = 1u,
                title = stringResource(AMMR.strings.label_recent_recents),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {
        resumeLastEpisodeSeenEvent.send(Unit)
    }

    override suspend fun onReselectHold(navigator: Navigator) {
        navigator.push(DownloadQueueScreen)
    }

    private val switchToHistoryTabChannel = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)

    fun showHistory() {
        switchToHistoryTabChannel.trySend(Unit)
    }

    @Composable
    override fun Content() {
        val context = LocalContext.current

        val historyViewModel = metroViewModel<HistoryViewModel>()
        // AM (RECENTS_FILTER_CHIP) -->
        val updatesViewModel = metroViewModel<UpdatesViewModel>()
        val updatesSettingsViewModel = metroViewModel<UpdatesSettingsViewModel>()
        // AM (TAB_HOLD) -->
        val snackbarHostState = SnackbarHostState()
        // <-- AM (TAB_HOLD)
        var showHistoryScreen by remember { mutableStateOf(false) }

        RecentsScaffold(
            showHistoryScreen = showHistoryScreen,
            shouldShowHistoryScreen = { showHistoryScreen = it },
            updatesViewModel = updatesViewModel,
            historyViewModel = historyViewModel,
            snackbarHostState = snackbarHostState,
        ) { contentPadding ->
            Crossfade(targetState = showHistoryScreen, label = "recents_crossfade") { showHistory ->
                if (!showHistory) {
                    AnimeUpdatesHalfTab(updatesViewModel, updatesSettingsViewModel, contentPadding)
                } else {
                    HistoryHalfTab(historyViewModel, snackbarHostState, contentPadding)
                }
            }
        }

        LaunchedEffect(Unit) {
            switchToHistoryTabChannel.receiveAsFlow().collectLatest { showHistoryScreen = true }
        }
        // <-- AM (RECENTS_FILTER_CHIP)

        LaunchedEffect(Unit) {
            // AM (DISCORD_RPC) -->
            with(DiscordRPCService) {
                discordScope.launchIO { setScreen(context.applicationContext, DiscordScreen.RECENTS) }
            }
            // <-- AM (DISCORD_RPC)
            (context as? MainActivity)?.ready = true
            // AM (TAB_HOLD) -->
            resumeLastEpisodeSeenEvent.receiveAsFlow().collectLatest {
                openEpisode(context, historyViewModel.getNextEpisode(), snackbarHostState)
            }
        }
    }
}

internal suspend fun openEpisode(context: Context, episode: Episode?, snackbarHostState: SnackbarHostState) {
    val playerPreferences: PlayerPreferences = context.appGraph.playerPreferences
    val extPlayer = playerPreferences.alwaysUseExternalPlayer.get()
    if (episode != null) {
        MainActivity.startPlayerActivity(context, episode.animeId, episode.id, extPlayer)
    } else {
        snackbarHostState.showSnackbar(context.stringResource(AYMR.strings.no_next_episode))
    }
}
// <-- AM (TAB_HOLD)

// AM (RECENTS_FILTER_CHIP) -->
@Composable
fun RecentsScaffold(
    showHistoryScreen: Boolean,
    shouldShowHistoryScreen: (Boolean) -> Unit,
    snackbarHostState: SnackbarHostState,
    updatesViewModel: UpdatesViewModel,
    historyViewModel: HistoryViewModel,
    content: @Composable (PaddingValues) -> Unit,
) {
    val context = LocalContext.current
    val navigator = LocalNavigator.currentOrThrow
    val scope = rememberCoroutineScope()

    val updatesState by updatesViewModel.state.collectAsState()
    val historyState by historyViewModel.state.collectAsState()

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = { scrollBehavior ->
            Column {
                if (!showHistoryScreen) {
                    UpdatesTopBar(
                        onCalendarClicked = { navigator.push(UpcomingScreen()) },
                        onFilterClicked = updatesViewModel::showFilterDialog,
                        hasFilters = updatesState.hasActiveFilters,
                        onUpdateLibrary = updatesViewModel::updateLibrary,
                        actionModeCounter = updatesState.selected.size,
                        onSelectAll = { updatesViewModel.toggleAllSelection(true) },
                        onInvertSelection = updatesViewModel::invertSelection,
                        onCancelActionMode = { updatesViewModel.toggleAllSelection(false) },
                        scrollBehavior = scrollBehavior,
                    )
                } else {
                    HistoryTopBar(
                        state = historyState,
                        onSearchQueryChange = historyViewModel::updateSearchQuery,
                        onDialogChange = historyViewModel::setDialog,
                        scrollBehavior = scrollBehavior,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.padding.small),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    FilterChip(
                        selected = !showHistoryScreen,
                        onClick = { shouldShowHistoryScreen(false) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_updates_outline_24dp),
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize),
                            )
                        },
                        label = { Text(text = stringResource(MR.strings.label_recent_updates)) },
                    )

                    FilterChip(
                        selected = showHistoryScreen,
                        onClick = { shouldShowHistoryScreen(true) },
                        leadingIcon = {
                            Icon(
                                imageVector = MaterialSymbols.Rounded.History,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize),
                            )
                        },
                        label = { Text(text = stringResource(MR.strings.label_recent_manga)) },
                    )
                }
            }
        },
        bottomBar = {
            if (!showHistoryScreen) {
                UpdatesBottomBar(
                    selected = updatesState.selected,
                    onDownloadEpisode = updatesViewModel::downloadEpisodes,
                    onMultiBookmarkClicked = updatesViewModel::bookmarkUpdates,
                    onMultiFillermarkClicked = updatesViewModel::fillermarkUpdates,
                    onMultiMarkAsSeenClicked = updatesViewModel::markUpdatesSeen,
                    onMultiDeleteClicked = updatesViewModel::showConfirmDeleteEpisodes,
                    onOpenEpisode = { updateItem, altPlayer ->
                        scope.launchIO {
                            openEpisode(context, updateItem, altPlayer)
                        }
                    },
                )
            }
        },
    ) { contentPadding ->
        content(contentPadding)
    }
}
// <-- AM (RECENTS_FILTER_CHIP)
// <-- AM (RECENTS)
