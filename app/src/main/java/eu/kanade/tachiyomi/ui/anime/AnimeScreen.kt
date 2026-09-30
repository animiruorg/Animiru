package eu.kanade.tachiyomi.ui.anime

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.core.util.ifSourcesLoaded
import eu.kanade.domain.anime.model.hasCustomBackground
import eu.kanade.domain.anime.model.hasCustomCover
import eu.kanade.domain.anime.model.toSAnime
import eu.kanade.presentation.anime.AnimeScreen
import eu.kanade.presentation.anime.DuplicateAnimeDialog
import eu.kanade.presentation.anime.EditCoverAction
import eu.kanade.presentation.anime.EpisodeOptionsDialogScreen
import eu.kanade.presentation.anime.EpisodeSettingsDialog
import eu.kanade.presentation.anime.SeasonSettingsDialog
import eu.kanade.presentation.anime.components.AnimeImagesDialog
import eu.kanade.presentation.anime.components.DeleteEpisodesDialog
import eu.kanade.presentation.anime.components.ScanlatorFilterDialog
import eu.kanade.presentation.anime.components.SetIntervalDialog
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.components.NavigatorAdaptiveSheet
import eu.kanade.presentation.more.settings.screen.player.PlayerSettingsGesturesScreen.SkipIntroLengthDialog
import eu.kanade.presentation.util.AssistContentScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.formatEpisodeNumber
import eu.kanade.presentation.util.isTabletUi
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.data.torrent.service.TorrentServerService
import eu.kanade.tachiyomi.source.isLocalOrStub
import eu.kanade.tachiyomi.source.isSourceForTorrents
import eu.kanade.tachiyomi.ui.anime.notes.AnimeNotesScreen
import eu.kanade.tachiyomi.ui.anime.track.TrackInfoDialogHomeScreen
import eu.kanade.tachiyomi.ui.browse.extension.details.SourcePreferencesScreen
import eu.kanade.tachiyomi.ui.browse.migration.season.MigrateSeasonSelectScreen
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.setting.SettingsScreen
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.toShareIntent
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.feature.migration.config.MigrationConfigScreen
import mihon.feature.migration.dialog.MigrateAnimeDialog
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.episode.model.Episode
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

class AnimeScreen(
    private val animeId: Long,
    val fromSource: Boolean = false,
) : Screen(), AssistContentScreen {

    private var assistUrl: String? = null

    override fun onProvideAssistUrl() = assistUrl

    @Composable
    override fun Content() {
        if (!ifSourcesLoaded()) {
            LoadingScreen()
            return
        }

        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val haptic = LocalHapticFeedback.current
        val scope = rememberCoroutineScope()
        val viewModel = assistedMetroViewModel<AnimeViewModel, AnimeViewModel.Factory> {
            create(animeId = animeId, isFromSource = fromSource)
        }

        val state by viewModel.state.collectAsStateWithLifecycle()

        if (state is AnimeViewModel.State.Loading) {
            LoadingScreen()
            return
        }

        val successState = state as AnimeViewModel.State.Success
        val isHttpSource = remember { successState.source is AnimeHttpSource }
        // AY -->
        val isConfigurableSource = remember { successState.source is ConfigurableAnimeSource }
        // <-- AY

        LaunchedEffect(successState.anime, viewModel.source) {
            if (isHttpSource) {
                try {
                    withIOContext {
                        assistUrl = getAnimeUrl(viewModel.anime, viewModel.source)
                    }
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Failed to get anime URL" }
                }
            }
        }

        AnimeScreen(
            state = successState,
            snackbarHostState = viewModel.snackbarHostState,
            nextUpdate = successState.anime.expectedNextUpdate,
            isTabletUi = isTabletUi(),
            episodeSwipeStartAction = viewModel.episodeSwipeStartAction,
            episodeSwipeEndAction = viewModel.episodeSwipeEndAction,
            // AY -->
            showNextEpisodeAirTime = viewModel.showNextEpisodeAirTime,
            alwaysUseExternalPlayer = viewModel.alwaysUseExternalPlayer,
            // <-- AY
            navigateUp = navigator::pop,
            // AM -->
            longNavigateUp = {
                val parentId = successState.anime.parentId
                if (parentId != null) {
                    navigator.replace(AnimeScreen(parentId))
                } else {
                    navigator.pop()
                }
            },
            // <-- AM
            // AM (FILE_SIZE) -->
            showFileSize = viewModel.showFileSize,
            // <-- AM (FILE_SIZE)
            onEpisodeClicked = { episode, /* AY --> */ alt /* <-- AY */ ->
                // AY -->
                scope.launchIO {
                    if (viewModel.isTorrentEnabled() && successState.source.isSourceForTorrents()) {
                        TorrentServerService.start(context)
                    }
                    val extPlayer = viewModel.alwaysUseExternalPlayer != alt
                    openEpisode(context, episode, extPlayer)
                }
                // <-- AY
            },
            onDownloadEpisode = viewModel::runEpisodeDownloadActions.takeIf {
                // AY -->
                !successState.source.isLocalOrStub() && successState.anime.fetchType == FetchType.Episodes
                // <-- AY
            },
            onAddToLibraryClicked = {
                viewModel.toggleFavorite()
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            onWebViewClicked = {
                openAnimeInWebView(
                    navigator,
                    viewModel.anime,
                    viewModel.source,
                )
            }.takeIf { isHttpSource },
            onWebViewLongClicked = {
                copyAnimeUrl(
                    context,
                    viewModel.anime,
                    viewModel.source,
                )
            }.takeIf { isHttpSource },
            onTrackingClicked = {
                if (!successState.hasLoggedInTrackers) {
                    navigator.push(SettingsScreen(SettingsScreen.Destination.Tracking))
                } else {
                    viewModel.showTrackDialog()
                }
            },
            onTagSearch = { scope.launch { performGenreSearch(navigator, it, viewModel.source!!) } },
            onFilterButtonClicked = viewModel::showSettingsDialog,
            onRefresh = viewModel::fetchAllFromSource,
            onContinueWatching = {
                // AY -->
                scope.launchIO {
                    val extPlayer = viewModel.alwaysUseExternalPlayer
                    continueWatching(context, viewModel.getNextUnseenEpisode(), extPlayer)
                }
                // <-- AY
            },
            onSearch = { query, global -> scope.launch { performSearch(navigator, query, global) } },
            onCoverClicked = viewModel::showImagesDialog,
            onShareClicked = { shareAnime(context, viewModel.anime, viewModel.source) }.takeIf { isHttpSource },
            onDownloadActionClicked = viewModel::runDownloadAction.takeIf {
                // AY -->
                !successState.source.isLocalOrStub() && successState.anime.fetchType == FetchType.Episodes
                // <-- AY
            },
            onEditCategoryClicked = viewModel::showChangeCategoryDialog.takeIf { successState.anime.favorite },
            onEditFetchIntervalClicked = viewModel::showSetFetchIntervalDialog.takeIf {
                successState.anime.favorite
            },
            onMigrateClicked = {
                navigator.push(MigrationConfigScreen(successState.anime.id))
            }.takeIf { successState.anime.favorite },
            // AY -->
            onSettingsClicked = {
                navigator.push(SourcePreferencesScreen(successState.source.id))
            }.takeIf { isConfigurableSource },
            onSkipIntroClicked = viewModel::showAnimeSkipIntroDialog.takeIf {
                // AY -->
                successState.anime.favorite && successState.anime.fetchType == FetchType.Episodes
                // <-- AY
            },
            // <-- AY
            // AM (CUSTOM_INFORMATION) -->
            onEditInfoClicked = viewModel::showEditAnimeInfoDialog,
            // <-- AM (CUSTOM_INFORMATION)
            onMultiBookmarkClicked = viewModel::bookmarkEpisodes,
            // AY -->
            onMultiFillermarkClicked = viewModel::fillermarkEpisodes,
            // <-- AY
            onEditNotesClicked = { navigator.push(AnimeNotesScreen(anime = successState.anime)) },
            onMultiMarkAsSeenClicked = viewModel::markEpisodesSeen,
            onMarkPreviousAsSeenClicked = viewModel::markPreviousEpisodeSeen,
            onMultiDeleteClicked = viewModel::showDeleteEpisodeDialog,
            onEpisodeSwipe = viewModel::episodeSwipe,
            onEpisodeSelected = viewModel::toggleSelection,
            onAllEpisodeSelected = viewModel::toggleAllSelection,
            onInvertSelection = viewModel::invertSelection,
            // AY -->
            onSeasonClicked = {
                navigator.push(AnimeScreen(it.id))
            },
            onContinueWatchingClicked = {
                scope.launchIO {
                    val episode = viewModel.getNextUnseenEpisode(it.anime)
                    episode?.let { ep ->
                        openEpisode(context, ep, viewModel.alwaysUseExternalPlayer)
                    }
                }
            },
            onRelatedAnimeClicked = { relatedAnime ->
                navigator.push(AnimeScreen(relatedAnime.id, fromSource = !relatedAnime.favorite))
            },
            onRelatedAnimeLongClicked = { relatedAnime ->
                navigator.push(GlobalSearchScreen(relatedAnime.title))
            },
            relatedAnimeDisplayMode = viewModel.relatedAnimeDisplayMode,
            // <-- AY
        )

        var showScanlatorsDialog by remember { mutableStateOf(false) }

        val onDismissRequest = {
            viewModel.dismissDialog()
            // AY -->
            if (viewModel.autoOpenTrack && viewModel.isFromChangeCategory &&
                successState.anime.fetchType == FetchType.Episodes
            ) {
                viewModel.isFromChangeCategory = false
                viewModel.showTrackDialog()
            }
            // <-- AY
        }
        when (val dialog = successState.dialog) {
            null -> {}
            is AnimeViewModel.Dialog.ChangeCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onEditCategories = { navigator.push(CategoryScreen()) },
                    onConfirm = { include, _ ->
                        viewModel.moveAnimeToCategoriesAndAddToLibrary(dialog.anime, include)
                    },
                )
            }
            is AnimeViewModel.Dialog.DeleteEpisodes -> {
                DeleteEpisodesDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = {
                        viewModel.toggleAllSelection(false)
                        viewModel.deleteEpisodes(dialog.episodes)
                    },
                )
            }

            is AnimeViewModel.Dialog.DuplicateAnime -> {
                DuplicateAnimeDialog(
                    duplicates = dialog.duplicates,
                    onDismissRequest = onDismissRequest,
                    onConfirm = { viewModel.toggleFavorite(onRemoved = {}, checkDuplicate = false) },
                    onOpenAnime = { navigator.push(AnimeScreen(it.id)) },
                    onMigrate = { viewModel.showMigrateDialog(it) },
                )
            }

            is AnimeViewModel.Dialog.Migrate -> {
                MigrateAnimeDialog(
                    current = dialog.current,
                    target = dialog.target,
                    // Initiated from the context of [dialog.target] so we show [dialog.current].
                    onClickTitle = { navigator.push(AnimeScreen(dialog.current.id)) },
                    // AY -->
                    onClickSeasons = { navigator.push(MigrateSeasonSelectScreen(dialog.current, dialog.target)) },
                    // <-- AY
                    onDismissRequest = onDismissRequest,
                )
            }
            AnimeViewModel.Dialog.EpisodeSettingsSheet -> EpisodeSettingsDialog(
                onDismissRequest = onDismissRequest,
                anime = successState.anime,
                onDownloadFilterChanged = viewModel::setDownloadedFilter,
                onUnseenFilterChanged = viewModel::setUnseenFilter,
                onBookmarkedFilterChanged = viewModel::setBookmarkedFilter,
                // AY -->
                onFillermarkedFilterChanged = viewModel::setFillermarkedFilter,
                // <-- AY
                onSortModeChanged = viewModel::setSorting,
                onDisplayModeChanged = viewModel::setDisplayMode,
                // AY -->
                onShowPreviewsEnabled = viewModel::showEpisodePreviews,
                onShowSummariesEnabled = viewModel::showEpisodeSummaries,
                // <-- AY
                // AM -->
                onSetThumbnailSize = viewModel::setEpisodeThumbnailSize,
                // <-- AM
                onSetAsDefault = viewModel::setCurrentSettingsAsDefault,
                onResetToDefault = viewModel::resetToDefaultSettings,
                scanlatorFilterActive = successState.scanlatorFilterActive,
                onScanlatorFilterClicked = { showScanlatorsDialog = true },
            )
            // AY -->
            AnimeViewModel.Dialog.SeasonSettingsSheet -> SeasonSettingsDialog(
                onDismissRequest = onDismissRequest,
                anime = successState.anime,
                onDownloadFilterChanged = viewModel::setSeasonDownloadedFilter,
                onUnseenFilterChanged = viewModel::setSeasonUnseenFilter,
                onStartedFilterChanged = viewModel::setSeasonStartedFilter,
                onCompletedFilterChanged = viewModel::setSeasonCompletedFilter,
                onBookmarkedFilterChanged = viewModel::setSeasonBookmarkedFilter,
                onFillermarkedFilterChanged = viewModel::setSeasonFillermarkedFilter,
                onSortModeChanged = viewModel::setSeasonSorting,
                onDisplayGridModeChanged = viewModel::setSeasonDisplayGridMode,
                onDisplayGridSizeChanged = viewModel::setSeasonDisplayGridSize,
                onOverlayDownloadedChanged = viewModel::setSeasonDownloadOverlay,
                onOverlayUnseenChanged = viewModel::setSeasonUnseenOverlay,
                onOverlayLocalChanged = viewModel::setSeasonLocalOverlay,
                onOverlayLangChanged = viewModel::setSeasonLangOverlay,
                onOverlayContinueChanged = viewModel::setSeasonContinueOverlay,
                onDisplayModeChanged = viewModel::setSeasonDisplayMode,
                onSetAsDefault = viewModel::setSeasonCurrentSettingsAsDefault,
            )
            // <-- AY
            AnimeViewModel.Dialog.TrackSheet -> {
                NavigatorAdaptiveSheet(
                    screen = TrackInfoDialogHomeScreen(
                        animeId = successState.anime.id,
                        animeTitle = successState.anime.title,
                        // AM -->
                        isSeason = successState.anime.fetchType == FetchType.Seasons,
                        // <-- AM
                        sourceId = successState.source.id,
                    ),
                    enableSwipeDismiss = { it.lastItem is TrackInfoDialogHomeScreen },
                    onDismissRequest = onDismissRequest,
                )
            }
            AnimeViewModel.Dialog.FullImages -> {
                val vm = assistedMetroViewModel<AnimeImageViewModel, AnimeImageViewModel.Factory> {
                    create(animeId = animeId)
                }
                val anime by vm.state.collectAsStateWithLifecycle()
                if (anime != null) {
                    val getContent = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
                        if (it == null) return@rememberLauncherForActivityResult
                        vm.editImage(context, it)
                    }
                    AnimeImagesDialog(
                        anime = anime!!,
                        snackbarHostState = vm.snackbarHostState,
                        // AY -->
                        pagerState = vm.pagerState,
                        // <-- AY
                        isCustomCover = remember(anime) { anime!!.hasCustomCover() },
                        // AY -->
                        isCustomBackground = remember(anime) { anime!!.hasCustomBackground() },
                        // <-- AY
                        onShareClick = { vm.shareImage(context) },
                        onSaveClick = { vm.saveImage(context) },
                        onEditClick = {
                            when (it) {
                                EditCoverAction.EDIT -> getContent.launch("image/*")
                                EditCoverAction.DELETE -> vm.deleteCustomImage(context)
                            }
                        },
                        onDismissRequest = onDismissRequest,
                    )
                } else {
                    LoadingScreen(Modifier.systemBarsPadding())
                }
            }
            is AnimeViewModel.Dialog.SetFetchInterval -> {
                SetIntervalDialog(
                    interval = dialog.anime.fetchInterval,
                    nextUpdate = dialog.anime.expectedNextUpdate,
                    onDismissRequest = onDismissRequest,
                    onValueChanged = { interval: Int -> viewModel.setFetchInterval(dialog.anime, interval) }
                        .takeIf { viewModel.isUpdateIntervalEnabled },
                )
            }
            // AY -->
            AnimeViewModel.Dialog.ChangeAnimeSkipIntro -> {
                fun updateSkipIntroLength(newLength: Long) {
                    scope.launchIO {
                        viewModel.setAnimeViewerFlags.awaitSetSkipIntroLength(animeId, newLength)
                    }
                }
                SkipIntroLengthDialog(
                    initialSkipIntroLength = if (!successState.anime.skipIntroDisable &&
                        successState.anime.skipIntroLength == 0
                    ) {
                        viewModel.gesturePreferences.defaultIntroLength.get()
                    } else {
                        successState.anime.skipIntroLength
                    },
                    onDismissRequest = onDismissRequest,
                    onValueChanged = {
                        updateSkipIntroLength(it.toLong())
                        onDismissRequest()
                    },
                )
            }
            is AnimeViewModel.Dialog.ShowQualities -> {
                EpisodeOptionsDialogScreen.onDismissDialog = onDismissRequest
                val episodeTitle = if (dialog.anime.displayMode == Anime.EPISODE_DISPLAY_NUMBER) {
                    stringResource(
                        AYMR.strings.display_mode_episode,
                        formatEpisodeNumber(dialog.episode.episodeNumber),
                    )
                } else {
                    dialog.episode.name
                }
                NavigatorAdaptiveSheet(
                    screen = EpisodeOptionsDialogScreen(
                        useExternalDownloader = viewModel.useExternalDownloader,
                        episodeTitle = episodeTitle,
                        episodeId = dialog.episode.id,
                        animeId = dialog.anime.id,
                        sourceId = dialog.source.id,
                    ),
                    onDismissRequest = onDismissRequest,
                )
            }
            // <-- AY
            // AM (CUSTOM_INFORMATION) -->
            is AnimeViewModel.Dialog.EditAnimeInfo -> {
                EditAnimeDialog(
                    anime = successState.anime,
                    onDismissRequest = viewModel::dismissDialog,
                    onPositiveClick = viewModel::updateAnimeInfo,
                )
            }
            // <-- AM (CUSTOM_INFORMATION)
        }

        if (showScanlatorsDialog) {
            ScanlatorFilterDialog(
                availableScanlators = successState.availableScanlators,
                excludedScanlators = successState.excludedScanlators,
                onDismissRequest = { showScanlatorsDialog = false },
                onConfirm = viewModel::setExcludedScanlators,
            )
        }
    }

    private suspend fun continueWatching(context: Context, unseenEpisode: Episode?, useExternalPlayer: Boolean) {
        if (unseenEpisode != null) openEpisode(context, unseenEpisode, useExternalPlayer)
    }

    private suspend fun openEpisode(context: Context, episode: Episode, useExternalPlayer: Boolean) {
        // AY -->
        withIOContext {
            MainActivity.startPlayerActivity(
                context,
                episode.animeId,
                episode.id,
                useExternalPlayer,
            )
        }
        // <-- AY
    }

    private fun getAnimeUrl(anime_: Anime?, source_: AnimeSource?): String? {
        val anime = anime_ ?: return null
        val source = source_ as? AnimeHttpSource ?: return null

        return try {
            source.getAnimeUrl(anime.toSAnime())
        } catch (e: Exception) {
            null
        }
    }

    private fun openAnimeInWebView(navigator: Navigator, anime_: Anime?, source_: AnimeSource?) {
        getAnimeUrl(anime_, source_)?.let { url ->
            navigator.push(
                WebViewScreen(
                    url = url,
                    initialTitle = anime_?.title,
                    sourceId = source_?.id,
                ),
            )
        }
    }

    private fun shareAnime(context: Context, anime_: Anime?, source_: AnimeSource?) {
        try {
            getAnimeUrl(anime_, source_)?.let { url ->
                val intent = url.toUri().toShareIntent(context, type = "text/plain")
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            context.toast(e.message)
        }
    }

    /**
     * Perform a search using the provided query.
     *
     * @param query the search query to the parent controller
     */
    private suspend fun performSearch(navigator: Navigator, query: String, global: Boolean) {
        if (global) {
            navigator.push(GlobalSearchScreen(query))
            return
        }

        if (navigator.size < 2) {
            return
        }

        when (val previousController = navigator.items[navigator.size - 2]) {
            is HomeScreen -> {
                navigator.pop()
                previousController.search(query)
            }
            is BrowseSourceScreen -> {
                navigator.pop()
                previousController.search(query)
            }
        }
    }

    /**
     * Performs a genre search using the provided genre name.
     *
     * @param genreName the search genre to the parent controller
     */
    private suspend fun performGenreSearch(navigator: Navigator, genreName: String, source: AnimeSource) {
        if (navigator.size < 2) {
            return
        }

        val previousController = navigator.items[navigator.size - 2]
        if (previousController is BrowseSourceScreen && source is AnimeHttpSource) {
            navigator.pop()
            previousController.searchGenre(genreName)
        } else {
            performSearch(navigator, genreName, global = false)
        }
    }

    /**
     * Copy Anime URL to Clipboard
     */
    private fun copyAnimeUrl(context: Context, anime_: Anime?, source_: AnimeSource?) {
        val anime = anime_ ?: return
        val source = source_ as? AnimeHttpSource ?: return
        val url = source.getAnimeUrl(anime.toSAnime())
        context.copyToClipboard(url, url)
    }
}
