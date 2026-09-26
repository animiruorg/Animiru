package eu.kanade.presentation.anime

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastMap
import aniyomi.domain.anime.model.SeasonAnime
import aniyomi.domain.anime.model.SeasonDisplayMode
import eu.kanade.presentation.anime.components.AnimeActionRow
import eu.kanade.presentation.anime.components.AnimeBottomActionMenu
import eu.kanade.presentation.anime.components.AnimeEpisodeListItem
import eu.kanade.presentation.anime.components.AnimeInfoBox
import eu.kanade.presentation.anime.components.AnimeSeasonListItem
import eu.kanade.presentation.anime.components.AnimeToolbar
import eu.kanade.presentation.anime.components.EpisodeDownloadAction
import eu.kanade.presentation.anime.components.ExpandableAnimeDescription
import eu.kanade.presentation.anime.components.ItemHeader
import eu.kanade.presentation.anime.components.MissingEpisodeCountListItem
import eu.kanade.presentation.anime.components.NextEpisodeAiringListItem
import eu.kanade.presentation.components.relativeDateText
import eu.kanade.presentation.util.formatEpisodeNumber
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.source.getNameForAnimeInfo
import eu.kanade.tachiyomi.ui.anime.AnimeSeasonItem
import eu.kanade.tachiyomi.ui.anime.AnimeViewModel
import eu.kanade.tachiyomi.ui.anime.EpisodeList
import eu.kanade.tachiyomi.util.system.copyToClipboard
import kotlinx.coroutines.delay
import mihon.app.di.appGraph
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.episode.service.missingEntriesCount
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.model.StubSource
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.FastScrollIrregularLazyVerticalGrid
import tachiyomi.presentation.core.components.Scroller.EXACT_HEIGHT_KEY_PREFIX
import tachiyomi.presentation.core.components.TwoPanelBox
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.shouldExpandFAB
import tachiyomi.source.local.isLocal
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Composable
fun AnimeScreen(
    state: AnimeViewModel.State.Success,
    snackbarHostState: SnackbarHostState,
    nextUpdate: Instant?,
    isTabletUi: Boolean,
    episodeSwipeStartAction: LibraryPreferences.EpisodeSwipeAction,
    episodeSwipeEndAction: LibraryPreferences.EpisodeSwipeAction,
    // AY -->
    showNextEpisodeAirTime: Boolean,
    alwaysUseExternalPlayer: Boolean,
    // <-- AY
    navigateUp: () -> Unit,
    // AM -->
    longNavigateUp: () -> Unit,
    // <-- AM
    // AM (FILE_SIZE) -->
    showFileSize: Boolean,
    // <-- AM (FILE_SIZE)
    // AY -->
    onEpisodeClicked: (episode: Episode, alt: Boolean) -> Unit,
    // <-- AY
    onDownloadEpisode: ((List<EpisodeList.Item>, EpisodeDownloadAction) -> Unit)?,
    onAddToLibraryClicked: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onWebViewLongClicked: (() -> Unit)?,
    // AY -->
    onTrackingClicked: (() -> Unit)?,
    // <-- AY

    // For tags menu
    onTagSearch: (String) -> Unit,

    onFilterButtonClicked: () -> Unit,
    onRefresh: () -> Unit,
    onContinueWatching: () -> Unit,
    onSearch: (query: String, global: Boolean) -> Unit,

    // For cover dialog
    onCoverClicked: () -> Unit,

    // For top action menu
    onShareClicked: (() -> Unit)?,
    onDownloadActionClicked: ((DownloadAction) -> Unit)?,
    onEditCategoryClicked: (() -> Unit)?,
    onEditFetchIntervalClicked: (() -> Unit)?,
    onMigrateClicked: (() -> Unit)?,
    // AY -->
    onSettingsClicked: (() -> Unit)?,
    onSkipIntroClicked: (() -> Unit)?,
    // <-- AY
    // AM (CUSTOM_INFORMATION) -->
    onEditInfoClicked: () -> Unit,
    // <-- AM (CUSTOM_INFORMATION)
    onEditNotesClicked: () -> Unit,

    // For bottom action menu
    onMultiBookmarkClicked: (List<Episode>, bookmarked: Boolean) -> Unit,
    // AY -->
    onMultiFillermarkClicked: (List<Episode>, fillermarked: Boolean) -> Unit,
    // <-- AY
    onMultiMarkAsSeenClicked: (List<Episode>, markAsSeen: Boolean) -> Unit,
    onMarkPreviousAsSeenClicked: (Episode) -> Unit,
    onMultiDeleteClicked: (List<Episode>) -> Unit,

    // For episode swipe
    onEpisodeSwipe: (EpisodeList.Item, LibraryPreferences.EpisodeSwipeAction) -> Unit,

    // Episode selection
    onEpisodeSelected: (EpisodeList.Item, Boolean, Boolean) -> Unit,
    onAllEpisodeSelected: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,

    // AY -->
    // Season clicked
    onSeasonClicked: (SeasonAnime) -> Unit,
    onContinueWatchingClicked: ((SeasonAnime) -> Unit)?,

    // Related anime clicked
    onRelatedAnimeClicked: (Anime) -> Unit,
    onRelatedAnimeLongClicked: (Anime) -> Unit,
    relatedAnimeDisplayMode: LibraryDisplayMode,
    // <-- AY
) {
    val context = LocalContext.current
    val onCopyTagToClipboard: (tag: String) -> Unit = {
        if (it.isNotEmpty()) {
            context.copyToClipboard(it, it)
        }
    }

    if (!isTabletUi) {
        AnimeScreenSmallImpl(
            state = state,
            snackbarHostState = snackbarHostState,
            nextUpdate = nextUpdate,
            episodeSwipeStartAction = episodeSwipeStartAction,
            episodeSwipeEndAction = episodeSwipeEndAction,
            // AY -->
            showNextEpisodeAirTime = showNextEpisodeAirTime,
            alwaysUseExternalPlayer = alwaysUseExternalPlayer,
            // <-- AY
            navigateUp = navigateUp,
            // AM -->
            longNavigateUp = longNavigateUp,
            // <-- AM
            // AM (FILE_SIZE) -->
            showFileSize = showFileSize,
            // <-- AM (FILE_SIZE)
            onEpisodeClicked = onEpisodeClicked,
            onDownloadEpisode = onDownloadEpisode,
            onAddToLibraryClicked = onAddToLibraryClicked,
            onWebViewClicked = onWebViewClicked,
            onWebViewLongClicked = onWebViewLongClicked,
            onTrackingClicked = onTrackingClicked,
            onTagSearch = onTagSearch,
            onCopyTagToClipboard = onCopyTagToClipboard,
            onFilterClicked = onFilterButtonClicked,
            onRefresh = onRefresh,
            onContinueWatching = onContinueWatching,
            onSearch = onSearch,
            onCoverClicked = onCoverClicked,
            onShareClicked = onShareClicked,
            onDownloadActionClicked = onDownloadActionClicked,
            onEditCategoryClicked = onEditCategoryClicked,
            onEditIntervalClicked = onEditFetchIntervalClicked,
            onMigrateClicked = onMigrateClicked,
            // AY -->
            onSettingsClicked = onSettingsClicked,
            onSkipIntroClicked = onSkipIntroClicked,
            // <-- AY
            // AM (CUSTOM_INFORMATION) -->
            onEditInfoClicked = onEditInfoClicked,
            // <-- AM (CUSTOM_INFORMATION)
            onEditNotesClicked = onEditNotesClicked,
            onMultiBookmarkClicked = onMultiBookmarkClicked,
            // AY -->
            onMultiFillermarkClicked = onMultiFillermarkClicked,
            // <-- AY
            onMultiMarkAsSeenClicked = onMultiMarkAsSeenClicked,
            onMarkPreviousAsSeenClicked = onMarkPreviousAsSeenClicked,
            onMultiDeleteClicked = onMultiDeleteClicked,
            onEpisodeSwipe = onEpisodeSwipe,
            onEpisodeSelected = onEpisodeSelected,
            onAllEpisodeSelected = onAllEpisodeSelected,
            onInvertSelection = onInvertSelection,
            // AY -->
            onSeasonClicked = onSeasonClicked,
            onClickContinueWatching = onContinueWatchingClicked,
            onRelatedAnimeClicked = onRelatedAnimeClicked,
            onRelatedAnimeLongClicked = onRelatedAnimeLongClicked,
            relatedAnimeDisplayMode = relatedAnimeDisplayMode,
            // <-- AY
        )
    } else {
        AnimeScreenLargeImpl(
            state = state,
            snackbarHostState = snackbarHostState,
            nextUpdate = nextUpdate,
            episodeSwipeStartAction = episodeSwipeStartAction,
            episodeSwipeEndAction = episodeSwipeEndAction,
            // AY -->
            showNextEpisodeAirTime = showNextEpisodeAirTime,
            alwaysUseExternalPlayer = alwaysUseExternalPlayer,
            // <-- AY
            navigateUp = navigateUp,
            // AM -->
            longNavigateUp = longNavigateUp,
            // <-- AM
            // AM (FILE_SIZE) -->
            showFileSize = showFileSize,
            // <-- AM (FILE_SIZE)
            onEpisodeClicked = onEpisodeClicked,
            onDownloadEpisode = onDownloadEpisode,
            onAddToLibraryClicked = onAddToLibraryClicked,
            onWebViewClicked = onWebViewClicked,
            onWebViewLongClicked = onWebViewLongClicked,
            onTrackingClicked = onTrackingClicked,
            onTagSearch = onTagSearch,
            onCopyTagToClipboard = onCopyTagToClipboard,
            onFilterButtonClicked = onFilterButtonClicked,
            onRefresh = onRefresh,
            onContinueWatching = onContinueWatching,
            onSearch = onSearch,
            onCoverClicked = onCoverClicked,
            onShareClicked = onShareClicked,
            onDownloadActionClicked = onDownloadActionClicked,
            onEditCategoryClicked = onEditCategoryClicked,
            onEditIntervalClicked = onEditFetchIntervalClicked,
            onMigrateClicked = onMigrateClicked,
            // AY -->
            onSettingsClicked = onSettingsClicked,
            onSkipIntroClicked = onSkipIntroClicked,
            // <-- AY
            // AM (CUSTOM_INFORMATION) -->
            onEditInfoClicked = onEditInfoClicked,
            // <-- AM (CUSTOM_INFORMATION)
            onEditNotesClicked = onEditNotesClicked,
            onMultiBookmarkClicked = onMultiBookmarkClicked,
            // AY -->
            onMultiFillermarkClicked = onMultiFillermarkClicked,
            // <-- AY
            onMultiMarkAsSeenClicked = onMultiMarkAsSeenClicked,
            onMarkPreviousAsSeenClicked = onMarkPreviousAsSeenClicked,
            onMultiDeleteClicked = onMultiDeleteClicked,
            onEpisodeSwipe = onEpisodeSwipe,
            onEpisodeSelected = onEpisodeSelected,
            onAllEpisodeSelected = onAllEpisodeSelected,
            onInvertSelection = onInvertSelection,
            // AY -->
            onSeasonClicked = onSeasonClicked,
            onClickContinueWatching = onContinueWatchingClicked,
            onRelatedAnimeClicked = onRelatedAnimeClicked,
            onRelatedAnimeLongClicked = onRelatedAnimeLongClicked,
            relatedAnimeDisplayMode = relatedAnimeDisplayMode,
            // <-- AY
        )
    }
}

@Composable
private fun AnimeScreenSmallImpl(
    state: AnimeViewModel.State.Success,
    snackbarHostState: SnackbarHostState,
    nextUpdate: Instant?,
    episodeSwipeStartAction: LibraryPreferences.EpisodeSwipeAction,
    episodeSwipeEndAction: LibraryPreferences.EpisodeSwipeAction,
    // AY -->
    showNextEpisodeAirTime: Boolean,
    alwaysUseExternalPlayer: Boolean,
    // <-- AY
    navigateUp: () -> Unit,
    // AM -->
    longNavigateUp: () -> Unit,
    // <-- AM
    // AM (FILE_SIZE) -->
    showFileSize: Boolean,
    // <-- AM (FILE_SIZE)
    // AY -->
    onEpisodeClicked: (Episode, Boolean) -> Unit,
    // <-- AY
    onDownloadEpisode: ((List<EpisodeList.Item>, EpisodeDownloadAction) -> Unit)?,
    onAddToLibraryClicked: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onWebViewLongClicked: (() -> Unit)?,
    // AY -->
    onTrackingClicked: (() -> Unit)?,
    // <-- AY

    // For tags menu
    onTagSearch: (String) -> Unit,
    onCopyTagToClipboard: (tag: String) -> Unit,

    onFilterClicked: () -> Unit,
    onRefresh: () -> Unit,
    onContinueWatching: () -> Unit,
    onSearch: (query: String, global: Boolean) -> Unit,

    // For cover dialog
    onCoverClicked: () -> Unit,

    // For top action menu
    onShareClicked: (() -> Unit)?,
    onDownloadActionClicked: ((DownloadAction) -> Unit)?,
    onEditCategoryClicked: (() -> Unit)?,
    onEditIntervalClicked: (() -> Unit)?,
    onMigrateClicked: (() -> Unit)?,
    // AY -->
    onSettingsClicked: (() -> Unit)?,
    onSkipIntroClicked: (() -> Unit)?,
    // <-- AY
    // AM (CUSTOM_INFORMATION) -->
    onEditInfoClicked: () -> Unit,
    // <-- AM (CUSTOM_INFORMATION)
    onEditNotesClicked: () -> Unit,

    // For bottom action menu
    onMultiBookmarkClicked: (List<Episode>, bookmarked: Boolean) -> Unit,
    // AY -->
    onMultiFillermarkClicked: (List<Episode>, fillermarked: Boolean) -> Unit,
    // <-- AY
    onMultiMarkAsSeenClicked: (List<Episode>, markAsSeen: Boolean) -> Unit,
    onMarkPreviousAsSeenClicked: (Episode) -> Unit,
    onMultiDeleteClicked: (List<Episode>) -> Unit,

    // For episode swipe
    onEpisodeSwipe: (EpisodeList.Item, LibraryPreferences.EpisodeSwipeAction) -> Unit,

    // Episode selection
    onEpisodeSelected: (EpisodeList.Item, Boolean, Boolean) -> Unit,
    onAllEpisodeSelected: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,

    // AY -->
    // Season clicked
    onSeasonClicked: (SeasonAnime) -> Unit,
    onClickContinueWatching: ((SeasonAnime) -> Unit)?,

    // Related anime clicked
    onRelatedAnimeClicked: (Anime) -> Unit,
    onRelatedAnimeLongClicked: (Anime) -> Unit,
    relatedAnimeDisplayMode: LibraryDisplayMode,
    // <-- AY
) {
    // AY -->
    val density = LocalDensity.current
    val offsetGridPaddingPx = with(density) { GRID_PADDING.roundToPx() }
    val gridSize = remember(state.anime) { state.anime.seasonDisplayGridSize }
    val itemListState = rememberLazyGridState()

    val (episodes, seasons, listItem, isAnySelected) = remember(state) {
        StateUIData(
            episodes = state.processedEpisodes,
            seasons = state.processedSeasons,
            listItem = state.episodeListItems,
            isAnySelected = state.isAnySelected,
        )
    }

    var toolbarHeight by remember { mutableIntStateOf(0) }
    // <-- AY

    BackHandler(enabled = isAnySelected) {
        onAllEpisodeSelected(false)
    }

    // AY -->
    BoxWithConstraints {
        val density = LocalDensity.current
        val containerHeightPx = with(density) { this@BoxWithConstraints.maxHeight.roundToPx() }
        // <-- AY
        Scaffold(
            topBar = {
                val selectedEpisodeCount: Int = remember(episodes) {
                    episodes.count { it.selected }
                }
                val isFirstItemVisible by remember {
                    derivedStateOf { itemListState.firstVisibleItemIndex == 0 }
                }
                val isFirstItemScrolled by remember {
                    derivedStateOf { itemListState.firstVisibleItemScrollOffset > 0 }
                }
                val titleAlpha by animateFloatAsState(
                    if (!isFirstItemVisible) 1f else 0f,
                    label = "Top Bar Title",
                )
                val backgroundAlpha by animateFloatAsState(
                    if (!isFirstItemVisible || isFirstItemScrolled) 1f else 0f,
                    label = "Top Bar Background",
                )
                AnimeToolbar(
                    title = state.anime.title,
                    hasFilters = state.filterActive,
                    navigateUp = navigateUp,
                    // AM -->
                    longNavigateUp = longNavigateUp,
                    // <-- AM
                    onClickFilter = onFilterClicked,
                    onClickShare = onShareClicked,
                    onClickDownload = onDownloadActionClicked,
                    onClickEditCategory = onEditCategoryClicked,
                    onClickRefresh = onRefresh,
                    onClickMigrate = onMigrateClicked,
                    // AY -->
                    onClickSettings = onSettingsClicked,
                    onClickSkipIntro = onSkipIntroClicked,
                    // <-- AY
                    // AM (CUSTOM_INFORMATION) -->
                    onClickEditInfo = onEditInfoClicked.takeIf { state.anime.favorite },
                    // <-- AM (CUSTOM_INFORMATION)
                    onClickEditNotes = onEditNotesClicked,
                    actionModeCounter = selectedEpisodeCount,
                    onCancelActionMode = { onAllEpisodeSelected(false) },
                    onSelectAll = { onAllEpisodeSelected(true) },
                    onInvertSelection = { onInvertSelection() },
                    titleAlphaProvider = { titleAlpha },
                    backgroundAlphaProvider = { backgroundAlpha },
                    // AY -->
                    modifier = Modifier.onSizeChanged { toolbarHeight = it.height },
                    // <-- AY
                )
            },
            bottomBar = {
                val selectedEpisodes = remember(episodes) {
                    episodes.filter { it.selected }
                }
                SharedAnimeBottomActionMenu(
                    selected = selectedEpisodes,
                    // AY -->
                    onEpisodeClicked = onEpisodeClicked,
                    alwaysUseExternalPlayer = alwaysUseExternalPlayer,
                    // <-- AY
                    onMultiBookmarkClicked = onMultiBookmarkClicked,
                    // AY -->
                    onMultiFillermarkClicked = onMultiFillermarkClicked,
                    // <-- AY
                    onMultiMarkAsSeenClicked = onMultiMarkAsSeenClicked,
                    onMarkPreviousAsSeenClicked = onMarkPreviousAsSeenClicked,
                    onDownloadEpisode = onDownloadEpisode,
                    onMultiDeleteClicked = onMultiDeleteClicked,
                    fillFraction = 1f,
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            floatingActionButton = {
                val isFABVisible = remember(episodes) {
                    episodes.fastAny { !it.episode.seen } && !isAnySelected
                }
                SmallExtendedFloatingActionButton(
                    text = {
                        val isWatching = remember(state.episodes) {
                            state.episodes.fastAny { it.episode.seen }
                        }
                        Text(
                            text = stringResource(
                                if (isWatching) MR.strings.action_resume else MR.strings.action_start,
                            ),
                        )
                    },
                    icon = { Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null) },
                    onClick = onContinueWatching,
                    expanded = itemListState.shouldExpandFAB(),
                    modifier = Modifier.animateFloatingActionButton(
                        visible = isFABVisible,
                        alignment = Alignment.BottomEnd,
                    ),
                )
            },
        ) { contentPadding ->
            val topPadding = contentPadding.calculateTopPadding()

            PullRefresh(
                refreshing = state.isRefreshingData,
                onRefresh = onRefresh,
                enabled = !isAnySelected,
                indicatorPadding = PaddingValues(top = topPadding),
            ) {
                val layoutDirection = LocalLayoutDirection.current
                // AY -->
                FastScrollIrregularLazyVerticalGrid(
                    modifier = Modifier.fillMaxHeight(),
                    state = itemListState,
                    columns = if (gridSize == 0) GridCells.Adaptive(128.dp) else GridCells.Fixed(gridSize),
                    contentPadding = PaddingValues(
                        start = GRID_PADDING + contentPadding.calculateStartPadding(layoutDirection),
                        end = GRID_PADDING + contentPadding.calculateEndPadding(layoutDirection),
                        bottom = contentPadding.calculateBottomPadding(),
                    ),
                    topContentPadding = contentPadding.calculateTopPadding(),
                ) {
                    // <-- AY
                    item(
                        key = EXACT_HEIGHT_KEY_PREFIX + AnimeScreenItem.INFO_BOX,
                        contentType = AnimeScreenItem.INFO_BOX,
                        // AY -->
                        span = { GridItemSpan(maxLineSpan) },
                        // <-- AY
                    ) {
                        AnimeInfoBox(
                            isTabletUi = false,
                            appBarPadding = topPadding,
                            anime = state.anime,
                            sourceName = remember { state.source.getNameForAnimeInfo() },
                            isStubSource = remember { state.source is StubSource },
                            onCoverClick = onCoverClicked,
                            doSearch = onSearch,
                            // AY -->
                            modifier = Modifier.ignorePadding(offsetGridPaddingPx),
                            // <-- AY
                        )
                    }

                    item(
                        key = EXACT_HEIGHT_KEY_PREFIX + AnimeScreenItem.ACTION_ROW,
                        contentType = AnimeScreenItem.ACTION_ROW,
                        // AY -->
                        span = { GridItemSpan(maxLineSpan) },
                        // <-- AY
                    ) {
                        AnimeActionRow(
                            favorite = state.anime.favorite,
                            trackingCount = state.trackingCount,
                            nextUpdate = nextUpdate,
                            isUserIntervalMode = state.anime.fetchInterval < 0,
                            // AM -->
                            isSyncingTrackers = state.isSyncingTrackers,
                            // <-- AM
                            onAddToLibraryClicked = onAddToLibraryClicked,
                            onWebViewClicked = onWebViewClicked,
                            onWebViewLongClicked = onWebViewLongClicked,
                            onTrackingClicked = onTrackingClicked,
                            onEditIntervalClicked = onEditIntervalClicked,
                            onEditCategory = onEditCategoryClicked,
                            // AY -->
                            modifier = Modifier.ignorePadding(offsetGridPaddingPx),
                            // <-- AY
                        )
                    }

                    item(
                        key = EXACT_HEIGHT_KEY_PREFIX + AnimeScreenItem.DESCRIPTION_WITH_TAG,
                        contentType = AnimeScreenItem.DESCRIPTION_WITH_TAG,
                        // AY -->
                        span = { GridItemSpan(maxLineSpan) },
                        // <-- AY
                    ) {
                        ExpandableAnimeDescription(
                            defaultExpandState = state.isFromSource,
                            description = state.anime.description,
                            tagsProvider = { state.anime.genre },
                            notes = state.anime.notes,
                            onTagSearch = onTagSearch,
                            onCopyTagToClipboard = onCopyTagToClipboard,
                            onEditNotes = onEditNotesClicked,
                            // AY -->
                            relations = state.relatedAnime,
                            onRelatedClick = onRelatedAnimeClicked,
                            onRelatedLongClick = onRelatedAnimeLongClicked,
                            relatedDisplayMode = relatedAnimeDisplayMode,
                            // <-- AY
                            // AY -->
                            modifier = Modifier.ignorePadding(offsetGridPaddingPx),
                            // <-- AY
                        )
                    }

                    item(
                        key = EXACT_HEIGHT_KEY_PREFIX + AnimeScreenItem.EPISODE_HEADER,
                        contentType = AnimeScreenItem.EPISODE_HEADER,
                        // AY -->
                        span = { GridItemSpan(maxLineSpan) },
                        // <-- AY
                    ) {
                        val missingEpisodeCount = remember(episodes) {
                            episodes.map { it.episode.episodeNumber }.missingEntriesCount()
                        }
                        // AY -->
                        val missingSeasonsCount = remember(seasons) {
                            seasons.map { it.seasonAnime.anime.seasonNumber }.missingEntriesCount()
                        }
                        ItemHeader(
                            enabled = !isAnySelected,
                            itemCount = when (state.anime.fetchType) {
                                FetchType.Seasons -> seasons.size
                                FetchType.Episodes -> episodes.size
                            },
                            missingItemsCount = when (state.anime.fetchType) {
                                FetchType.Seasons -> missingSeasonsCount
                                FetchType.Episodes -> missingEpisodeCount
                            },
                            onClick = onFilterClicked,
                            fetchType = state.anime.fetchType,
                            modifier = Modifier.ignorePadding(offsetGridPaddingPx),
                        )
                        // <-- AY
                    }

                    // AY -->
                    when (state.anime.fetchType) {
                        FetchType.Seasons -> {
                            sharedSeasons(
                                anime = state.anime,
                                seasons = seasons,
                                containerHeight = containerHeightPx - toolbarHeight,
                                onSeasonClicked = onSeasonClicked,
                                onClickContinueWatching = onClickContinueWatching,
                                listItemModifier = Modifier.ignorePadding(offsetGridPaddingPx),
                            )
                        }
                        // <-- AY
                        FetchType.Episodes -> {
                            // AY -->
                            if (state.airingTime > 0L) {
                                item(
                                    key = EXACT_HEIGHT_KEY_PREFIX + AnimeScreenItem.AIRING_TIME,
                                    contentType = AnimeScreenItem.AIRING_TIME,
                                    span = { GridItemSpan(maxLineSpan) },
                                ) {
                                    // Handles the second by second countdown
                                    var timer by remember { mutableLongStateOf(state.airingTime) }
                                    LaunchedEffect(key1 = timer) {
                                        if (timer > 0L) {
                                            delay(1.seconds)
                                            timer -= 1000L
                                        }
                                    }
                                    if (timer > 0L &&
                                        showNextEpisodeAirTime &&
                                        state.anime.status.toInt() != SAnime.COMPLETED
                                    ) {
                                        NextEpisodeAiringListItem(
                                            title = stringResource(
                                                AYMR.strings.display_mode_episode,
                                                formatEpisodeNumber(state.airingEpisodeNumber),
                                            ),
                                            date = formatTime(state.airingTime, useDayFormat = true),
                                            modifier = Modifier.ignorePadding(offsetGridPaddingPx),
                                        )
                                    }
                                }
                            }
                            // <-- AY

                            sharedEpisodeItems(
                                anime = state.anime,
                                // AM (FILE_SIZE) -->
                                source = state.source,
                                showFileSize = showFileSize,
                                // <-- AM (FILE_SIZE)
                                episodes = listItem,
                                isAnyEpisodeSelected = episodes.fastAny { it.selected },
                                // AY -->
                                showSummaries = state.showSummaries,
                                showPreviews = state.showPreviews,
                                // <-- AY
                                episodeSwipeStartAction = episodeSwipeStartAction,
                                episodeSwipeEndAction = episodeSwipeEndAction,
                                onEpisodeClicked = onEpisodeClicked,
                                onDownloadEpisode = onDownloadEpisode,
                                onEpisodeSelected = onEpisodeSelected,
                                onEpisodeSwipe = onEpisodeSwipe,
                                itemModifier = Modifier.ignorePadding(offsetGridPaddingPx),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AnimeScreenLargeImpl(
    state: AnimeViewModel.State.Success,
    snackbarHostState: SnackbarHostState,
    nextUpdate: Instant?,
    episodeSwipeStartAction: LibraryPreferences.EpisodeSwipeAction,
    episodeSwipeEndAction: LibraryPreferences.EpisodeSwipeAction,
    // AY -->
    showNextEpisodeAirTime: Boolean,
    alwaysUseExternalPlayer: Boolean,
    // <-- AY
    navigateUp: () -> Unit,
    // AM -->
    longNavigateUp: () -> Unit,
    // <-- AM
    // AM (FILE_SIZE) -->
    showFileSize: Boolean,
    // <-- AM (FILE_SIZE)
    // AY -->
    onEpisodeClicked: (Episode, Boolean) -> Unit,
    // <-- AY
    onDownloadEpisode: ((List<EpisodeList.Item>, EpisodeDownloadAction) -> Unit)?,
    onAddToLibraryClicked: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onWebViewLongClicked: (() -> Unit)?,
    // AY -->
    onTrackingClicked: (() -> Unit)?,
    // <-- AY

    // For tags menu
    onTagSearch: (String) -> Unit,
    onCopyTagToClipboard: (tag: String) -> Unit,

    onFilterButtonClicked: () -> Unit,
    onRefresh: () -> Unit,
    onContinueWatching: () -> Unit,
    onSearch: (query: String, global: Boolean) -> Unit,

    // For cover dialog
    onCoverClicked: () -> Unit,

    // For top action menu
    onShareClicked: (() -> Unit)?,
    onDownloadActionClicked: ((DownloadAction) -> Unit)?,
    onEditCategoryClicked: (() -> Unit)?,
    onEditIntervalClicked: (() -> Unit)?,
    onMigrateClicked: (() -> Unit)?,
    // AY -->
    onSettingsClicked: (() -> Unit)?,
    onSkipIntroClicked: (() -> Unit)?,
    // <-- AY
    // AM (CUSTOM_INFORMATION) -->
    onEditInfoClicked: () -> Unit,
    // <-- AM (CUSTOM_INFORMATION)
    onEditNotesClicked: () -> Unit,

    // For bottom action menu
    onMultiBookmarkClicked: (List<Episode>, bookmarked: Boolean) -> Unit,
    // AY -->
    onMultiFillermarkClicked: (List<Episode>, fillermarked: Boolean) -> Unit,
    // <-- AY
    onMultiMarkAsSeenClicked: (List<Episode>, markAsSeen: Boolean) -> Unit,
    onMarkPreviousAsSeenClicked: (Episode) -> Unit,
    onMultiDeleteClicked: (List<Episode>) -> Unit,

    // For swipe actions
    onEpisodeSwipe: (EpisodeList.Item, LibraryPreferences.EpisodeSwipeAction) -> Unit,

    // Episode selection
    onEpisodeSelected: (EpisodeList.Item, Boolean, Boolean) -> Unit,
    onAllEpisodeSelected: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,

    // AY -->
    // Season clicked
    onSeasonClicked: (SeasonAnime) -> Unit,
    onClickContinueWatching: ((SeasonAnime) -> Unit)?,

    // Related anime clicked
    onRelatedAnimeClicked: (Anime) -> Unit,
    onRelatedAnimeLongClicked: (Anime) -> Unit,
    relatedAnimeDisplayMode: LibraryDisplayMode,
    // <-- AY
) {
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current

    val (episodes, seasons, listItem, isAnySelected) = remember(state) {
        StateUIData(
            episodes = state.processedEpisodes,
            seasons = state.processedSeasons,
            listItem = state.episodeListItems,
            isAnySelected = state.isAnySelected,
        )
    }

    val insetPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).asPaddingValues()
    var topBarHeight by remember { mutableIntStateOf(0) }
    // AY -->
    val offsetGridPaddingPx = with(density) { GRID_PADDING.roundToPx() }
    val gridSize = remember(state.anime) { state.anime.seasonDisplayGridSize }

    val itemListState = rememberLazyGridState()
    // <-- AY

    BackHandler(enabled = isAnySelected) {
        onAllEpisodeSelected(false)
    }

    // AY -->
    BoxWithConstraints {
        val density = LocalDensity.current
        val containerHeightPx = with(density) { this@BoxWithConstraints.maxHeight.roundToPx() }
        // <-- AY
        Scaffold(
            topBar = {
                val selectedEpisodeCount = remember(episodes) {
                    episodes.count { it.selected }
                }
                AnimeToolbar(
                    modifier = Modifier.onSizeChanged { topBarHeight = it.height },
                    title = state.anime.title,
                    hasFilters = state.filterActive,
                    navigateUp = navigateUp,
                    // AM -->
                    longNavigateUp = longNavigateUp,
                    // <-- AM
                    onClickFilter = onFilterButtonClicked,
                    onClickShare = onShareClicked,
                    onClickDownload = onDownloadActionClicked,
                    onClickEditCategory = onEditCategoryClicked,
                    onClickRefresh = onRefresh,
                    onClickMigrate = onMigrateClicked,
                    // AY -->
                    onClickSettings = onSettingsClicked,
                    onClickSkipIntro = onSkipIntroClicked,
                    // <-- AY
                    // AM (CUSTOM_INFORMATION) -->
                    onClickEditInfo = onEditInfoClicked.takeIf { state.anime.favorite },
                    // <-- AM (CUSTOM_INFORMATION)
                    onClickEditNotes = onEditNotesClicked,
                    onCancelActionMode = { onAllEpisodeSelected(false) },
                    actionModeCounter = selectedEpisodeCount,
                    onSelectAll = { onAllEpisodeSelected(true) },
                    onInvertSelection = { onInvertSelection() },
                    titleAlphaProvider = { 1f },
                    backgroundAlphaProvider = { 1f },
                )
            },
            bottomBar = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    val selectedEpisodes = remember(episodes) {
                        episodes.filter { it.selected }
                    }
                    SharedAnimeBottomActionMenu(
                        selected = selectedEpisodes,
                        // AY -->
                        onEpisodeClicked = onEpisodeClicked,
                        alwaysUseExternalPlayer = alwaysUseExternalPlayer,
                        // <-- AY
                        onMultiBookmarkClicked = onMultiBookmarkClicked,
                        // AY -->
                        onMultiFillermarkClicked = onMultiFillermarkClicked,
                        // <-- AY
                        onMultiMarkAsSeenClicked = onMultiMarkAsSeenClicked,
                        onMarkPreviousAsSeenClicked = onMarkPreviousAsSeenClicked,
                        onDownloadEpisode = onDownloadEpisode,
                        onMultiDeleteClicked = onMultiDeleteClicked,
                        fillFraction = 0.5f,
                    )
                }
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            floatingActionButton = {
                val isFABVisible = remember(episodes) {
                    episodes.fastAny { !it.episode.seen } && !isAnySelected
                }
                SmallExtendedFloatingActionButton(
                    text = {
                        val isWatching = remember(state.episodes) {
                            state.episodes.fastAny { it.episode.seen }
                        }
                        Text(
                            text = stringResource(
                                if (isWatching) MR.strings.action_resume else MR.strings.action_start,
                            ),
                        )
                    },
                    icon = { Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null) },
                    onClick = onContinueWatching,
                    expanded = itemListState.shouldExpandFAB(),
                    modifier = Modifier.animateFloatingActionButton(
                        visible = isFABVisible,
                        alignment = Alignment.BottomEnd,
                    ),
                )
            },
        ) { contentPadding ->
            PullRefresh(
                refreshing = state.isRefreshingData,
                onRefresh = onRefresh,
                enabled = !isAnySelected,
                indicatorPadding = PaddingValues(
                    start = insetPadding.calculateStartPadding(layoutDirection),
                    top = with(density) { topBarHeight.toDp() },
                    end = insetPadding.calculateEndPadding(layoutDirection),
                ),
            ) {
                TwoPanelBox(
                    modifier = Modifier.padding(
                        start = contentPadding.calculateStartPadding(layoutDirection),
                        end = contentPadding.calculateEndPadding(layoutDirection),
                    ),
                    startContent = {
                        Column(
                            modifier = Modifier
                                .verticalScroll(rememberScrollState())
                                .padding(bottom = contentPadding.calculateBottomPadding()),
                        ) {
                            AnimeInfoBox(
                                isTabletUi = true,
                                appBarPadding = contentPadding.calculateTopPadding(),
                                anime = state.anime,
                                sourceName = remember { state.source.getNameForAnimeInfo() },
                                isStubSource = remember { state.source is StubSource },
                                onCoverClick = onCoverClicked,
                                doSearch = onSearch,
                            )
                            AnimeActionRow(
                                favorite = state.anime.favorite,
                                trackingCount = state.trackingCount,
                                nextUpdate = nextUpdate,
                                isUserIntervalMode = state.anime.fetchInterval < 0,
                                // AM -->
                                isSyncingTrackers = state.isSyncingTrackers,
                                // <-- AM
                                onAddToLibraryClicked = onAddToLibraryClicked,
                                onWebViewClicked = onWebViewClicked,
                                onWebViewLongClicked = onWebViewLongClicked,
                                onTrackingClicked = onTrackingClicked,
                                onEditIntervalClicked = onEditIntervalClicked,
                                onEditCategory = onEditCategoryClicked,
                            )
                            ExpandableAnimeDescription(
                                defaultExpandState = true,
                                description = state.anime.description,
                                tagsProvider = { state.anime.genre },
                                notes = state.anime.notes,
                                onTagSearch = onTagSearch,
                                onCopyTagToClipboard = onCopyTagToClipboard,
                                onEditNotes = onEditNotesClicked,
                                // AY -->
                                relations = state.relatedAnime,
                                onRelatedClick = onRelatedAnimeClicked,
                                onRelatedLongClick = onRelatedAnimeLongClicked,
                                relatedDisplayMode = relatedAnimeDisplayMode,
                                // <-- AY
                            )
                        }
                    },
                    endContent = {
                        // AY -->
                        FastScrollIrregularLazyVerticalGrid(
                            modifier = Modifier.fillMaxHeight(),
                            state = itemListState,
                            columns = if (gridSize == 0) GridCells.Adaptive(128.dp) else GridCells.Fixed(gridSize),
                            contentPadding = PaddingValues(
                                start = GRID_PADDING,
                                end = GRID_PADDING,
                                top = contentPadding.calculateTopPadding(),
                                bottom = contentPadding.calculateBottomPadding(),
                            ),
                            topContentPadding = contentPadding.calculateTopPadding(),
                        ) {
                            // <-- AY
                            item(
                                key = EXACT_HEIGHT_KEY_PREFIX + AnimeScreenItem.EPISODE_HEADER,
                                contentType = AnimeScreenItem.EPISODE_HEADER,
                                // AY -->
                                span = { GridItemSpan(maxLineSpan) },
                                // <-- AY
                            ) {
                                val missingEpisodeCount = remember(episodes) {
                                    episodes.map { it.episode.episodeNumber }.missingEntriesCount()
                                }
                                // AY -->
                                val missingSeasonsCount = remember(seasons) {
                                    seasons.map { it.seasonAnime.anime.seasonNumber }.missingEntriesCount()
                                }
                                ItemHeader(
                                    enabled = !isAnySelected,
                                    itemCount = when (state.anime.fetchType) {
                                        FetchType.Seasons -> seasons.size
                                        FetchType.Episodes -> episodes.size
                                    },
                                    missingItemsCount = when (state.anime.fetchType) {
                                        FetchType.Seasons -> missingSeasonsCount
                                        FetchType.Episodes -> missingEpisodeCount
                                    },
                                    onClick = onFilterButtonClicked,
                                    fetchType = state.anime.fetchType,
                                    modifier = Modifier.ignorePadding(offsetGridPaddingPx),
                                )
                                // <-- AY
                            }

                            // AY -->
                            when (state.anime.fetchType) {
                                FetchType.Seasons -> {
                                    sharedSeasons(
                                        anime = state.anime,
                                        seasons = seasons,
                                        containerHeight = containerHeightPx - topBarHeight,
                                        onSeasonClicked = onSeasonClicked,
                                        onClickContinueWatching = onClickContinueWatching,
                                        listItemModifier = Modifier.ignorePadding(offsetGridPaddingPx),
                                    )
                                }
                                // <-- AY
                                FetchType.Episodes -> {
                                    // AY -->
                                    if (state.airingTime > 0L) {
                                        item(
                                            key = EXACT_HEIGHT_KEY_PREFIX + AnimeScreenItem.AIRING_TIME,
                                            contentType = AnimeScreenItem.AIRING_TIME,
                                        ) {
                                            // Handles the second by second countdown
                                            var timer by remember { mutableLongStateOf(state.airingTime) }
                                            LaunchedEffect(key1 = timer) {
                                                if (timer > 0L) {
                                                    delay(1.seconds)
                                                    timer -= 1000L
                                                }
                                            }
                                            if (timer > 0L &&
                                                showNextEpisodeAirTime &&
                                                state.anime.status.toInt() != SAnime.COMPLETED
                                            ) {
                                                NextEpisodeAiringListItem(
                                                    title = stringResource(
                                                        AYMR.strings.display_mode_episode,
                                                        formatEpisodeNumber(state.airingEpisodeNumber),
                                                    ),
                                                    date = formatTime(state.airingTime, useDayFormat = true),
                                                    modifier = Modifier.ignorePadding(offsetGridPaddingPx),
                                                )
                                            }
                                        }
                                    }
                                    // <-- AY

                                    sharedEpisodeItems(
                                        anime = state.anime,
                                        // AM (FILE_SIZE) -->
                                        source = state.source,
                                        showFileSize = showFileSize,
                                        // <-- AM (FILE_SIZE)
                                        episodes = listItem,
                                        isAnyEpisodeSelected = episodes.fastAny { it.selected },
                                        // AY -->
                                        showSummaries = state.showSummaries,
                                        showPreviews = state.showPreviews,
                                        // <-- AY
                                        episodeSwipeStartAction = episodeSwipeStartAction,
                                        episodeSwipeEndAction = episodeSwipeEndAction,
                                        onEpisodeClicked = onEpisodeClicked,
                                        onDownloadEpisode = onDownloadEpisode,
                                        onEpisodeSelected = onEpisodeSelected,
                                        onEpisodeSwipe = onEpisodeSwipe,
                                        // AY -->
                                        itemModifier = Modifier.ignorePadding(offsetGridPaddingPx),
                                        // <-- AY
                                    )
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SharedAnimeBottomActionMenu(
    selected: List<EpisodeList.Item>,
    // AY -->
    onEpisodeClicked: (Episode, Boolean) -> Unit,
    alwaysUseExternalPlayer: Boolean,
    // <-- AY
    onMultiBookmarkClicked: (List<Episode>, bookmarked: Boolean) -> Unit,
    // AY -->
    onMultiFillermarkClicked: (List<Episode>, fillermarked: Boolean) -> Unit,
    // <-- AY
    onMultiMarkAsSeenClicked: (List<Episode>, markAsSeen: Boolean) -> Unit,
    onMarkPreviousAsSeenClicked: (Episode) -> Unit,
    onDownloadEpisode: ((List<EpisodeList.Item>, EpisodeDownloadAction) -> Unit)?,
    onMultiDeleteClicked: (List<Episode>) -> Unit,
    fillFraction: Float,
    modifier: Modifier = Modifier,
) {
    AnimeBottomActionMenu(
        visible = selected.isNotEmpty(),
        modifier = modifier.fillMaxWidth(fillFraction),
        onBookmarkClicked = {
            onMultiBookmarkClicked.invoke(selected.fastMap { it.episode }, true)
        }.takeIf { selected.fastAny { !it.episode.bookmark } },
        onRemoveBookmarkClicked = {
            onMultiBookmarkClicked.invoke(selected.fastMap { it.episode }, false)
        }.takeIf { selected.fastAll { it.episode.bookmark } },
        // AY -->
        onFillermarkClicked = {
            onMultiFillermarkClicked.invoke(selected.fastMap { it.episode }, true)
        }.takeIf { selected.fastAny { !it.episode.fillermark } },
        onRemoveFillermarkClicked = {
            onMultiFillermarkClicked.invoke(selected.fastMap { it.episode }, false)
        }.takeIf { selected.fastAll { it.episode.fillermark } },
        // <-- AY
        onMarkAsSeenClicked = {
            onMultiMarkAsSeenClicked(selected.fastMap { it.episode }, true)
        }.takeIf { selected.fastAny { !it.episode.seen } },
        onMarkAsUnseenClicked = {
            onMultiMarkAsSeenClicked(selected.fastMap { it.episode }, false)
        }.takeIf { selected.fastAny { it.episode.seen || it.episode.lastSecondSeen > 0L } },
        onMarkPreviousAsSeenClicked = {
            onMarkPreviousAsSeenClicked(selected[0].episode)
        }.takeIf { selected.size == 1 },
        onDownloadClicked = {
            onDownloadEpisode!!(selected.toList(), EpisodeDownloadAction.START)
        }.takeIf {
            onDownloadEpisode != null && selected.fastAny { it.downloadState != Download.State.DOWNLOADED }
        },
        onDeleteClicked = {
            onMultiDeleteClicked(selected.fastMap { it.episode })
        }.takeIf {
            selected.fastAny { it.downloadState == Download.State.DOWNLOADED }
        },
        // AY -->
        onExternalClicked = {
            onEpisodeClicked(selected.fastMap { it.episode }.first(), true)
        }.takeIf { !alwaysUseExternalPlayer && selected.size == 1 },
        onInternalClicked = {
            onEpisodeClicked(selected.fastMap { it.episode }.first(), true)
        }.takeIf { alwaysUseExternalPlayer && selected.size == 1 },
        // <-- AY
    )
}

// AY -->
private fun LazyGridScope.sharedSeasons(
    anime: Anime,
    seasons: List<AnimeSeasonItem>,
    containerHeight: Int,
    onSeasonClicked: (SeasonAnime) -> Unit,
    onClickContinueWatching: ((SeasonAnime) -> Unit)?,
    listItemModifier: Modifier = Modifier,
) {
    items(
        items = seasons,
        key = { season -> season.seasonAnime.anime },
        span = { GridItemSpan(if (anime.seasonDisplayGridMode == SeasonDisplayMode.List) maxLineSpan else 1) },
    ) { item ->
        AnimeSeasonListItem(
            anime = anime,
            item = item,
            containerHeight = containerHeight,
            onSeasonClicked = onSeasonClicked,
            onClickContinueWatching = onClickContinueWatching,
            listItemModifier = listItemModifier,
        )
    }
}

private fun LazyGridScope.sharedEpisodeItems(
    // <-- AY
    anime: Anime,
    // AM (FILE_SIZE) -->
    source: AnimeSource,
    showFileSize: Boolean,
    // <-- AM (FILE_SIZE)
    episodes: List<EpisodeList>,
    isAnyEpisodeSelected: Boolean,
    // AY -->
    showSummaries: Boolean,
    showPreviews: Boolean,
    // <-- AY
    episodeSwipeStartAction: LibraryPreferences.EpisodeSwipeAction,
    episodeSwipeEndAction: LibraryPreferences.EpisodeSwipeAction,
    // AY -->
    onEpisodeClicked: (Episode, Boolean) -> Unit,
    // <-- AY
    onDownloadEpisode: ((List<EpisodeList.Item>, EpisodeDownloadAction) -> Unit)?,
    onEpisodeSelected: (EpisodeList.Item, Boolean, Boolean) -> Unit,
    onEpisodeSwipe: (EpisodeList.Item, LibraryPreferences.EpisodeSwipeAction) -> Unit,
    // AY -->
    itemModifier: Modifier = Modifier,
    // <-- AY
) {
    items(
        items = episodes,
        key = { item ->
            when (item) {
                is EpisodeList.MissingCount -> "missing-count-${item.id}"
                is EpisodeList.Item -> "episode-${item.id}"
            }
        },
        contentType = { AnimeScreenItem.EPISODE },
        // AY -->
        span = { GridItemSpan(maxLineSpan) },
        // <-- AY
    ) { item ->
        val context = LocalContext.current
        val haptic = LocalHapticFeedback.current
        val downloadProvider = remember { context.appGraph.downloadProvider }

        when (item) {
            is EpisodeList.MissingCount -> {
                MissingEpisodeCountListItem(
                    count = item.count,
                    // AY -->
                    modifier = itemModifier,
                    // <-- AY
                )
            }
            is EpisodeList.Item -> {
                // AM (FILE_SIZE) -->
                var fileSizeAsync: Long? by remember { mutableStateOf(item.fileSize) }
                val isEpisodeDownloaded = item.downloadState == Download.State.DOWNLOADED
                if (isEpisodeDownloaded && showFileSize && fileSizeAsync == null) {
                    LaunchedEffect(item, Unit) {
                        fileSizeAsync = withIOContext {
                            downloadProvider.getEpisodeFileSize(
                                item.episode.name,
                                item.episode.url,
                                item.episode.scanlator,
                                // AM (CUSTOM_INFORMATION) -->
                                anime.ogTitle,
                                // <-- AM (CUSTOM_INFORMATION)
                                source,
                            )
                        }
                        item.fileSize = fileSizeAsync
                    }
                }
                // <-- AM (FILE_SIZE)
                AnimeEpisodeListItem(
                    title = if (anime.displayMode == Anime.EPISODE_DISPLAY_NUMBER) {
                        stringResource(
                            AYMR.strings.display_mode_episode,
                            formatEpisodeNumber(item.episode.episodeNumber),
                        )
                    } else {
                        item.episode.name
                    },
                    date = relativeDateText(item.episode.dateUpload),
                    watchProgress = item.episode.lastSecondSeen
                        .takeIf { !item.episode.seen && it > 0L }
                        ?.let {
                            // AY -->
                            stringResource(
                                AYMR.strings.episode_progress,
                                formatTime(it),
                                formatTime(item.episode.totalSeconds),
                            )
                            // <-- AY
                        },
                    scanlator = item.episode.scanlator.takeIf { !it.isNullOrBlank() },
                    // AY -->
                    summary = item.episode.summary.takeIf { !it.isNullOrBlank() && showSummaries },
                    previewUrl = item.episode.previewUrl.takeIf { !it.isNullOrBlank() && showPreviews },
                    // <-- AY
                    seen = item.episode.seen,
                    bookmark = item.episode.bookmark,
                    // AY -->
                    fillermark = item.episode.fillermark,
                    // <-- AY
                    selected = item.selected,
                    // AY -->
                    isAnyEpisodeSelected = isAnyEpisodeSelected,
                    // <-- AY
                    downloadIndicatorEnabled = !isAnyEpisodeSelected && !anime.isLocal(),
                    downloadStateProvider = { item.downloadState },
                    downloadProgressProvider = { item.downloadProgress },
                    episodeSwipeStartAction = episodeSwipeStartAction,
                    episodeSwipeEndAction = episodeSwipeEndAction,
                    onLongClick = {
                        onEpisodeSelected(item, !item.selected, true)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onClick = {
                        onEpisodeItemClick(
                            episodeItem = item,
                            isAnyEpisodeSelected = isAnyEpisodeSelected,
                            onToggleSelection = { onEpisodeSelected(item, !item.selected, false) },
                            onEpisodeClicked = onEpisodeClicked,
                        )
                    },
                    onDownloadClick = if (onDownloadEpisode != null) {
                        { onDownloadEpisode(listOf(item), it) }
                    } else {
                        null
                    },
                    onEpisodeSwipe = {
                        onEpisodeSwipe(item, it)
                    },
                    // AM (FILE_SIZE) -->
                    fileSize = fileSizeAsync,
                    // <-- AM (FILE_SIZE)
                    // AY -->
                    modifier = itemModifier,
                    // <-- AY
                )
            }
        }
    }
}

private fun onEpisodeItemClick(
    episodeItem: EpisodeList.Item,
    isAnyEpisodeSelected: Boolean,
    onToggleSelection: (Boolean) -> Unit,
    // AY -->
    onEpisodeClicked: (Episode, Boolean) -> Unit,
    // <-- AY
) {
    when {
        episodeItem.selected -> onToggleSelection(false)
        isAnyEpisodeSelected -> onToggleSelection(true)
        else -> onEpisodeClicked(episodeItem.episode, false)
    }
}

// AY -->
private data class StateUIData(
    val episodes: List<EpisodeList.Item>,
    val seasons: List<AnimeSeasonItem>,
    val listItem: List<EpisodeList>,
    val isAnySelected: Boolean,
)

private fun formatTime(milliseconds: Long, useDayFormat: Boolean = false): String {
    val dur = milliseconds.milliseconds

    return if (useDayFormat) {
        dur.toComponents { days, hours, minutes, seconds, _ ->
            "Airing in %02dd %02dh %02dm %02ds".format(days, hours, minutes, seconds)
        }
    } else if (milliseconds > 3600000L) {
        dur.toComponents { _, hours, minutes, seconds, _ ->
            "%d:%02d:%02d".format(hours, minutes, seconds)
        }
    } else {
        dur.toComponents { _, _, minutes, seconds, _ ->
            "%d:%02d".format(minutes, seconds)
        }
    }
}

private val GRID_PADDING = 14.dp
private fun Modifier.ignorePadding(gridPadding: Int) = layout { measurable, constraints ->
    val looseConstraints = constraints.offset(gridPadding * 2, 0)
    val placeable = measurable.measure(looseConstraints)

    layout(placeable.width, placeable.height) {
        placeable.placeRelative(0, 0)
    }
}
// <-- AY
