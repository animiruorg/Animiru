package eu.kanade.tachiyomi.ui.updates

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.util.fastFilter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.core.preference.asState
import eu.kanade.core.util.addOrRemove
import eu.kanade.core.util.insertSeparators
import eu.kanade.domain.episode.interactor.SetSeenStatus
import eu.kanade.presentation.anime.components.EpisodeDownloadAction
import eu.kanade.presentation.updates.UpdatesUiModel
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.util.lang.toLocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import logcat.LogPriority
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.anime.interactor.GetAnime
import tachiyomi.domain.anime.model.applyFilter
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.episode.interactor.GetEpisode
import tachiyomi.domain.episode.interactor.UpdateEpisode
import tachiyomi.domain.episode.model.EpisodeUpdate
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.updates.interactor.GetUpdates
import tachiyomi.domain.updates.model.UpdatesWithRelations
import tachiyomi.domain.updates.service.UpdatesPreferences
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class UpdatesViewModel(
    private val context: Context,
    private val sourceManager: SourceManager,
    private val downloadManager: DownloadManager,
    private val downloadCache: DownloadCache,
    private val updateEpisode: UpdateEpisode,
    private val setSeenStatus: SetSeenStatus,
    private val getUpdates: GetUpdates,
    private val getAnime: GetAnime,
    private val getEpisode: GetEpisode,
    private val libraryPreferences: LibraryPreferences,
    private val updatesPreferences: UpdatesPreferences,
    // AY -->
    downloadPreferences: DownloadPreferences,
    // <-- AY
) : ViewModel() {

    val snackbarHostState: SnackbarHostState = SnackbarHostState()

    private val _events: Channel<Event> = Channel(Int.MAX_VALUE)
    val events: Flow<Event> = _events.receiveAsFlow()

    val lastUpdated by libraryPreferences.lastUpdatedTimestamp.asState(viewModelScope)

    // AY -->
    val useExternalDownloader = downloadPreferences.useExternalDownloader.get()
    // <-- AY

    // First and last selected index in list
    private val selectedPositions: Array<Int> = arrayOf(-1, -1)
    private val selectedEpisodeIds = MutableStateFlow(emptySet<Long>())

    private val dialog = MutableStateFlow<Dialog?>(null)

    private val downloadStates = MutableStateFlow(emptyMap<Long, DownloadProgress>())

    init {
        viewModelScope.launchIO {
            merge(downloadManager.statusFlow(), downloadManager.progressFlow())
                .catch { logcat(LogPriority.ERROR, it) }
                .collect(this@UpdatesViewModel::updateDownloadState)
        }
    }

    private fun updateDownloadState(download: Download) {
        val episodeId = download.episode.id
        downloadStates.update {
            // Terminal states are derived by the queried item itself, so drop the override instead
            // of letting it outlive reality, e.g. showing a since deleted episode as downloaded.
            if (download.status == Download.State.NOT_DOWNLOADED || download.status == Download.State.DOWNLOADED) {
                it - episodeId
            } else {
                it + (episodeId to DownloadProgress(download.status, download.progress))
            }
        }
    }

    // Set date limit for recent episodes
    val limit = Clock.System.now().minus(3, DateTimeUnit.MONTH, TimeZone.currentSystemDefault())

    private val hasActiveFilters = getUpdatesItemPreferenceFlow()
        .map { prefs ->
            listOf(
                prefs.filterUnseen,
                prefs.filterDownloaded,
                prefs.filterStarted,
                prefs.filterBookmarked,
            )
                .any { it != TriState.DISABLED } ||
                prefs.filterExcludedScanlators ||
                listOf(
                    prefs.filterIncludedCategories,
                    prefs.filterExcludedCategories,
                )
                    .any { it.isNotEmpty() }
        }
        .distinctUntilChanged()

    private val updateItems = combine(
        // needed for SQL filters (unseen, started, bookmarked, etc)
        getUpdatesItemPreferenceFlow()
            .distinctUntilChanged()
            .flatMapLatest {
                getUpdates.subscribe(
                    Clock.System.now().minus(3, DateTimeUnit.MONTH, TimeZone.currentSystemDefault()),
                    unseen = it.filterUnseen.toBooleanOrNull(),
                    started = it.filterStarted.toBooleanOrNull(),
                    bookmarked = it.filterBookmarked.toBooleanOrNull(),
                    // AY -->
                    fillermarked = it.filterFillermarked.toBooleanOrNull(),
                    // <-- AY
                    hideExcludedScanlators = it.filterExcludedScanlators,
                    includedCategories = it.filterIncludedCategories,
                    excludedCategories = it.filterExcludedCategories,
                ).distinctUntilChanged()
            },
        downloadCache.changes,
        downloadManager.queueState,
        // needed for Kotlin filters (downloaded)
        getUpdatesItemPreferenceFlow().distinctUntilChanged { old, new ->
            old.filterDownloaded == new.filterDownloaded
        },
    ) { updates, _, _, itemPreferences ->
        updates
            .toUpdateItems()
            .applyFilters(itemPreferences)
    }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), null)

    val state: StateFlow<State> = combine(
        updateItems,
        selectedEpisodeIds,
        downloadStates,
        dialog,
        hasActiveFilters,
    ) { items, selectedIds, downloads, dialog, hasActiveFilters ->
        State(
            isLoading = items == null,
            hasActiveFilters = hasActiveFilters,
            items = items.orEmpty().map { item ->
                val download = downloads[item.update.episodeId]
                item.copy(
                    selected = item.update.episodeId in selectedIds,
                    downloadStateProvider = if (download != null) {
                        { download.status }
                    } else {
                        item.downloadStateProvider
                    },
                    downloadProgressProvider = if (download != null) {
                        { download.progress }
                    } else {
                        item.downloadProgressProvider
                    },
                )
            },
            dialog = dialog,
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), State())

    private fun List<UpdatesItem>.applyFilters(
        preferences: ItemPreferences,
    ): List<UpdatesItem> {
        val filterDownloaded = preferences.filterDownloaded

        val filterFnDownloaded: (UpdatesItem) -> Boolean = {
            applyFilter(filterDownloaded) {
                it.downloadStateProvider() == Download.State.DOWNLOADED
            }
        }

        return fastFilter {
            filterFnDownloaded(it)
        }
    }

    private fun List<UpdatesWithRelations>.toUpdateItems(): List<UpdatesItem> {
        return this
            .map { update ->
                val activeDownload = downloadManager.getQueuedDownloadOrNull(update.episodeId)
                val downloaded = downloadManager.isEpisodeDownloaded(
                    update.episodeName,
                    update.scanlator,
                    update.episodeUrl,
                    // AM (CUSTOM_INFORMATION) -->
                    update.ogAnimeTitle,
                    // <-- AM (CUSTOM_INFORMATION)
                    update.sourceId,
                )
                val downloadState = when {
                    activeDownload != null -> activeDownload.status
                    downloaded -> Download.State.DOWNLOADED
                    else -> Download.State.NOT_DOWNLOADED
                }
                UpdatesItem(
                    update = update,
                    downloadStateProvider = { downloadState },
                    downloadProgressProvider = { activeDownload?.progress ?: 0 },
                    // AM (FILE_SIZE) -->
                    fileSize = null,
                    // <-- AM (FILE_SIZE)
                )
            }
    }

    fun updateLibrary(): Boolean {
        val started = LibraryUpdateJob.startNow(context)
        viewModelScope.launch {
            _events.send(Event.LibraryUpdateTriggered(started))
        }
        return started
    }

    fun downloadEpisodes(items: List<UpdatesItem>, action: EpisodeDownloadAction) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            when (action) {
                EpisodeDownloadAction.START -> {
                    downloadEpisodes(items)
                    if (items.any { it.downloadStateProvider() == Download.State.ERROR }) {
                        downloadManager.startDownloads()
                    }
                }
                EpisodeDownloadAction.START_NOW -> {
                    val episodeId = items.singleOrNull()?.update?.episodeId ?: return@launch
                    startDownloadingNow(episodeId)
                }
                EpisodeDownloadAction.CANCEL -> {
                    val episodeId = items.singleOrNull()?.update?.episodeId ?: return@launch
                    cancelDownload(episodeId)
                }
                EpisodeDownloadAction.DELETE -> {
                    deleteEpisodes(items)
                }
                // AY -->
                EpisodeDownloadAction.SHOW_QUALITIES -> {
                    val update = items.singleOrNull()?.update ?: return@launch
                    showQualitiesDialog(update)
                }
                // <-- AY
            }
            toggleAllSelection(false)
        }
    }

    private fun startDownloadingNow(episodeId: Long) {
        downloadManager.startDownloadNow(episodeId)
    }

    private fun cancelDownload(episodeId: Long) {
        val activeDownload = downloadManager.getQueuedDownloadOrNull(episodeId) ?: return
        downloadManager.cancelQueuedDownloads(listOf(activeDownload))
        updateDownloadState(activeDownload.apply { status = Download.State.NOT_DOWNLOADED })
    }

    /**
     * Mark the selected updates list as seen/unseen.
     * @param updates the list of selected updates.
     * @param seen whether to mark episodes as seen or unseen.
     */
    fun markUpdatesSeen(updates: List<UpdatesItem>, seen: Boolean) {
        viewModelScope.launchIO {
            setSeenStatus.await(
                seen = seen,
                episodes = updates
                    .mapNotNull { getEpisode.await(it.update.episodeId) }
                    .toTypedArray(),
            )
        }
        toggleAllSelection(false)
    }

    /**
     * Bookmarks the given list of episodes.
     * @param updates the list of episodes to bookmark.
     */
    fun bookmarkUpdates(updates: List<UpdatesItem>, bookmark: Boolean) {
        viewModelScope.launchIO {
            updates
                .filterNot { it.update.bookmark == bookmark }
                .map { EpisodeUpdate(it.update.episodeId) { this.bookmark = bookmark } }
                .let { updateEpisode.awaitAll(it) }
        }
        toggleAllSelection(false)
    }

    // AY -->

    /**
     * Fillermarks the given list of episodes.
     * @param updates the list of episodes to fillermark.
     */
    fun fillermarkUpdates(updates: List<UpdatesItem>, fillermark: Boolean) {
        viewModelScope.launchIO {
            updates
                .filterNot { it.update.fillermark == fillermark }
                .map { EpisodeUpdate(it.update.episodeId) { this.fillermark = fillermark } }
                .let { updateEpisode.awaitAll(it) }
        }
        toggleAllSelection(false)
    }
    // <-- AY

    /**
     * Downloads the given list of episodes with the manager.
     * @param updatesItem the list of episodes to download.
     */
    private fun downloadEpisodes(updatesItem: List<UpdatesItem>, alt: Boolean = false) {
        viewModelScope.launchNonCancellable {
            val groupedUpdates = updatesItem.groupBy { it.update.animeId }.values
            for (updates in groupedUpdates) {
                val animeId = updates.first().update.animeId
                val anime = getAnime.await(animeId) ?: continue
                // Don't download if source isn't available
                sourceManager.get(anime.source) ?: continue
                val episodes = updates.mapNotNull { getEpisode.await(it.update.episodeId) }
                downloadManager.downloadEpisodes(anime, episodes, true, alt)
            }
        }
    }

    /**
     * Delete selected episodes
     *
     * @param updatesItem list of episodes
     */
    fun deleteEpisodes(updatesItem: List<UpdatesItem>) {
        viewModelScope.launchNonCancellable {
            updatesItem
                .groupBy { it.update.animeId }
                .entries
                .forEach { (animeId, updates) ->
                    val anime = getAnime.await(animeId) ?: return@forEach
                    val source = sourceManager.get(anime.source) ?: return@forEach
                    val episodes = updates.mapNotNull { getEpisode.await(it.update.episodeId) }
                    downloadManager.deleteEpisodes(episodes, anime, source)
                }
        }
        toggleAllSelection(false)
    }

    fun showConfirmDeleteEpisodes(updatesItem: List<UpdatesItem>) {
        setDialog(Dialog.DeleteConfirmation(updatesItem))
    }

    // AY -->
    private fun showQualitiesDialog(update: UpdatesWithRelations) {
        setDialog(
            Dialog.ShowQualities(
                update.episodeName,
                update.episodeId,
                update.animeId,
                update.sourceId,
            ),
        )
    }
    // <-- AY

    fun toggleSelection(
        item: UpdatesItem,
        selected: Boolean,
        fromLongPress: Boolean = false,
    ) {
        val items = state.value.items
        val selectedIndex = items.indexOfFirst { it.update.episodeId == item.update.episodeId }
        if (selectedIndex < 0) return

        // Read selection from its own flow, not the derived items, which lag behind it.
        val currentSelection = selectedEpisodeIds.value
        if ((item.update.episodeId in currentSelection) == selected) return

        // Off the visible items, not the id set, which can retain ids filtered out of the list
        val firstSelection = items.none { it.selected }
        val newSelection = currentSelection.toHashSet()
        newSelection.addOrRemove(item.update.episodeId, selected)

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

                range.forEach { newSelection.add(items[it].update.episodeId) }
            }
        } else if (!fromLongPress) {
            if (!selected) {
                if (selectedIndex == selectedPositions[0]) {
                    selectedPositions[0] = items.indexOfFirst { it.update.episodeId in newSelection }
                } else if (selectedIndex == selectedPositions[1]) {
                    selectedPositions[1] = items.indexOfLast { it.update.episodeId in newSelection }
                }
            } else {
                if (selectedIndex < selectedPositions[0]) {
                    selectedPositions[0] = selectedIndex
                } else if (selectedIndex > selectedPositions[1]) {
                    selectedPositions[1] = selectedIndex
                }
            }
        }

        selectedEpisodeIds.update { newSelection }
    }

    fun toggleAllSelection(selected: Boolean) {
        val ids = if (selected) state.value.items.map { it.update.episodeId }.toSet() else emptySet()
        selectedEpisodeIds.update { ids }

        selectedPositions[0] = -1
        selectedPositions[1] = -1
    }

    fun invertSelection() {
        val current = selectedEpisodeIds.value
        val ids = state.value.items
            .map { it.update.episodeId }
            .filterNot { it in current }
            .toSet()
        selectedEpisodeIds.update { ids }

        selectedPositions[0] = -1
        selectedPositions[1] = -1
    }

    fun setDialog(dialog: Dialog?) {
        this.dialog.update { dialog }
    }

    fun resetNewUpdatesCount() {
        libraryPreferences.newUpdatesCount.set(0)
    }

    private fun getUpdatesItemPreferenceFlow(): Flow<ItemPreferences> {
        return combine(
            updatesPreferences.filterDownloaded.changes(),
            updatesPreferences.filterUnseen.changes(),
            updatesPreferences.filterStarted.changes(),
            updatesPreferences.filterBookmarked.changes(),
            updatesPreferences.filterFillermarked.changes(),
            updatesPreferences.filterExcludedScanlators.changes(),
            updatesPreferences.filterIncludedCategories.changes(),
            updatesPreferences.filterExcludedCategories.changes(),
        ) {
            @Suppress("UNCHECKED_CAST")
            ItemPreferences(
                filterDownloaded = it[0] as TriState,
                filterUnseen = it[1] as TriState,
                filterStarted = it[2] as TriState,
                filterBookmarked = it[3] as TriState,
                // AY -->
                filterFillermarked = it[4] as TriState,
                // <-- AY
                filterExcludedScanlators = it[5] as Boolean,
                filterIncludedCategories = it[6] as List<Long>,
                filterExcludedCategories = it[7] as List<Long>,
            )
        }
    }

    fun showFilterDialog() {
        dialog.update { Dialog.FilterSheet }
    }

    @Immutable
    private data class ItemPreferences(
        val filterDownloaded: TriState,
        val filterUnseen: TriState,
        val filterStarted: TriState,
        val filterBookmarked: TriState,
        val filterFillermarked: TriState,
        val filterExcludedScanlators: Boolean,
        val filterIncludedCategories: List<Long>,
        val filterExcludedCategories: List<Long>,
    )

    private data class DownloadProgress(val status: Download.State, val progress: Int)

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val hasActiveFilters: Boolean = false,
        val items: List<UpdatesItem> = listOf(),
        val dialog: Dialog? = null,
    ) {
        val selected = items.filter { it.selected }
        val selectionMode = selected.isNotEmpty()

        fun getUiModel(): List<UpdatesUiModel> {
            return items
                .map { UpdatesUiModel.Item(it) }
                .insertSeparators { before, after ->
                    val beforeDate = before?.item?.update?.dateFetch?.toLocalDate()
                    val afterDate = after?.item?.update?.dateFetch?.toLocalDate()
                    when {
                        beforeDate != afterDate && afterDate != null -> UpdatesUiModel.Header(afterDate)
                        // Return null to avoid adding a separator between two items.
                        else -> null
                    }
                }
        }
    }

    sealed interface Dialog {
        data class DeleteConfirmation(val toDelete: List<UpdatesItem>) : Dialog
        data object FilterSheet : Dialog

        // AY -->
        data class ShowQualities(
            val episodeTitle: String,
            val episodeId: Long,
            val animeId: Long,
            val sourceId: Long,
        ) : Dialog
        // <-- AY
    }

    sealed interface Event {
        data object InternalError : Event
        data class LibraryUpdateTriggered(val started: Boolean) : Event
    }
}

private fun TriState.toBooleanOrNull(): Boolean? {
    return when (this) {
        TriState.DISABLED -> null
        TriState.ENABLED_IS -> true
        TriState.ENABLED_NOT -> false
    }
}

@Immutable
data class UpdatesItem(
    val update: UpdatesWithRelations,
    val downloadStateProvider: () -> Download.State,
    val downloadProgressProvider: () -> Int,
    val selected: Boolean = false,
    // AM (FILE_SIZE) -->
    var fileSize: Long?,
    // <-- AM (FILE_SIZE)
)
