package eu.kanade.tachiyomi.ui.anime

import android.content.Context
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Immutable
import androidx.compose.ui.util.fastAny
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import animiru.core.cache.ThumbnailCache
import animiru.domain.player.service.GesturePreferences
import animiru.domain.player.service.PlayerPreferences
import aniyomi.core.common.torrent.TorrentPreferences
import aniyomi.core.common.torrent.TorrentServerUtils
import aniyomi.domain.anime.interactor.GetRelatedAnime
import aniyomi.domain.anime.model.AnimeRelationGroup
import aniyomi.domain.anime.model.SeasonAnime
import aniyomi.domain.anime.model.SeasonDisplayMode
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import eu.kanade.core.util.addOrRemove
import eu.kanade.core.util.insertSeparators
import eu.kanade.domain.anime.interactor.GetExcludedScanlators
import eu.kanade.domain.anime.interactor.SetAnimeViewerFlags
import eu.kanade.domain.anime.interactor.SetExcludedScanlators
import eu.kanade.domain.anime.interactor.SyncRelatedAnimeWithSource
import eu.kanade.domain.anime.interactor.UpdateAnime
import eu.kanade.domain.anime.model.downloadedFilter
import eu.kanade.domain.anime.model.episodesFiltered
import eu.kanade.domain.anime.model.seasonDownloadedFilter
import eu.kanade.domain.anime.model.seasonsFiltered
import eu.kanade.domain.anime.model.toSAnime
import eu.kanade.domain.episode.interactor.GetAvailableScanlators
import eu.kanade.domain.episode.interactor.SetSeenStatus
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.track.interactor.AddTracks
import eu.kanade.domain.track.interactor.RefreshResult
import eu.kanade.domain.track.interactor.RefreshTracks
import eu.kanade.domain.track.interactor.TrackEpisode
import eu.kanade.domain.track.model.AutoTrackState
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.presentation.anime.DownloadAction
import eu.kanade.presentation.anime.components.EpisodeDownloadAction
import eu.kanade.presentation.util.formattedMessage
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.UnmeteredSource
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.torrent.service.TorrentServerService
import eu.kanade.tachiyomi.data.track.EnhancedTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.source.isSourceForTorrents
import eu.kanade.tachiyomi.ui.anime.track.TrackItem
import eu.kanade.tachiyomi.util.AniChartApi
import eu.kanade.tachiyomi.util.episode.getNextUnseen
import eu.kanade.tachiyomi.util.nullIfEmpty
import eu.kanade.tachiyomi.util.removeCovers
import eu.kanade.tachiyomi.util.removeThumbnail
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.trimOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import logcat.LogPriority
import mihon.domain.episode.interactor.FilterEpisodesForDownload
import mihon.domain.source.interactor.UpdateAnimeFromRemote
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.preference.mapAsCheckboxState
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.anime.interactor.GetAnimeWithEpisodesAndSeasons
import tachiyomi.domain.anime.interactor.GetDuplicateLibraryAnime
import tachiyomi.domain.anime.interactor.SetAnimeEpisodeFlags
import tachiyomi.domain.anime.interactor.SetAnimeSeasonFlags
import tachiyomi.domain.anime.interactor.SetCustomAnimeInfo
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.model.AnimeUpdate
import tachiyomi.domain.anime.model.AnimeWithEpisodeCount
import tachiyomi.domain.anime.model.CustomAnimeInfo
import tachiyomi.domain.anime.model.NoSeasonsException
import tachiyomi.domain.anime.model.applyFilter
import tachiyomi.domain.anime.repository.AnimeRepository
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetAnimeCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.episode.interactor.SetAnimeDefaultEpisodeFlags
import tachiyomi.domain.episode.interactor.UpdateEpisode
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.episode.model.EpisodeUpdate
import tachiyomi.domain.episode.model.NoEpisodesException
import tachiyomi.domain.episode.service.calculateEpisodeGap
import tachiyomi.domain.episode.service.getEpisodeSort
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.season.interactor.SetAnimeDefaultSeasonFlags
import tachiyomi.domain.season.service.getSeasonSortComparator
import tachiyomi.domain.season.service.seasonSortAlphabetically
import tachiyomi.domain.source.model.StubSource
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.storage.service.StoragePreferences
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.source.local.LocalSource
import tachiyomi.source.local.isLocal
import java.util.Calendar
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.floor

@AssistedInject
class AnimeViewModel(
    @Assisted private val animeId: Long,
    @Assisted private val isFromSource: Boolean,
    private val context: Context,
    private val libraryPreferences: LibraryPreferences,
    private val trackPreferences: TrackPreferences,
    // AY -->
    downloadPreferences: DownloadPreferences,
    private val torrentPreferences: TorrentPreferences,
    private val sourcePreferences: SourcePreferences,
    internal val playerPreferences: PlayerPreferences,
    internal val gesturePreferences: GesturePreferences,
    internal val setAnimeViewerFlags: SetAnimeViewerFlags,
    // <-- AY
    private val trackerManager: TrackerManager,
    private val trackEpisode: TrackEpisode,
    private val downloadManager: DownloadManager,
    private val downloadCache: DownloadCache,
    // AY -->
    private val getAnimeAndEpisodesAndSeasons: GetAnimeWithEpisodesAndSeasons,
    // <-- AY
    private val getDuplicateLibraryAnime: GetDuplicateLibraryAnime,
    private val getAvailableScanlators: GetAvailableScanlators,
    private val getExcludedScanlators: GetExcludedScanlators,
    private val setExcludedScanlators: SetExcludedScanlators,
    private val setAnimeEpisodeFlags: SetAnimeEpisodeFlags,
    private val setAnimeDefaultEpisodeFlags: SetAnimeDefaultEpisodeFlags,
    // AY -->
    private val setAnimeSeasonFlags: SetAnimeSeasonFlags,
    private val setAnimeDefaultSeasonFlags: SetAnimeDefaultSeasonFlags,
    private val getRelatedAnime: GetRelatedAnime,
    private val syncRelatedAnimeWithSource: SyncRelatedAnimeWithSource,
    // <-- AY
    private val setSeenStatus: SetSeenStatus,
    private val updateEpisode: UpdateEpisode,
    private val updateAnime: UpdateAnime,
    private val getCategories: GetCategories,
    private val getTracks: GetTracks,
    private val addTracks: AddTracks,
    private val setAnimeCategories: SetAnimeCategories,
    private val animeRepository: AnimeRepository,
    // AY -->
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId,
    private val torrentServerUtils: TorrentServerUtils,
    // <-- AY
    private val filterEpisodesForDownload: FilterEpisodesForDownload,
    private val updateAnimeFromRemote: UpdateAnimeFromRemote,
    private val sourceManager: SourceManager,
    private val refreshTracks: RefreshTracks,
    private val coverCache: CoverCache,
    // AM -->
    private val thumbnailCache: ThumbnailCache,
    // <-- AM
    // AM (FILE_SIZE) -->
    storagePreferences: StoragePreferences,
    // <-- AM (FILE_SIZE)
    // AM (CUSTOM_INFORMATION) -->
    private val setCustomAnimeInfo: SetCustomAnimeInfo,
    // <-- AM (CUSTOM_INFORMATION)
) : ViewModel() {

    val state: StateFlow<AnimeViewModel.State>
        field = MutableStateFlow<AnimeViewModel.State>(State.Loading)

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(animeId: Long, isFromSource: Boolean): AnimeViewModel
    }

    val snackbarHostState: SnackbarHostState = SnackbarHostState()

    private val successState: State.Success?
        get() = state.value as? State.Success

    val anime: Anime?
        get() = successState?.anime

    val source: AnimeSource?
        get() = successState?.source

    private val isFavorited: Boolean
        get() = anime?.favorite ?: false

    private val allEpisodes: List<EpisodeList.Item>?
        get() = successState?.episodes

    private val filteredEpisodes: List<EpisodeList.Item>?
        get() = successState?.processedEpisodes

    val episodeSwipeStartAction = libraryPreferences.swipeToEndAction.get()
    val episodeSwipeEndAction = libraryPreferences.swipeToStartAction.get()
    var autoTrackState = trackPreferences.autoUpdateTrackOnMarkSeen.get()

    val isUpdateIntervalEnabled =
        LibraryPreferences.ANIME_OUTSIDE_RELEASE_PERIOD in libraryPreferences.autoUpdateAnimeRestrictions.get()

    private val selectedPositions: Array<Int> = arrayOf(-1, -1) // first and last selected index in list
    private val selectedEpisodeIds: HashSet<Long> = HashSet()

    // AY -->
    val showNextEpisodeAirTime = trackPreferences.showNextEpisodeAiringTime.get()
    val alwaysUseExternalPlayer = playerPreferences.alwaysUseExternalPlayer.get()
    val useExternalDownloader = downloadPreferences.useExternalDownloader.get()

    val relatedAnimeDisplayMode = sourcePreferences.sourceDisplayMode.get()

    internal var isFromChangeCategory: Boolean = false

    internal val autoOpenTrack: Boolean
        get() = successState?.hasLoggedInTrackers == true && trackPreferences.trackOnAddingToLibrary.get()
    // <-- AY

    // AM (FILE_SIZE) -->
    val showFileSize = storagePreferences.showEpisodeFileSize.get()
    // <-- AM (FILE_SIZE)

    /**
     * Helper function to update the UI state only if it's currently in success state
     */
    private inline fun updateSuccessState(func: (State.Success) -> State.Success) {
        state.update {
            when (it) {
                State.Loading -> it
                is State.Success -> func(it)
            }
        }
    }

    init {
        viewModelScope.launchIO {
            combine(
                getAnimeAndEpisodesAndSeasons.subscribe(animeId, applyScanlatorFilter = true).distinctUntilChanged(),
                downloadCache.changes,
                downloadManager.queueState,
            ) { animeAndEpisodesAndSeasons, _, _ -> animeAndEpisodesAndSeasons }
                .collectLatest { (anime, episodes, seasons) ->
                    updateSuccessState {
                        it.copy(
                            anime = anime,
                            episodes = episodes.toEpisodeListItems(anime),
                            // AY -->
                            seasons = seasons.toAnimeSeasonItems(),
                            // <-- AY
                        )
                    }
                }
        }

        viewModelScope.launchIO {
            getExcludedScanlators.subscribe(animeId)
                .distinctUntilChanged()
                .collectLatest { excludedScanlators ->
                    updateSuccessState {
                        it.copy(excludedScanlators = excludedScanlators)
                    }
                }
        }

        viewModelScope.launchIO {
            getAvailableScanlators.subscribe(animeId)
                .distinctUntilChanged()
                .collectLatest { availableScanlators ->
                    updateSuccessState {
                        it.copy(availableScanlators = availableScanlators)
                    }
                }
        }

        observeDownloads()

        viewModelScope.launchIO {
            // AY -->
            val anime = getAnimeAndEpisodesAndSeasons.awaitAnime(animeId)
            val source = sourceManager.getOrStub(anime.source)

            val episodes = if (anime.fetchType == FetchType.Seasons) {
                emptyList()
            } else {
                getAnimeAndEpisodesAndSeasons.awaitEpisodes(animeId)
                    .toEpisodeListItems(anime)
            }

            val seasons = if (anime.fetchType == FetchType.Episodes) {
                emptyList()
            } else {
                getAnimeAndEpisodesAndSeasons.awaitSeasons(animeId)
                    .toAnimeSeasonItems()
            }
            // <-- AY

            if (!anime.favorite) {
                setAnimeDefaultEpisodeFlags.await(anime)
                // AY -->
                setAnimeDefaultSeasonFlags.await(anime)
                // <-- AY
            }

            val needRefreshInfo = !anime.initialized
            // AY -->
            val needRefreshEpisode = episodes.isEmpty() && anime.fetchType == FetchType.Episodes
            val needRefreshSeason = seasons.isEmpty() && anime.fetchType == FetchType.Seasons
            // <-- AY

            // Show what we have earlier
            state.update {
                State.Success(
                    anime = anime,
                    source = source,
                    isFromSource = isFromSource,
                    episodes = episodes,
                    availableScanlators = getAvailableScanlators.await(animeId),
                    excludedScanlators = getExcludedScanlators.await(animeId),
                    // AY -->
                    seasons = seasons,
                    isRefreshingData = needRefreshInfo || needRefreshEpisode || needRefreshSeason,
                    // <-- AY
                    dialog = null,
                    hideMissingEpisodes = libraryPreferences.hideMissingEpisodes.get(),
                )
            }

            // Start observe tracking since it only needs animeId
            observeTrackers()

            // AY -->
            observeRelatedAnime()
            syncRelatedAnime()
            // <-- AY

            // Fetch info-episodes when needed
            if (viewModelScope.isActive) {
                val fetchFromSourceTasks = listOf(
                    // AM -->
                    async { syncTrackers() },
                    // <-- AM
                    async {
                        if (needRefreshInfo || needRefreshEpisode || needRefreshSeason) {
                            fetchAllFromSource(
                                manualFetch = false,
                                fetchDetails = needRefreshInfo,
                                fetchEpisodes = needRefreshEpisode,
                                fetchSeasons = needRefreshSeason,
                            )
                        }
                    },
                )
                fetchFromSourceTasks.awaitAll()
            }

            // Initial loading finished
            updateSuccessState { it.copy(isRefreshingData = false) }
        }
    }

    fun fetchAllFromSource(manualFetch: Boolean = true) {
        viewModelScope.launch {
            updateSuccessState { it.copy(isRefreshingData = true) }
            val fetchFromSourceTasks = listOf(
                // AM -->
                async { syncTrackers() },
                // <-- AM
                async {
                    fetchAllFromSource(
                        manualFetch = manualFetch,
                        fetchDetails = true,
                        fetchEpisodes = true,
                        fetchSeasons = true,
                    )
                },
            )
            fetchFromSourceTasks.awaitAll()
            updateSuccessState { it.copy(isRefreshingData = false) }
            // AY -->
            successState?.let { updateAiringTime(it.anime, it.trackItems, manualFetch) }
            // <-- AY
        }
    }

    // AM -->
    private suspend fun syncTrackers() {
        if (!trackPreferences.syncEnhancedTrackers.get()) return
        val state = successState ?: return
        if (state.source is StubSource) return

        updateSuccessState { it.copy(isSyncingTrackers = true) }

        when (state.anime.fetchType) {
            FetchType.Seasons -> {
                if (trackPreferences.smartTrackerSync.get()) {
                    seasons@ for (s in state.seasons) {
                        refreshTrackers(animeId = s.seasonAnime.id, enhancedOnly = true, skipCompleted = true)
                            .filterIsInstance<RefreshResult.Success>()
                            .onEach {
                                if (it.track.lastEpisodeSeen.toLong() != it.track.totalEpisodes) {
                                    break@seasons
                                }
                            }
                    }
                } else {
                    state.seasons.chunked(5).forEach { s ->
                        supervisorScope {
                            s.map { season ->
                                async { refreshTrackers(animeId = season.seasonAnime.id, enhancedOnly = true) }
                            }.awaitAll()
                        }
                    }
                }
            }
            FetchType.Episodes -> {
                refreshTrackers(enhancedOnly = true)
            }
        }

        updateSuccessState { it.copy(isSyncingTrackers = false) }
    }
    // <-- AM

    private suspend fun fetchAllFromSource(
        manualFetch: Boolean,
        fetchDetails: Boolean,
        fetchEpisodes: Boolean,
        fetchSeasons: Boolean,
    ) {
        val state = successState ?: return

        // AY -->
        startTorrentServer(state.source)

        if (fetchDetails) {
            syncRelatedAnime(forceRefresh = true)
        }
        // <-- AY

        try {
            withUIContext {
                when (state.anime.fetchType) {
                    FetchType.Episodes -> {
                        val update = updateAnimeFromRemote.awaitEpisodesUpdate(
                            source = state.source,
                            anime = state.anime,
                            fetchDetails = fetchDetails,
                            fetchEpisodes = fetchEpisodes,
                            manualFetch = manualFetch,
                        )
                            .getOrThrow()

                        if (manualFetch) {
                            downloadNewEpisodes(update.newEpisodes)
                        }
                    }
                    FetchType.Seasons -> {
                        val update = updateAnimeFromRemote.awaitSeasonsUpdate(
                            source = state.source,
                            anime = state.anime,
                            fetchDetails = fetchDetails,
                            fetchSeasons = fetchSeasons,
                            manualFetch = manualFetch,
                        )
                            .getOrThrow()

                        if (libraryPreferences.updateSeasonOnRefresh.get()) {
                            fetchEpisodesFromSeasons(update.newSeasons, manualFetch)
                        }
                    }
                }
            }
        } catch (_: CancellationException) {
            // ignore
        } catch (e: Exception) {
            val message = when (e) {
                is NoEpisodesException -> {
                    context.stringResource(AYMR.strings.no_episodes_error)
                }
                is NoSeasonsException -> {
                    context.stringResource(AYMR.strings.no_seasons_error)
                }
                else -> {
                    logcat(LogPriority.ERROR, e)
                    with(context) { e.formattedMessage }
                }
            }
            viewModelScope.launch {
                snackbarHostState.showSnackbar(message = message)
            }
        }
    }

    // Anime info - start

    // AM (CUSTOM_INFORMATION) -->
    fun updateAnimeInfo(
        title: String?,
        author: String?,
        artist: String?,
        description: String?,
        tags: List<String>?,
        status: Long?,
    ) {
        val state = successState ?: return
        var anime = state.anime
        if (state.anime.isLocal()) {
            val newTitle = if (title.isNullOrBlank()) anime.url else title.trim()
            val newAuthor = author?.trimOrNull()
            val newArtist = artist?.trimOrNull()
            val newDesc = description?.trimOrNull()
            anime = anime.copy(
                ogTitle = newTitle,
                ogAuthor = author?.trimOrNull(),
                ogArtist = artist?.trimOrNull(),
                ogDescription = description?.trimOrNull(),
                ogGenre = tags?.nullIfEmpty(),
                ogStatus = status ?: 0,
                lastUpdate = anime.lastUpdate + 1,
            )
            (sourceManager.get(LocalSource.ID) as LocalSource).updateAnimeInfo(anime.toSAnime())
            viewModelScope.launchNonCancellable {
                updateAnime.await(
                    AnimeUpdate(
                        anime.id,
                        title = newTitle,
                        author = newAuthor,
                        artist = newArtist,
                        description = newDesc,
                        genre = tags,
                        status = status,
                    ),
                )
            }
        } else {
            val genre = if (!tags.isNullOrEmpty() && tags != state.anime.ogGenre) {
                tags
            } else {
                null
            }
            setCustomAnimeInfo.set(
                CustomAnimeInfo(
                    state.anime.id,
                    title?.trimOrNull(),
                    author?.trimOrNull(),
                    artist?.trimOrNull(),
                    description?.trimOrNull(),
                    genre,
                    status.takeUnless { it == state.anime.ogStatus },
                ),
            )
            anime = anime.copy(lastUpdate = anime.lastUpdate + 1)
        }

        updateSuccessState { successState ->
            successState.copy(anime = anime)
        }
    }
    // <-- AM (CUSTOM_INFORMATION)

    fun toggleFavorite() {
        toggleFavorite(
            onRemoved = {
                viewModelScope.launch {
                    if (!hasDownloads()) return@launch
                    val result = snackbarHostState.showSnackbar(
                        message = context.stringResource(AYMR.strings.delete_downloads_for_anime),
                        actionLabel = context.stringResource(MR.strings.action_delete),
                        withDismissAction = true,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        deleteDownloads()
                    }
                }
            },
        )
    }

    /**
     * Update favorite status of anime, (removes / adds) anime (to / from) library.
     */
    fun toggleFavorite(
        onRemoved: () -> Unit,
        checkDuplicate: Boolean = true,
    ) {
        val state = successState ?: return
        viewModelScope.launchIO {
            val anime = state.anime

            if (isFavorited) {
                // Remove from library
                if (updateAnime.awaitUpdateFavorite(anime.id, false)) {
                    // Remove covers and update last modified in db
                    if (anime.removeCovers(coverCache) != anime) {
                        updateAnime.awaitUpdateCoverLastModified(anime.id)
                    }

                    // AM -->
                    if (anime.fetchType == FetchType.Episodes) {
                        val episodes = state.episodes.map { it.episode }
                        episodes.forEach {
                            if (it.removeThumbnail(thumbnailCache, anime.isLocal()) != it) {
                                updateEpisode.awaitUpdateThumbnailLastModified(it.id)
                            }
                        }
                    }
                    // <-- AM

                    withUIContext { onRemoved() }
                }
            } else {
                // Add to library
                // First, check if duplicate exists if callback is provided
                if (checkDuplicate) {
                    val duplicates = getDuplicateLibraryAnime(anime)

                    if (duplicates.isNotEmpty()) {
                        updateSuccessState { it.copy(dialog = Dialog.DuplicateAnime(anime, duplicates)) }
                        return@launchIO
                    }
                }

                // Now check if user previously set categories, when available
                val categories = getCategories()
                val defaultCategoryId = libraryPreferences.defaultCategory.get().toLong()
                val defaultCategory = categories.find { it.id == defaultCategoryId }
                when {
                    // Default category set
                    defaultCategory != null -> {
                        val result = updateAnime.awaitUpdateFavorite(anime.id, true)
                        if (!result) return@launchIO
                        moveAnimeToCategory(defaultCategory)
                    }

                    // Automatic 'Default' or no categories
                    defaultCategoryId == 0L || categories.isEmpty() -> {
                        val result = updateAnime.awaitUpdateFavorite(anime.id, true)
                        if (!result) return@launchIO
                        moveAnimeToCategory(null)
                    }

                    // Choose a category
                    else -> {
                        // AY -->
                        isFromChangeCategory = true
                        // <-- AY
                        showChangeCategoryDialog()
                    }
                }

                // Finally match with enhanced tracking when available
                addTracks.bindEnhancedTrackers(anime, state.source)
                // AY -->
                if (!isFromChangeCategory && autoOpenTrack && anime.fetchType == FetchType.Episodes) {
                    showTrackDialog()
                }
                // <-- AY
            }
        }
    }

    fun showChangeCategoryDialog() {
        val anime = successState?.anime ?: return
        viewModelScope.launch {
            val categories = getCategories()
            val selection = getAnimeCategoryIds(anime)
            updateSuccessState { successState ->
                successState.copy(
                    dialog = Dialog.ChangeCategory(
                        anime = anime,
                        initialSelection = categories.mapAsCheckboxState { it.id in selection },
                    ),
                )
            }
        }
    }

    fun showSetFetchIntervalDialog() {
        val anime = successState?.anime ?: return
        updateSuccessState {
            it.copy(dialog = Dialog.SetFetchInterval(anime))
        }
    }

    fun setFetchInterval(anime: Anime, interval: Int) {
        viewModelScope.launchIO {
            if (
                updateAnime.awaitUpdateFetchInterval(
                    // Custom intervals are negative
                    anime.copy(fetchInterval = -interval),
                )
            ) {
                val updatedAnime = animeRepository.getAnimeById(anime.id)
                updateSuccessState { it.copy(anime = updatedAnime) }
            }
        }
    }

    /**
     * Returns true if the anime has any downloads.
     */
    private fun hasDownloads(): Boolean {
        val anime = successState?.anime ?: return false
        return downloadManager.getDownloadCount(anime) > 0
    }

    /**
     * Deletes all the downloads for the anime.
     */
    private fun deleteDownloads() {
        val state = successState ?: return
        downloadManager.deleteAnime(state.anime, state.source)
    }

    /**
     * Get user categories.
     *
     * @return List of categories, not including the default category
     */
    suspend fun getCategories(): List<Category> {
        return getCategories.await().filterNot { it.isSystemCategory }
    }

    /**
     * Gets the category id's the anime is in, if the anime is not in a category, returns the default id.
     *
     * @param anime the anime to get categories from.
     * @return Array of category ids the anime is in, if none returns default id
     */
    private suspend fun getAnimeCategoryIds(anime: Anime): List<Long> {
        return getCategories.await(anime.id)
            .map { it.id }
    }

    fun moveAnimeToCategoriesAndAddToLibrary(anime: Anime, categories: List<Long>) {
        moveAnimeToCategory(categories)
        if (anime.favorite) return

        viewModelScope.launchIO {
            updateAnime.awaitUpdateFavorite(anime.id, true)
        }
    }

    /**
     * Move the given anime to categories.
     *
     * @param categories the selected categories.
     */
    private fun moveAnimeToCategories(categories: List<Category>) {
        val categoryIds = categories.map { it.id }
        moveAnimeToCategory(categoryIds)
    }

    private fun moveAnimeToCategory(categoryIds: List<Long>) {
        viewModelScope.launchIO {
            setAnimeCategories.await(animeId, categoryIds)
        }
    }

    /**
     * Move the given anime to the category.
     *
     * @param category the selected category, or null for default category.
     */
    private fun moveAnimeToCategory(category: Category?) {
        moveAnimeToCategories(listOfNotNull(category))
    }

    // Anime info - end

    // Episodes list - start

    private fun observeDownloads() {
        viewModelScope.launchIO {
            downloadManager.statusFlow()
                .filter { it.anime.id == successState?.anime?.id }
                .catch { error -> logcat(LogPriority.ERROR, error) }
                .collect {
                    withUIContext {
                        updateDownloadState(it)
                    }
                }
        }

        viewModelScope.launchIO {
            downloadManager.progressFlow()
                .filter { it.anime.id == successState?.anime?.id }
                .catch { error -> logcat(LogPriority.ERROR, error) }
                .collect {
                    withUIContext {
                        updateDownloadState(it)
                    }
                }
        }
    }

    private fun updateDownloadState(download: Download) {
        updateSuccessState { successState ->
            val modifiedIndex = successState.episodes.indexOfFirst { it.id == download.episode.id }
            if (modifiedIndex < 0) return@updateSuccessState successState

            val newEpisodes = successState.episodes.toMutableList().apply {
                val item = removeAt(modifiedIndex)
                    .copy(downloadState = download.status, downloadProgress = download.progress)
                add(modifiedIndex, item)
            }
            successState.copy(episodes = newEpisodes)
        }
    }

    private fun List<Episode>.toEpisodeListItems(anime: Anime): List<EpisodeList.Item> {
        val isLocal = anime.isLocal()
        return map { episode ->
            val activeDownload = if (isLocal) {
                null
            } else {
                downloadManager.getQueuedDownloadOrNull(episode.id)
            }
            val downloaded = if (isLocal) {
                true
            } else {
                downloadManager.isEpisodeDownloaded(
                    episode.name,
                    episode.scanlator,
                    episode.url,
                    // AM (CUSTOM_INFORMATION) -->
                    anime.ogTitle,
                    // <-- AM (CUSTOM_INFORMATION)
                    anime.source,
                )
            }
            val downloadState = when {
                activeDownload != null -> activeDownload.status
                downloaded -> Download.State.DOWNLOADED
                else -> Download.State.NOT_DOWNLOADED
            }

            EpisodeList.Item(
                episode = episode,
                downloadState = downloadState,
                downloadProgress = activeDownload?.progress ?: 0,
                selected = episode.id in selectedEpisodeIds,
            )
        }
    }

    // AY -->
    private fun List<SeasonAnime>.toAnimeSeasonItems(): List<AnimeSeasonItem> {
        return map { seasonAnime ->
            AnimeSeasonItem(
                seasonAnime = seasonAnime,
                downloadCount = downloadManager.getDownloadCount(seasonAnime.anime).toLong(),
                unseenCount = seasonAnime.unseenCount,
                isLocal = seasonAnime.anime.isLocal(),
                sourceLanguage = sourceManager.getOrStub(seasonAnime.anime.source).lang,
                showContinueOverlay = false,
            )
        }
    }

    fun isTorrentEnabled(): Boolean {
        return torrentPreferences.torrServerEnable.get()
    }

    private suspend fun startTorrentServer(source: AnimeSource?) {
        if (isTorrentEnabled() && source.isSourceForTorrents()) {
            TorrentServerService.start(context)
            TorrentServerService.wait(context, 10)
            torrentServerUtils.setTrackersList()
        }
    }

    /**
     * Fetch episodes from all seasons of an anime.
     */
    private suspend fun CoroutineScope.fetchEpisodesFromSeasons(seasons: List<Anime>, manualFetch: Boolean) {
        val state = successState ?: return

        val fetch: suspend (Anime) -> Unit = { s ->
            // Only fetch seasons with `Episodes` fetch type and only for non completed, unless they
            // haven't been fetched at all.
            if (s.fetchType === FetchType.Episodes && (s.lastUpdate == 0L || s.status.toInt() != SAnime.COMPLETED)) {
                try {
                    updateAnimeFromRemote.awaitEpisodesUpdate(
                        source = state.source,
                        anime = s,
                        fetchEpisodes = true,
                        manualFetch = manualFetch,
                    )
                } catch (e: Throwable) {
                    logcat(LogPriority.ERROR, e)
                }
            }
        }

        if (state.source is UnmeteredSource) {
            seasons.map { s ->
                async(Dispatchers.IO) {
                    fetch(s)
                }
            }.awaitAll()
        } else {
            seasons.forEach { s ->
                ensureActive()
                fetch(s)
            }
        }
    }
    // <-- AY

    /**
     * @throws IllegalStateException if the swipe action is [LibraryPreferences.EpisodeSwipeAction.Disabled]
     */
    fun episodeSwipe(episodeItem: EpisodeList.Item, swipeAction: LibraryPreferences.EpisodeSwipeAction) {
        viewModelScope.launch {
            executeEpisodeSwipeAction(episodeItem, swipeAction)
        }
    }

    /**
     * @throws IllegalStateException if the swipe action is [LibraryPreferences.EpisodeSwipeAction.Disabled]
     */
    private fun executeEpisodeSwipeAction(
        episodeItem: EpisodeList.Item,
        swipeAction: LibraryPreferences.EpisodeSwipeAction,
    ) {
        val episode = episodeItem.episode
        when (swipeAction) {
            LibraryPreferences.EpisodeSwipeAction.ToggleSeen -> {
                markEpisodesSeen(listOf(episode), !episode.seen)
            }
            LibraryPreferences.EpisodeSwipeAction.ToggleBookmark -> {
                bookmarkEpisodes(listOf(episode), !episode.bookmark)
            }
            // AY -->
            LibraryPreferences.EpisodeSwipeAction.ToggleFillermark -> {
                fillermarkEpisodes(listOf(episode), !episode.fillermark)
            }
            // <-- AY
            LibraryPreferences.EpisodeSwipeAction.Download -> {
                val downloadAction: EpisodeDownloadAction = when (episodeItem.downloadState) {
                    Download.State.ERROR,
                    Download.State.NOT_DOWNLOADED,
                    -> EpisodeDownloadAction.START_NOW
                    Download.State.QUEUE,
                    Download.State.DOWNLOADING,
                    -> EpisodeDownloadAction.CANCEL
                    Download.State.DOWNLOADED -> EpisodeDownloadAction.DELETE
                }
                runEpisodeDownloadActions(
                    items = listOf(episodeItem),
                    action = downloadAction,
                )
            }
            LibraryPreferences.EpisodeSwipeAction.Disabled -> throw IllegalStateException()
        }
    }

    // AY -->
    suspend fun getNextUnseenEpisode(anime: Anime): Episode? {
        return getEpisodesByAnimeId.await(anime.id).getNextUnseen(anime, downloadManager)
    }
    // <-- AY

    /**
     * Returns the next unseen episode or null if everything is seen.
     */
    fun getNextUnseenEpisode(): Episode? {
        val successState = successState ?: return null
        return successState.episodes.getNextUnseen(successState.anime)
    }

    private fun getUnseenEpisodes(): List<Episode> {
        return filteredEpisodes
            .orEmpty()
            .filter { (episode, dlStatus) -> !episode.seen && dlStatus == Download.State.NOT_DOWNLOADED }
            .map { it.episode }
    }

    private fun getUnseenEpisodesSorted(): List<Episode> {
        val anime = successState?.anime ?: return emptyList()
        val episodesSorted = getUnseenEpisodes().sortedWith(getEpisodeSort(anime))
        return if (anime.sortDescending()) episodesSorted.reversed() else episodesSorted
    }

    private fun getBookmarkedEpisodes(): List<Episode> {
        return filteredEpisodes
            .orEmpty()
            .filter { (episode, dlStatus) -> episode.bookmark && dlStatus == Download.State.NOT_DOWNLOADED }
            .map { it.episode }
    }

    private fun startDownload(
        episodes: List<Episode>,
        startNow: Boolean,
        // AY -->
        video: Video? = null,
        // <-- AY
    ) {
        val successState = successState ?: return

        viewModelScope.launchNonCancellable {
            if (startNow) {
                val episodeId = episodes.singleOrNull()?.id ?: return@launchNonCancellable
                downloadManager.startDownloadNow(episodeId)
            } else {
                downloadEpisodes(episodes, false, video)
            }

            if (!isFavorited && !successState.hasPromptedToAddBefore) {
                updateSuccessState { state ->
                    state.copy(hasPromptedToAddBefore = true)
                }
                val result = snackbarHostState.showSnackbar(
                    message = context.stringResource(MR.strings.snack_add_to_library),
                    actionLabel = context.stringResource(MR.strings.action_add),
                    withDismissAction = true,
                )
                if (result == SnackbarResult.ActionPerformed && !isFavorited) {
                    toggleFavorite()
                }
            }
        }
    }

    fun runEpisodeDownloadActions(
        items: List<EpisodeList.Item>,
        action: EpisodeDownloadAction,
    ) {
        when (action) {
            EpisodeDownloadAction.START -> {
                startDownload(items.map { it.episode }, false)
                if (items.any { it.downloadState == Download.State.ERROR }) {
                    downloadManager.startDownloads()
                }
            }
            EpisodeDownloadAction.START_NOW -> {
                val episode = items.singleOrNull()?.episode ?: return
                startDownload(listOf(episode), true)
            }
            EpisodeDownloadAction.CANCEL -> {
                val episodeId = items.singleOrNull()?.id ?: return
                cancelDownload(episodeId)
            }
            EpisodeDownloadAction.DELETE -> {
                deleteEpisodes(items.map { it.episode })
            }
            // AY -->
            EpisodeDownloadAction.SHOW_QUALITIES -> {
                val episode = items.singleOrNull()?.episode ?: return
                showQualitiesDialog(episode)
            }
            // <-- AY
        }
    }

    fun runDownloadAction(action: DownloadAction) {
        val episodesToDownload = when (action) {
            DownloadAction.NEXT_1_EPISODE -> getUnseenEpisodesSorted().take(1)
            DownloadAction.NEXT_5_EPISODES -> getUnseenEpisodesSorted().take(5)
            DownloadAction.NEXT_10_EPISODES -> getUnseenEpisodesSorted().take(10)
            DownloadAction.NEXT_25_EPISODES -> getUnseenEpisodesSorted().take(25)
            DownloadAction.UNSEEN_EPISODES -> getUnseenEpisodes()
            DownloadAction.BOOKMARKED_EPISODES -> getBookmarkedEpisodes()
        }
        if (episodesToDownload.isNotEmpty()) {
            startDownload(episodesToDownload, false)
        }
    }

    private fun cancelDownload(episodeId: Long) {
        val activeDownload = downloadManager.getQueuedDownloadOrNull(episodeId) ?: return
        downloadManager.cancelQueuedDownloads(listOf(activeDownload))
        updateDownloadState(activeDownload.apply { status = Download.State.NOT_DOWNLOADED })
    }

    fun markPreviousEpisodeSeen(pointer: Episode) {
        val anime = successState?.anime ?: return
        val episodes = filteredEpisodes.orEmpty().map { it.episode }
        val prevEpisodes = if (anime.sortDescending()) episodes.asReversed() else episodes
        val pointerPos = prevEpisodes.indexOf(pointer)
        if (pointerPos != -1) markEpisodesSeen(prevEpisodes.take(pointerPos), true)
    }

    /**
     * Mark the selected episode list as seen/unseen.
     * @param episodes the list of selected episodes.
     * @param seen whether to mark episodes as seen or unseen.
     */
    fun markEpisodesSeen(episodes: List<Episode>, seen: Boolean) {
        toggleAllSelection(false)
        if (episodes.isEmpty()) return
        viewModelScope.launchIO {
            setSeenStatus.await(
                seen = seen,
                episodes = episodes.toTypedArray(),
            )

            if (!seen || successState?.hasLoggedInTrackers == false || autoTrackState == AutoTrackState.NEVER) {
                return@launchIO
            }

            refreshTrackers()

            val tracks = getTracks.await(animeId)
            val maxEpisodeNumber = episodes.maxOf { it.episodeNumber }
            val shouldPromptTrackingUpdate = tracks.any { track -> maxEpisodeNumber > track.lastEpisodeSeen }

            if (!shouldPromptTrackingUpdate) return@launchIO
            if (autoTrackState == AutoTrackState.ALWAYS) {
                trackEpisode.await(context, animeId, maxEpisodeNumber)
                withUIContext {
                    context.toast(
                        context.stringResource(AYMR.strings.trackers_updated_summary_anime, maxEpisodeNumber.toInt()),
                    )
                }
                return@launchIO
            }

            val result = snackbarHostState.showSnackbar(
                message = context.stringResource(AYMR.strings.confirm_tracker_update_anime, maxEpisodeNumber.toInt()),
                actionLabel = context.stringResource(MR.strings.action_ok),
                duration = SnackbarDuration.Short,
                withDismissAction = true,
            )

            if (result == SnackbarResult.ActionPerformed) {
                trackEpisode.await(context, animeId, maxEpisodeNumber)
            }
        }
    }

    private suspend fun refreshTrackers(
        // AM -->
        enhancedOnly: Boolean = false,
        skipCompleted: Boolean = false,
        // <-- AM
    ): List<RefreshResult> {
        return refreshTrackers(
            animeId = animeId,
            enhancedOnly = enhancedOnly,
            skipCompleted = skipCompleted,
        )
    }

    // AM -->
    private suspend fun refreshTrackers(
        animeId: Long,
        enhancedOnly: Boolean = false,
        skipCompleted: Boolean = false,
    ): List<RefreshResult> {
        return refreshTracks.await(animeId, enhancedOnly, skipCompleted)
            .onEach {
                val (track, e) = it as? RefreshResult.Failure ?: return@onEach
                logcat(LogPriority.ERROR, e) {
                    "Failed to refresh track data animeId=$animeId for service ${track.id}"
                }
                withUIContext {
                    context.toast(
                        context.stringResource(
                            MR.strings.track_error,
                            track.name,
                            e.message ?: "",
                        ),
                    )
                }
            }
    }
    // <-- AM

    /**
     * Downloads the given list of episodes with the manager.
     * @param episodes the list of episodes to download.
     */
    private fun downloadEpisodes(
        episodes: List<Episode>,
        // AY -->
        alt: Boolean = false,
        video: Video? = null,
        // <-- AY
    ) {
        val anime = successState?.anime ?: return
        downloadManager.downloadEpisodes(anime, episodes, true, alt, video)
        toggleAllSelection(false)
    }

    /**
     * Bookmarks the given list of episodes.
     * @param episodes the list of episodes to bookmark.
     */
    fun bookmarkEpisodes(episodes: List<Episode>, bookmarked: Boolean) {
        viewModelScope.launchIO {
            episodes
                .filterNot { it.bookmark == bookmarked }
                .map { EpisodeUpdate(id = it.id, bookmark = bookmarked) }
                .let { updateEpisode.awaitAll(it) }
        }
        toggleAllSelection(false)
    }

    // AY -->

    /**
     * Fillermarks the given list of episodes.
     * @param episodes the list of episodes to fillermark.
     */
    fun fillermarkEpisodes(episodes: List<Episode>, fillermarked: Boolean) {
        viewModelScope.launchIO {
            episodes
                .filterNot { it.fillermark == fillermarked }
                .map { EpisodeUpdate(id = it.id, fillermark = fillermarked) }
                .let { updateEpisode.awaitAll(it) }
        }
        toggleAllSelection(false)
    }
    // <-- AY

    /**
     * Deletes the given list of episode.
     *
     * @param episodes the list of episodes to delete.
     */
    fun deleteEpisodes(episodes: List<Episode>) {
        viewModelScope.launchNonCancellable {
            try {
                successState?.let { state ->
                    downloadManager.deleteEpisodes(
                        episodes,
                        state.anime,
                        state.source,
                    )
                }
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
            }
        }
    }

    private fun downloadNewEpisodes(episodes: List<Episode>) {
        viewModelScope.launchNonCancellable {
            val anime = successState?.anime ?: return@launchNonCancellable
            val episodesToDownload = filterEpisodesForDownload.await(anime, episodes)

            if (episodesToDownload.isNotEmpty()) {
                downloadEpisodes(episodesToDownload)
            }
        }
    }

    /**
     * Sets the seen filter and requests an UI update.
     * @param state whether to display only unseen episodes or all episodes.
     */
    fun setUnseenFilter(state: TriState) {
        val anime = successState?.anime ?: return

        val flag = when (state) {
            TriState.DISABLED -> Anime.SHOW_ALL
            TriState.ENABLED_IS -> Anime.EPISODE_SHOW_UNSEEN
            TriState.ENABLED_NOT -> Anime.EPISODE_SHOW_SEEN
        }
        viewModelScope.launchNonCancellable {
            setAnimeEpisodeFlags.awaitSetUnseenFilter(anime, flag)
        }
    }

    /**
     * Sets the download filter and requests an UI update.
     * @param state whether to display only downloaded episodes or all episodes.
     */
    fun setDownloadedFilter(state: TriState) {
        val anime = successState?.anime ?: return

        val flag = when (state) {
            TriState.DISABLED -> Anime.SHOW_ALL
            TriState.ENABLED_IS -> Anime.EPISODE_SHOW_DOWNLOADED
            TriState.ENABLED_NOT -> Anime.EPISODE_SHOW_NOT_DOWNLOADED
        }

        viewModelScope.launchNonCancellable {
            setAnimeEpisodeFlags.awaitSetDownloadedFilter(anime, flag)
        }
    }

    /**
     * Sets the bookmark filter and requests an UI update.
     * @param state whether to display only bookmarked episodes or all episodes.
     */
    fun setBookmarkedFilter(state: TriState) {
        val anime = successState?.anime ?: return

        val flag = when (state) {
            TriState.DISABLED -> Anime.SHOW_ALL
            TriState.ENABLED_IS -> Anime.EPISODE_SHOW_BOOKMARKED
            TriState.ENABLED_NOT -> Anime.EPISODE_SHOW_NOT_BOOKMARKED
        }

        viewModelScope.launchNonCancellable {
            setAnimeEpisodeFlags.awaitSetBookmarkFilter(anime, flag)
        }
    }

    // AY -->

    /**
     * Sets the fillermark filter and requests an UI update.
     * @param state whether to display only fillermarked episodes or all episodes.
     */
    fun setFillermarkedFilter(state: TriState) {
        val anime = successState?.anime ?: return

        val flag = when (state) {
            TriState.DISABLED -> Anime.SHOW_ALL
            TriState.ENABLED_IS -> Anime.EPISODE_SHOW_FILLERMARKED
            TriState.ENABLED_NOT -> Anime.EPISODE_SHOW_NOT_FILLERMARKED
        }

        viewModelScope.launchNonCancellable {
            setAnimeEpisodeFlags.awaitSetFillermarkFilter(anime, flag)
        }
    }
    // <-- AY

    /**
     * Sets the active display mode.
     * @param mode the mode to set.
     */
    fun setDisplayMode(mode: Long) {
        val anime = successState?.anime ?: return

        viewModelScope.launchNonCancellable {
            setAnimeEpisodeFlags.awaitSetDisplayMode(anime, mode)
        }
    }

    /**
     * Sets the sorting method and requests an UI update.
     * @param sort the sorting mode.
     */
    fun setSorting(sort: Long) {
        val anime = successState?.anime ?: return

        viewModelScope.launchNonCancellable {
            setAnimeEpisodeFlags.awaitSetSortingModeOrFlipOrder(anime, sort)
        }
    }

    // AY -->

    /**
     * Sets whether previews are to be shown or not.
     * @param flag to show previews.
     */
    fun showEpisodePreviews(flag: Long) {
        val anime = successState?.anime ?: return

        viewModelScope.launchNonCancellable {
            setAnimeEpisodeFlags.awaitShowEpisodePreviews(anime, flag)
        }
    }

    /**
     * Sets whether summaries are to be shown or not.
     * @param flag to show summaries.
     */
    fun showEpisodeSummaries(flag: Long) {
        val anime = successState?.anime ?: return

        viewModelScope.launchNonCancellable {
            setAnimeEpisodeFlags.awaitShowEpisodeSummaries(anime, flag)
        }
    }
    // <-- AY

    fun setCurrentSettingsAsDefault(applyToExisting: Boolean) {
        val anime = successState?.anime ?: return
        viewModelScope.launchNonCancellable {
            libraryPreferences.setEpisodeSettingsDefault(anime)
            if (applyToExisting) {
                setAnimeDefaultEpisodeFlags.awaitAll()
            }
            snackbarHostState.showSnackbar(message = context.stringResource(AYMR.strings.episode_settings_updated))
        }
    }

    // AY -->

    /**
     * Sets the season download filter and requests an UI update.
     * @param state whether to display only downloaded seasons or all seasons.
     */
    fun setSeasonDownloadedFilter(state: TriState) {
        val anime = successState?.anime ?: return

        val flag = when (state) {
            TriState.DISABLED -> Anime.SHOW_ALL
            TriState.ENABLED_IS -> Anime.SEASON_SHOW_DOWNLOADED
            TriState.ENABLED_NOT -> Anime.SEASON_SHOW_NOT_DOWNLOADED
        }

        viewModelScope.launchNonCancellable {
            setAnimeSeasonFlags.awaitSetDownloadedFilter(anime, flag)
        }
    }

    /**
     * Sets the season seen filter and requests an UI update.
     * @param state whether to display only unseen seasons or all seasons.
     */
    fun setSeasonUnseenFilter(state: TriState) {
        val anime = successState?.anime ?: return

        val flag = when (state) {
            TriState.DISABLED -> Anime.SHOW_ALL
            TriState.ENABLED_IS -> Anime.SEASON_SHOW_UNSEEN
            TriState.ENABLED_NOT -> Anime.SEASON_SHOW_SEEN
        }

        viewModelScope.launchNonCancellable {
            setAnimeSeasonFlags.awaitSetUnseenFilter(anime, flag)
        }
    }

    /**
     * Sets the season started filter and requests an UI update.
     * @param state whether to display only started seasons or all seasons.
     */
    fun setSeasonStartedFilter(state: TriState) {
        val anime = successState?.anime ?: return

        val flag = when (state) {
            TriState.DISABLED -> Anime.SHOW_ALL
            TriState.ENABLED_IS -> Anime.SEASON_SHOW_STARTED
            TriState.ENABLED_NOT -> Anime.SEASON_SHOW_NOT_STARTED
        }

        viewModelScope.launchNonCancellable {
            setAnimeSeasonFlags.awaitSetStartedFilter(anime, flag)
        }
    }

    /**
     * Sets the season bookmarked filter and requests an UI update.
     * @param state whether to display only bookmarked seasons or all seasons.
     */
    fun setSeasonBookmarkedFilter(state: TriState) {
        val anime = successState?.anime ?: return

        val flag = when (state) {
            TriState.DISABLED -> Anime.SHOW_ALL
            TriState.ENABLED_IS -> Anime.SEASON_SHOW_BOOKMARKED
            TriState.ENABLED_NOT -> Anime.SEASON_SHOW_NOT_BOOKMARKED
        }

        viewModelScope.launchNonCancellable {
            setAnimeSeasonFlags.awaitSetBookmarkedFilter(anime, flag)
        }
    }

    // AY -->

    /**
     * Sets the season fillermarked filter and requests an UI update.
     * @param state whether to display only fillermarked seasons or all seasons.
     */
    fun setSeasonFillermarkedFilter(state: TriState) {
        val anime = successState?.anime ?: return

        val flag = when (state) {
            TriState.DISABLED -> Anime.SHOW_ALL
            TriState.ENABLED_IS -> Anime.SEASON_SHOW_FILLERMARKED
            TriState.ENABLED_NOT -> Anime.SEASON_SHOW_NOT_FILLERMARKED
        }

        viewModelScope.launchNonCancellable {
            setAnimeSeasonFlags.awaitSetFillermarkedFilter(anime, flag)
        }
    }
    // <-- AY

    /**
     * Sets the season completed filter and requests an UI update.
     * @param state whether to display only completed seasons or all seasons.
     */
    fun setSeasonCompletedFilter(state: TriState) {
        val anime = successState?.anime ?: return

        val flag = when (state) {
            TriState.DISABLED -> Anime.SHOW_ALL
            TriState.ENABLED_IS -> Anime.SEASON_SHOW_COMPLETED
            TriState.ENABLED_NOT -> Anime.SEASON_SHOW_NOT_COMPLETED
        }

        viewModelScope.launchNonCancellable {
            setAnimeSeasonFlags.awaitSetCompletedFilter(anime, flag)
        }
    }

    /**
     * Sets the season sorting method and requests an UI update.
     * @param sort the sorting mode.
     */
    fun setSeasonSorting(sort: Long) {
        val anime = successState?.anime ?: return

        viewModelScope.launchNonCancellable {
            setAnimeSeasonFlags.awaitSetSortingModeOrFlipOrder(anime, sort)
        }
    }

    /**
     * Sets the season grid display method and requests an UI update.
     * @param mode the display mode.
     */
    fun setSeasonDisplayGridMode(mode: SeasonDisplayMode) {
        val anime = successState?.anime ?: return

        viewModelScope.launchNonCancellable {
            setAnimeSeasonFlags.awaitSetGridMode(anime, mode)
        }
    }

    /**
     * Sets the season grid size and requests an UI update.
     * @param size the size.
     */
    fun setSeasonDisplayGridSize(size: Int) {
        val anime = successState?.anime ?: return

        viewModelScope.launchNonCancellable {
            setAnimeSeasonFlags.awaitSetGridSize(anime, size)
        }
    }

    /**
     * Sets the season download overlay and requests an UI update.
     * @param visible the visibility.
     */
    fun setSeasonDownloadOverlay(visible: Boolean) {
        val anime = successState?.anime ?: return

        viewModelScope.launchNonCancellable {
            setAnimeSeasonFlags.awaitSetDownloadedOverlay(anime, visible)
        }
    }

    /**
     * Sets the season unseen overlay and requests an UI update.
     * @param visible the visibility.
     */
    fun setSeasonUnseenOverlay(visible: Boolean) {
        val anime = successState?.anime ?: return

        viewModelScope.launchNonCancellable {
            setAnimeSeasonFlags.awaitSetUnseenOverlay(anime, visible)
        }
    }

    /**
     * Sets the season local overlay and requests an UI update.
     * @param visible the visibility.
     */
    fun setSeasonLocalOverlay(visible: Boolean) {
        val anime = successState?.anime ?: return

        viewModelScope.launchNonCancellable {
            setAnimeSeasonFlags.awaitSetLocalOverlay(anime, visible)
        }
    }

    /**
     * Sets the season lang overlay and requests an UI update.
     * @param visible the visibility.
     */
    fun setSeasonLangOverlay(visible: Boolean) {
        val anime = successState?.anime ?: return

        viewModelScope.launchNonCancellable {
            setAnimeSeasonFlags.awaitSetLangOverlay(anime, visible)
        }
    }

    /**
     * Sets the season continue overlay and requests an UI update.
     * @param visible the visibility.
     */
    fun setSeasonContinueOverlay(visible: Boolean) {
        val anime = successState?.anime ?: return

        viewModelScope.launchNonCancellable {
            setAnimeSeasonFlags.awaitSetContinueOverlay(anime, visible)
        }
    }

    /**
     * Sets the active season display mode.
     * @param mode the mode to set.
     */
    fun setSeasonDisplayMode(mode: Long) {
        val anime = successState?.anime ?: return

        viewModelScope.launchNonCancellable {
            setAnimeSeasonFlags.awaitSetDisplayMode(anime, mode)
        }
    }

    fun setSeasonCurrentSettingsAsDefault(applyToExisting: Boolean) {
        val anime = successState?.anime ?: return

        viewModelScope.launchNonCancellable {
            libraryPreferences.setSeasonSettingsDefault(anime)
            if (applyToExisting) {
                setAnimeDefaultSeasonFlags.awaitAll()
            }
            snackbarHostState.showSnackbar(
                message = context.stringResource(AYMR.strings.season_settings_updated),
            )
        }
    }
    // <-- AY

    fun resetToDefaultSettings() {
        val anime = successState?.anime ?: return
        viewModelScope.launchNonCancellable {
            setAnimeDefaultEpisodeFlags.await(anime)
        }
    }

    fun toggleSelection(
        item: EpisodeList.Item,
        selected: Boolean,
        fromLongPress: Boolean = false,
    ) {
        updateSuccessState { successState ->
            val newEpisodes = successState.processedEpisodes.toMutableList().apply {
                val selectedIndex = successState.processedEpisodes.indexOfFirst { it.id == item.episode.id }
                if (selectedIndex < 0) return@apply

                val selectedItem = get(selectedIndex)
                if ((selectedItem.selected && selected) || (!selectedItem.selected && !selected)) return@apply

                val firstSelection = none { it.selected }
                set(selectedIndex, selectedItem.copy(selected = selected))
                selectedEpisodeIds.addOrRemove(item.id, selected)

                if (selected && fromLongPress) {
                    if (firstSelection) {
                        selectedPositions[0] = selectedIndex
                        selectedPositions[1] = selectedIndex
                    } else {
                        // Try to select the items in-between when possible
                        val range: IntRange
                        if (selectedIndex < selectedPositions[0]) {
                            range = selectedIndex + 1..<selectedPositions[0]
                            selectedPositions[0] = selectedIndex
                        } else if (selectedIndex > selectedPositions[1]) {
                            range = (selectedPositions[1] + 1)..<selectedIndex
                            selectedPositions[1] = selectedIndex
                        } else {
                            // Just select itself
                            range = IntRange.EMPTY
                        }

                        range.forEach {
                            val inbetweenItem = get(it)
                            if (!inbetweenItem.selected) {
                                selectedEpisodeIds.add(inbetweenItem.id)
                                set(it, inbetweenItem.copy(selected = true))
                            }
                        }
                    }
                } else if (!fromLongPress) {
                    if (!selected) {
                        if (selectedIndex == selectedPositions[0]) {
                            selectedPositions[0] = indexOfFirst { it.selected }
                        } else if (selectedIndex == selectedPositions[1]) {
                            selectedPositions[1] = indexOfLast { it.selected }
                        }
                    } else {
                        if (selectedIndex < selectedPositions[0]) {
                            selectedPositions[0] = selectedIndex
                        } else if (selectedIndex > selectedPositions[1]) {
                            selectedPositions[1] = selectedIndex
                        }
                    }
                }
            }
            successState.copy(episodes = newEpisodes)
        }
    }

    fun toggleAllSelection(selected: Boolean) {
        updateSuccessState { successState ->
            val newEpisodes = successState.episodes.map {
                selectedEpisodeIds.addOrRemove(it.id, selected)
                it.copy(selected = selected)
            }
            selectedPositions[0] = -1
            selectedPositions[1] = -1
            successState.copy(episodes = newEpisodes)
        }
    }

    fun invertSelection() {
        updateSuccessState { successState ->
            val newEpisodes = successState.episodes.map {
                selectedEpisodeIds.addOrRemove(it.id, !it.selected)
                it.copy(selected = !it.selected)
            }
            selectedPositions[0] = -1
            selectedPositions[1] = -1
            successState.copy(episodes = newEpisodes)
        }
    }

    // Episodes list - end

    // AY -->
    // Related anime - start

    private fun observeRelatedAnime() {
        viewModelScope.launchIO {
            getRelatedAnime.subscribe(animeId)
                .distinctUntilChanged()
                .collectLatest { relations ->
                    updateSuccessState { it.copy(relatedAnime = relations) }
                }
        }
    }

    fun syncRelatedAnime(forceRefresh: Boolean = false) {
        val state = successState ?: return
        if (!state.source.supportsRelatedAnime) return

        viewModelScope.launchIO {
            updateSuccessState { it.copy(isLoadingRelatedAnime = true) }
            try {
                syncRelatedAnimeWithSource.await(state.anime, forceRefresh)
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e) { "Failed to fetch related anime" }
            } finally {
                updateSuccessState { it.copy(isLoadingRelatedAnime = false) }
            }
        }
    }

    // Related anime - end
    // <-- AY

    // Track sheet - start

    private fun observeTrackers() {
        val anime = successState?.anime ?: return

        viewModelScope.launchIO {
            combine(
                getTracks.subscribe(anime.id).catch { logcat(LogPriority.ERROR, it) },
                trackerManager.loggedInTrackersFlow(),
            ) { animeTracks, loggedInTrackers ->
                // Show only if the service supports this anime's source
                val supportedTrackers = loggedInTrackers.filter { (it as? EnhancedTracker)?.accept(source!!) ?: true }
                    // AM -->
                    // For now, only enhanced trackers supports season tracking to sync the seasons.
                    // This could probably be fleshed out later.
                    .filter { anime.fetchType == FetchType.Episodes || it is EnhancedTracker }
                // <-- AM
                val supportedTrackerIds = supportedTrackers.map { it.id }.toHashSet()
                val supportedTrackerTracks = animeTracks.filter { it.trackerId in supportedTrackerIds }
                supportedTrackerTracks.size to supportedTrackers.isNotEmpty()
            }
                .distinctUntilChanged()
                .collectLatest { (trackingCount, hasLoggedInTrackers) ->
                    updateSuccessState {
                        it.copy(
                            trackingCount = trackingCount,
                            hasLoggedInTrackers = hasLoggedInTrackers,
                        )
                    }
                }
        }

        // AY -->
        viewModelScope.launchIO {
            combine(
                getTracks.subscribe(anime.id).catch { logcat(LogPriority.ERROR, it) },
                trackerManager.loggedInTrackersFlow(),
            ) { animeTracks, loggedInTrackers ->
                loggedInTrackers
                    .map { service -> TrackItem(animeTracks.find { it.trackerId == service.id }, service) }
            }
                .distinctUntilChanged()
                .collectLatest { trackItems ->
                    updateAiringTime(anime, trackItems, manualFetch = false)
                }
        }
        // <-- AY
    }

    // AY -->
    private suspend fun updateAiringTime(
        anime: Anime,
        trackItems: List<TrackItem>,
        manualFetch: Boolean,
    ) {
        val airingEpisodeData = AniChartApi().loadAiringTime(anime, trackItems, manualFetch)
        setAnimeViewerFlags.awaitSetNextEpisodeAiring(anime.id, airingEpisodeData)
        updateSuccessState { it.copy(nextAiringEpisode = airingEpisodeData) }
    }
    // <-- AY

    // Track sheet - end

    sealed interface Dialog {
        data class ChangeCategory(
            val anime: Anime,
            val initialSelection: List<CheckboxState<Category>>,
        ) : Dialog
        data class DeleteEpisodes(val episodes: List<Episode>) : Dialog
        data class DuplicateAnime(val anime: Anime, val duplicates: List<AnimeWithEpisodeCount>) : Dialog
        data class Migrate(val target: Anime, val current: Anime) : Dialog
        data class SetFetchInterval(val anime: Anime) : Dialog

        // AY -->
        data class ShowQualities(val episode: Episode, val anime: Anime, val source: AnimeSource) : Dialog
        // <-- AY

        // AM (CUSTOM_INFORMATION) -->
        data class EditAnimeInfo(val anime: Anime) : Dialog

        // <-- AM (CUSTOM_INFORMATION)
        data object ChangeAnimeSkipIntro : Dialog

        // AY -->
        data object EpisodeSettingsSheet : Dialog
        data object SeasonSettingsSheet : Dialog

        // <-- AY
        data object TrackSheet : Dialog
        data object FullImages : Dialog
    }

    fun dismissDialog() {
        updateSuccessState { it.copy(dialog = null) }
    }

    fun showDeleteEpisodeDialog(episodes: List<Episode>) {
        updateSuccessState { it.copy(dialog = Dialog.DeleteEpisodes(episodes)) }
    }

    fun showSettingsDialog() {
        updateSuccessState {
            // AY -->
            when (it.anime.fetchType) {
                FetchType.Seasons -> it.copy(dialog = Dialog.SeasonSettingsSheet)
                FetchType.Episodes -> it.copy(dialog = Dialog.EpisodeSettingsSheet)
            }
            // <-- AY
        }
    }

    fun showTrackDialog() {
        updateSuccessState { it.copy(dialog = Dialog.TrackSheet) }
    }

    fun showImagesDialog() {
        updateSuccessState { it.copy(dialog = Dialog.FullImages) }
    }

    fun showMigrateDialog(duplicate: Anime) {
        val anime = successState?.anime ?: return
        updateSuccessState { it.copy(dialog = Dialog.Migrate(target = anime, current = duplicate)) }
    }

    fun setExcludedScanlators(excludedScanlators: Set<String>) {
        viewModelScope.launchIO {
            setExcludedScanlators.await(animeId, excludedScanlators)
        }
    }

    // AY -->
    fun showAnimeSkipIntroDialog() {
        updateSuccessState { it.copy(dialog = Dialog.ChangeAnimeSkipIntro) }
    }

    private fun showQualitiesDialog(episode: Episode) {
        updateSuccessState { it.copy(dialog = Dialog.ShowQualities(episode, it.anime, it.source)) }
    }
    // <-- AY

    // AM (CUSTOM_INFORMATION) -->
    fun showEditAnimeInfoDialog() {
        updateSuccessState { it.copy(dialog = Dialog.EditAnimeInfo(it.anime)) }
    }
    // <-- AM (CUSTOM_INFORMATION)

    sealed interface State {
        @Immutable
        data object Loading : State

        @Immutable
        data class Success(
            val anime: Anime,
            val source: AnimeSource,
            val isFromSource: Boolean,
            val episodes: List<EpisodeList.Item>,
            // AY -->
            val seasons: List<AnimeSeasonItem>,
            // <-- AY
            val availableScanlators: Set<String>,
            val excludedScanlators: Set<String>,
            val trackingCount: Int = 0,
            val hasLoggedInTrackers: Boolean = false,
            // AY -->
            val relatedAnime: List<AnimeRelationGroup> = emptyList(),
            val isLoadingRelatedAnime: Boolean = false,
            // <-- AY
            // AM -->
            val isSyncingTrackers: Boolean = false,
            // <-- AM
            val isRefreshingData: Boolean = false,
            val dialog: Dialog? = null,
            val hasPromptedToAddBefore: Boolean = false,
            val hideMissingEpisodes: Boolean = false,
            // AY -->
            val trackItems: List<TrackItem> = emptyList(),
            val nextAiringEpisode: Pair<Int, Long> = Pair(
                anime.nextEpisodeToAir,
                anime.nextEpisodeAiringAt,
            ),
            // <-- AY
        ) : State {
            // AY -->
            val processedSeasons by lazy {
                seasons.applySeasonFilters(anime).toList()
            }
            // <-- AY

            val processedEpisodes by lazy {
                episodes.applyFilters(anime).toList()
            }

            val isAnySelected by lazy {
                episodes.fastAny { it.selected }
            }

            val episodeListItems by lazy {
                if (hideMissingEpisodes) {
                    return@lazy processedEpisodes
                }

                processedEpisodes.insertSeparators { before, after ->
                    val (lowerEpisode, higherEpisode) = if (anime.sortDescending()) {
                        after to before
                    } else {
                        before to after
                    }
                    if (higherEpisode == null) return@insertSeparators null

                    if (lowerEpisode == null) {
                        floor(higherEpisode.episode.episodeNumber)
                            .toInt()
                            .minus(1)
                            .coerceAtLeast(0)
                    } else {
                        calculateEpisodeGap(higherEpisode.episode, lowerEpisode.episode)
                    }
                        .takeIf { it > 0 }
                        ?.let { missingCount ->
                            EpisodeList.MissingCount(
                                id = "${lowerEpisode?.id}-${higherEpisode.id}",
                                count = missingCount,
                            )
                        }
                }
            }

            // AY -->
            val trackingAvailable: Boolean
                get() = trackItems.isNotEmpty()

            val airingEpisodeNumber: Double
                get() = nextAiringEpisode.first.toDouble()

            val airingTime: Long
                get() = nextAiringEpisode.second.times(1000L).minus(
                    Calendar.getInstance().timeInMillis,
                )
            val showPreviews: Boolean
                get() = anime.showPreviews()

            val showSummaries: Boolean
                get() = anime.showSummaries()
            // <-- AY

            val scanlatorFilterActive: Boolean
                get() = excludedScanlators.intersect(availableScanlators).isNotEmpty()

            val filterActive: Boolean
                // AY -->
                get() = scanlatorFilterActive || when (anime.fetchType) {
                    FetchType.Episodes -> anime.episodesFiltered()
                    FetchType.Seasons -> anime.seasonsFiltered()
                }
            // <-- AY

            /**
             * Applies the view filters to the list of episodes obtained from the database.
             * @return an observable of the list of episodes filtered and sorted.
             */
            private fun List<EpisodeList.Item>.applyFilters(anime: Anime): Sequence<EpisodeList.Item> {
                val isLocalAnime = anime.isLocal()
                val unseenFilter = anime.unseenFilter
                val downloadedFilter = anime.downloadedFilter
                val bookmarkedFilter = anime.bookmarkedFilter
                // AY -->
                val fillermarkedFilter = anime.fillermarkedFilter
                // <-- AY
                return asSequence()
                    .filter { (episode) -> applyFilter(unseenFilter) { !episode.seen } }
                    .filter { (episode) -> applyFilter(bookmarkedFilter) { episode.bookmark } }
                    // AY -->
                    .filter { (episode) -> applyFilter(fillermarkedFilter) { episode.fillermark } }
                    // <-- AY
                    .filter { applyFilter(downloadedFilter) { it.isDownloaded || isLocalAnime } }
                    .sortedWith { (episode1), (episode2) -> getEpisodeSort(anime).invoke(episode1, episode2) }
            }

            // AY -->
            private fun List<AnimeSeasonItem>.applySeasonFilters(anime: Anime): Sequence<AnimeSeasonItem> {
                val unseenFilter = anime.seasonUnseenFilter
                val downloadedFilter = anime.seasonDownloadedFilter
                val startedFilter = anime.seasonStartedFilter
                val completedFilter = anime.seasonCompletedFilter
                val bookmarkedFilter = anime.seasonBookmarkedFilter
                val fillermarkedFilter = anime.seasonFillermarkedFilter

                val comparator = getSeasonSortComparator(anime)
                    .let { if (anime.seasonSortDescending()) it.reversed() else it }
                    .thenComparator(seasonSortAlphabetically)

                return asSequence()
                    .filter { (season) -> applyFilter(unseenFilter) { !season.seen } }
                    .filter { (season) -> applyFilter(startedFilter) { season.hasStarted } }
                    .filter { (season) ->
                        applyFilter(completedFilter) { season.anime.status.toInt() == SAnime.COMPLETED }
                    }
                    .filter { (season) -> applyFilter(bookmarkedFilter) { season.hasBookmarks } }
                    .filter { (season) -> applyFilter(fillermarkedFilter) { season.hasFillermarks } }
                    .filter { applyFilter(downloadedFilter) { it.downloadCount > 0 || it.seasonAnime.anime.isLocal() } }
                    .sortedWith(compareBy(comparator) { it.seasonAnime })
                    .map {
                        val itemAnime = it.seasonAnime.anime
                        AnimeSeasonItem(
                            seasonAnime = it.seasonAnime,
                            downloadCount = if (anime.seasonDownloadedOverlay) it.downloadCount else -1L,
                            unseenCount = if (anime.seasonUnseenOverlay) it.unseenCount else -1L,
                            isLocal = anime.seasonLocalOverlay && it.isLocal,
                            sourceLanguage = if (anime.seasonLangOverlay) it.sourceLanguage else "",
                            showContinueOverlay =
                            anime.seasonContinueOverlay &&
                                it.unseenCount > 0 &&
                                itemAnime.fetchType == FetchType.Episodes,
                        )
                    }
            }
            // <-- AY
        }
    }
}

@Immutable
sealed class EpisodeList {
    @Immutable
    data class MissingCount(
        val id: String,
        val count: Int,
    ) : EpisodeList()

    @Immutable
    data class Item(
        val episode: Episode,
        val downloadState: Download.State,
        val downloadProgress: Int,
        // AM (FILE_SIZE) -->
        var fileSize: Long? = null,
        // <-- AM (FILE_SIZE)
        val selected: Boolean = false,
    ) : EpisodeList() {
        val id = episode.id
        val isDownloaded = downloadState == Download.State.DOWNLOADED
    }
}
