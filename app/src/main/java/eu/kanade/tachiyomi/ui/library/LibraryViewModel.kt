package eu.kanade.tachiyomi.ui.library

import androidx.compose.runtime.Immutable
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.core.preference.PreferenceMutableState
import eu.kanade.core.preference.asState
import eu.kanade.core.util.fastFilterNot
import eu.kanade.domain.anime.interactor.UpdateAnime
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.episode.interactor.SetSeenStatus
import eu.kanade.presentation.anime.DownloadAction
import eu.kanade.presentation.library.components.LibraryToolbarTitle
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.data.cache.BackgroundCache
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.track.TrackStatus
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.util.episode.getNextUnseen
import eu.kanade.tachiyomi.util.removeBackgrounds
import eu.kanade.tachiyomi.util.removeCovers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import mihon.core.common.utils.mutate
import mihon.domain.library.model.search.QueryNode
import mihon.feature.library.matches
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.util.lang.compareToWithCollator
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.domain.anime.interactor.GetLibraryAnime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.model.AnimeUpdate
import tachiyomi.domain.anime.model.applyFilter
import tachiyomi.domain.category.interactor.GetVisibleCategories
import tachiyomi.domain.category.interactor.SetAnimeCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.episode.interactor.GetBookmarkedEpisodesByAnimeId
import tachiyomi.domain.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.history.interactor.GetNextEpisodes
import tachiyomi.domain.library.model.LibraryAnime
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.model.LibraryGroup
import tachiyomi.domain.library.model.LibrarySort
import tachiyomi.domain.library.model.sort
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.GetTracksPerAnime
import tachiyomi.domain.track.model.Track
import tachiyomi.i18n.MR
import tachiyomi.i18n.animiru.AMMR
import tachiyomi.source.local.LocalSource
import tachiyomi.source.local.isLocal
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class LibraryViewModel(
    private val getLibraryAnime: GetLibraryAnime,
    // AY -->
    private val getVisibleCategories: GetVisibleCategories,
    // <-- AY
    private val getTracksPerAnime: GetTracksPerAnime,
    private val getNextEpisodes: GetNextEpisodes,
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId,
    private val getBookmarkedEpisodesByAnimeId: GetBookmarkedEpisodesByAnimeId,
    private val setSeenStatus: SetSeenStatus,
    private val updateAnime: UpdateAnime,
    private val setAnimeCategories: SetAnimeCategories,
    private val preferences: BasePreferences,
    private val libraryPreferences: LibraryPreferences,
    private val coverCache: CoverCache,
    // AY -->
    private val backgroundCache: BackgroundCache,
    // <-- AY
    private val sourceManager: SourceManager,
    private val downloadManager: DownloadManager,
    private val downloadCache: DownloadCache,
    private val trackerManager: TrackerManager,
    // AM (GROUPING) -->
    private val getTracks: GetTracks,
    // <-- AM (GROUPING)
) : ViewModel() {

    private val searchQuery = MutableStateFlow<String?>(null)

    private val selection = MutableStateFlow(emptySet</* Anime */ Long>())

    private val dialog = MutableStateFlow<Dialog?>(null)

    private val activeCategoryIndex = MutableStateFlow(libraryPreferences.lastUsedCategory.get())

    private val displayPreferences = combine(
        libraryPreferences.categoryTabs.changes(),
        libraryPreferences.categoryNumberOfItems.changes(),
        libraryPreferences.showContinueWatchingButton.changes(),
        ::DisplayPreferences,
    )

    private val hasActiveFilters = combine(
        getLibraryItemPreferencesFlow(),
        getTrackingFiltersFlow(),
    ) { prefs, trackFilters ->
        listOf(
            prefs.filterDownloaded,
            prefs.filterUnseen,
            prefs.filterStarted,
            prefs.filterBookmarked,
            prefs.filterCompleted,
            prefs.filterIntervalCustom,
            *trackFilters.values.toTypedArray(),
        )
            .any { it != TriState.DISABLED }
    }
        .distinctUntilChanged()

    // Shared separately so search, selection and dialog changes still reach [state] before the
    // first query result, and so returning to the tab doesn't flash empty while it restarts.
    private val library = eu.kanade.core.util.combine(
        searchQuery.debounce(0.25.seconds),
        // AY -->
        getVisibleCategories.subscribe(),
        // <-- AY
        getFavoritesFlow(),
        combine(getTracksPerAnime.subscribe(), getTrackingFiltersFlow(), ::Pair),
        getLibraryItemPreferencesFlow(),
        // AM (GROUPING) -->
        combine(libraryPreferences.groupLibraryBy.changes(), libraryPreferences.sortingMode.changes(), ::Pair),
        // <-- AM (GROUPING)
    ) { searchQuery, categories, favorites, (tracksMap, trackingFilters), itemPreferences, group ->
        val showSystemCategory = favorites.any { it.libraryAnime.categories.contains(0) }
        val filteredFavorites = favorites
            .applyFilters(tracksMap, trackingFilters, itemPreferences)
            .let { libraryItems ->
                if (searchQuery.isNullOrEmpty()) {
                    libraryItems
                } else {
                    val queryNode = QueryNode.from(searchQuery)
                    libraryItems.filter { queryNode.matches(it) }
                }
            }

        LibraryData(
            isInitialized = true,
            showSystemCategory = showSystemCategory,
            categories = categories,
            favorites = filteredFavorites,
            tracksMap = tracksMap,
            loggedInTrackerIds = trackingFilters.keys,
        ) to group
    }
        .distinctUntilChanged()
        .map { (data, group) ->
            Library(
                data = data,
                groupedFavorites = data.favorites
                    // AM (GROUPING) -->
                    .applyGrouping(data.categories, data.showSystemCategory, group.first)
                    .applySort(
                        favoritesById = data.favoritesById,
                        trackMap = data.tracksMap,
                        loggedInTrackerIds = data.loggedInTrackerIds,
                        groupSort = group.second.takeIf { group.first != LibraryGroup.BY_DEFAULT },
                    ),
                // <-- AM (GROUPING)
            )
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), null)

    val state: StateFlow<State> = combine(
        library,
        combine(searchQuery, selection, dialog, ::Triple),
        combine(activeCategoryIndex, displayPreferences, hasActiveFilters, ::Triple),
        // AM (GROUPING) -->
        libraryPreferences.groupLibraryBy.changes(),
        getVisibleCategories.subscribe(),
        // <-- AM (GROUPING)
    ) {
            library,
            (searchQuery, selection, dialog),
            (activeCategoryIndex, display, hasActiveFilters),
            groupType,
            categories,
        ->
        State(
            isLoading = library == null,
            searchQuery = searchQuery,
            selection = selection,
            hasActiveFilters = hasActiveFilters,
            showCategoryTabs = display.showCategoryTabs,
            showAnimeCount = display.showAnimeCount,
            showAnimeContinueButton = display.showAnimeContinueButton,
            dialog = dialog,
            // AM (GROUPING) -->
            groupType = groupType,
            hasCategories = categories.fastAny { !it.isSystemCategory },
            // <-- AM (GROUPING)
            libraryData = library?.data ?: LibraryData(),
            activeCategoryIndex = activeCategoryIndex,
            groupedFavorites = library?.groupedFavorites.orEmpty(),
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), State())

    private data class DisplayPreferences(
        val showCategoryTabs: Boolean,
        val showAnimeCount: Boolean,
        val showAnimeContinueButton: Boolean,
    )

    private data class Library(
        val data: LibraryData,
        val groupedFavorites: Map<Category, List</* LibraryItem */ Long>>,
    )

    private fun List<LibraryItem>.applyFilters(
        trackMap: Map<Long, List<Track>>,
        trackingFilter: Map<Long, TriState>,
        preferences: ItemPreferences,
    ): List<LibraryItem> {
        val downloadedOnly = preferences.globalFilterDownloaded
        val skipOutsideReleasePeriod = preferences.skipOutsideReleasePeriod
        val filterDownloaded = if (downloadedOnly) TriState.ENABLED_IS else preferences.filterDownloaded
        val filterUnseen = preferences.filterUnseen
        val filterStarted = preferences.filterStarted
        val filterBookmarked = preferences.filterBookmarked
        val filterCompleted = preferences.filterCompleted
        val filterIntervalCustom = preferences.filterIntervalCustom

        val isNotLoggedInAnyTrack = trackingFilter.isEmpty()

        val excludedTracks = trackingFilter.mapNotNull { if (it.value == TriState.ENABLED_NOT) it.key else null }
        val includedTracks = trackingFilter.mapNotNull { if (it.value == TriState.ENABLED_IS) it.key else null }
        val trackFiltersIsIgnored = includedTracks.isEmpty() && excludedTracks.isEmpty()

        val filterFnDownloaded: (LibraryItem) -> Boolean = {
            applyFilter(filterDownloaded) { it.isLocal || it.downloadCount > 0 }
        }

        val filterFnUnseen: (LibraryItem) -> Boolean = {
            applyFilter(filterUnseen) { it.libraryAnime.unseenCount > 0 }
        }

        val filterFnStarted: (LibraryItem) -> Boolean = {
            applyFilter(filterStarted) { it.libraryAnime.hasStarted }
        }

        val filterFnBookmarked: (LibraryItem) -> Boolean = {
            applyFilter(filterBookmarked) { it.libraryAnime.hasBookmarks }
        }

        val filterFnCompleted: (LibraryItem) -> Boolean = {
            applyFilter(filterCompleted) { it.libraryAnime.anime.status.toInt() == SAnime.COMPLETED }
        }

        val filterFnIntervalCustom: (LibraryItem) -> Boolean = {
            if (skipOutsideReleasePeriod) {
                applyFilter(filterIntervalCustom) { it.libraryAnime.anime.fetchInterval < 0 }
            } else {
                true
            }
        }

        val filterFnTracking: (LibraryItem) -> Boolean = tracking@{ item ->
            if (isNotLoggedInAnyTrack || trackFiltersIsIgnored) return@tracking true

            val animeTracks = trackMap[item.id].orEmpty().map { it.trackerId }

            val isExcluded = excludedTracks.isNotEmpty() && animeTracks.fastAny { it in excludedTracks }
            val isIncluded = includedTracks.isEmpty() || animeTracks.fastAny { it in includedTracks }

            !isExcluded && isIncluded
        }

        return fastFilter {
            filterFnDownloaded(it) &&
                filterFnUnseen(it) &&
                filterFnStarted(it) &&
                filterFnBookmarked(it) &&
                filterFnCompleted(it) &&
                filterFnIntervalCustom(it) &&
                filterFnTracking(it)
        }
    }

    private suspend fun List<LibraryItem>.applyGrouping(
        categories: List<Category>,
        showSystemCategory: Boolean,
        // AM (GROUPING) -->
        groupType: Int,
        // <-- AM (GROUPING)
    ): Map<Category, List</* LibraryItem */ Long>> {
        return when (groupType) {
            LibraryGroup.BY_DEFAULT -> {
                val groupCache = mutableMapOf</* Category */ Long, MutableList</* LibraryItem */ Long>>()
                forEach { item ->
                    item.libraryAnime.categories.forEach { categoryId ->
                        groupCache.getOrPut(categoryId) { mutableListOf() }.add(item.id)
                    }
                }
                categories.filter { showSystemCategory || !it.isSystemCategory }
                    .associateWith { groupCache[it.id]?.toList().orEmpty() }
            }
            // AM (GROUPING) -->
            LibraryGroup.UNGROUPED -> {
                mapOf(
                    Category(
                        0,
                        preferences.context.stringResource(AMMR.strings.ungrouped),
                        0,
                        0,
                        false,
                    ) to
                        this.map { it.id },
                )
            }
            else -> {
                getGroupedItems(
                    groupType = groupType,
                    libraryAnime = this,
                )
            }
            // <-- AM (GROUPING)
        }
    }

    private fun Map<Category, List</* LibraryItem */ Long>>.applySort(
        favoritesById: Map<Long, LibraryItem>,
        trackMap: Map<Long, List<Track>>,
        loggedInTrackerIds: Set<Long>,
        // AM (GROUPING) -->
        groupSort: LibrarySort? = null,
        // <-- AM (GROUPING)
    ): Map<Category, List</* LibraryItem */ Long>> {
        val sortAlphabetically: (LibraryItem, LibraryItem) -> Int = { anime1, anime2 ->
            val title1 = anime1.libraryAnime.anime.title.lowercase()
            val title2 = anime2.libraryAnime.anime.title.lowercase()
            title1.compareToWithCollator(title2)
        }

        val defaultTrackerScoreSortValue = -1.0
        val trackerScores by lazy {
            val trackerMap = trackerManager.getAll(loggedInTrackerIds).associateBy { e -> e.id }
            trackMap.mapValues { entry ->
                when {
                    entry.value.isEmpty() -> null
                    else ->
                        entry.value
                            .mapNotNull { trackerMap[it.trackerId]?.get10PointScore(it) }
                            .average()
                }
            }
        }

        fun LibrarySort.comparator(): Comparator<LibraryItem> = Comparator { anime1, anime2 ->
            // AM (GROUPING) -->
            val sort = groupSort ?: this
            // <-- AM (GROUPING)
            when (sort.type) {
                LibrarySort.Type.Alphabetical -> {
                    sortAlphabetically(anime1, anime2)
                }
                LibrarySort.Type.LastSeen -> {
                    anime1.libraryAnime.lastSeen.compareTo(anime2.libraryAnime.lastSeen)
                }
                LibrarySort.Type.LastUpdate -> {
                    anime1.libraryAnime.anime.lastUpdate.compareTo(anime2.libraryAnime.anime.lastUpdate)
                }
                LibrarySort.Type.UnseenCount -> when {
                    // Ensure unseen content comes first
                    anime1.libraryAnime.unseenCount == anime2.libraryAnime.unseenCount -> 0
                    anime1.libraryAnime.unseenCount == 0L -> if (this.isAscending) 1 else -1
                    anime2.libraryAnime.unseenCount == 0L -> if (this.isAscending) -1 else 1
                    else -> anime1.libraryAnime.unseenCount.compareTo(anime2.libraryAnime.unseenCount)
                }
                LibrarySort.Type.TotalEpisodes -> {
                    // AY -->
                    anime1.libraryAnime.totalCount.compareTo(anime2.libraryAnime.totalCount)
                    // <-- AY
                }
                LibrarySort.Type.LatestEpisode -> {
                    anime1.libraryAnime.latestUpload.compareTo(anime2.libraryAnime.latestUpload)
                }
                LibrarySort.Type.EpisodeFetchDate -> {
                    anime1.libraryAnime.episodeFetchedAt.compareTo(anime2.libraryAnime.episodeFetchedAt)
                }
                LibrarySort.Type.DateAdded -> {
                    compareValues(anime1.libraryAnime.anime.favoriteAt, anime2.libraryAnime.anime.favoriteAt)
                }
                LibrarySort.Type.TrackerMean -> {
                    val item1Score = trackerScores[anime1.id] ?: defaultTrackerScoreSortValue
                    val item2Score = trackerScores[anime2.id] ?: defaultTrackerScoreSortValue
                    item1Score.compareTo(item2Score)
                }
                LibrarySort.Type.AiringTime -> when {
                    anime1.libraryAnime.unseenCount != anime2.libraryAnime.unseenCount ->
                        anime1.libraryAnime.unseenCount.compareTo(anime2.libraryAnime.unseenCount)
                    anime1.libraryAnime.anime.nextEpisodeAiringAt == anime2.libraryAnime.anime.nextEpisodeAiringAt -> 0
                    anime1.libraryAnime.anime.nextEpisodeAiringAt == 0L -> if (this.isAscending) 1 else -1
                    anime2.libraryAnime.anime.nextEpisodeAiringAt == 0L -> if (this.isAscending) -1 else 1
                    else -> anime1.libraryAnime.anime.nextEpisodeAiringAt.compareTo(
                        anime2.libraryAnime.anime.nextEpisodeAiringAt,
                    )
                }
                LibrarySort.Type.Random -> {
                    error("Why Are We Still Here? Just To Suffer?")
                }
            }
        }

        return mapValues { (key, value) ->
            // AM (GROUPING) -->
            val sort = groupSort ?: key.sort
            // <-- AM (GROUPING)
            if (sort.type == LibrarySort.Type.Random) {
                return@mapValues value.shuffled(Random(libraryPreferences.randomSortSeed.get()))
            }

            val anime = value.mapNotNull { favoritesById[it] }

            val comparator = sort.comparator()
                .let { if (sort.isAscending) it else it.reversed() }
                .thenComparator(sortAlphabetically)

            anime.sortedWith(comparator).map { it.id }
        }
    }

    private fun getLibraryItemPreferencesFlow(): Flow<ItemPreferences> {
        return combine(
            libraryPreferences.downloadBadge.changes(),
            libraryPreferences.unseenBadge.changes(),
            libraryPreferences.localBadge.changes(),
            libraryPreferences.languageBadge.changes(),
            libraryPreferences.autoUpdateAnimeRestrictions.changes(),

            preferences.downloadedOnly.changes(),
            libraryPreferences.filterDownloaded.changes(),
            libraryPreferences.filterUnseen.changes(),
            libraryPreferences.filterStarted.changes(),
            libraryPreferences.filterBookmarked.changes(),
            libraryPreferences.filterCompleted.changes(),
            libraryPreferences.filterIntervalCustom.changes(),
        ) {
            ItemPreferences(
                downloadBadge = it[0] as Boolean,
                unseenBadge = it[1] as Boolean,
                localBadge = it[2] as Boolean,
                languageBadge = it[3] as Boolean,
                skipOutsideReleasePeriod = LibraryPreferences.ANIME_OUTSIDE_RELEASE_PERIOD in (it[4] as Set<*>),
                globalFilterDownloaded = it[5] as Boolean,
                filterDownloaded = it[6] as TriState,
                filterUnseen = it[7] as TriState,
                filterStarted = it[8] as TriState,
                filterBookmarked = it[9] as TriState,
                filterCompleted = it[10] as TriState,
                filterIntervalCustom = it[11] as TriState,
            )
        }
    }

    private fun getFavoritesFlow(): Flow<List<LibraryItem>> {
        return combine(
            getLibraryAnime.subscribe(),
            getLibraryItemPreferencesFlow(),
            downloadCache.changes,
        ) { libraryAnime, preferences, _ ->
            libraryAnime.map { anime ->
                LibraryItem(
                    libraryAnime = anime,
                    downloadCount = downloadManager.getDownloadCount(anime.anime),
                    unseenCount = anime.unseenCount,
                    isLocal = anime.anime.isLocal(),
                    sourceName = sourceManager.getOrStub(anime.anime.source).name.lowercase(),
                    sourceLanguage = sourceManager.getOrStub(anime.anime.source).lang,
                    badges = LibraryItem.Badges(
                        downloadCount = if (preferences.downloadBadge) {
                            downloadManager.getDownloadCount(anime.anime)
                        } else {
                            0
                        },
                        unseenCount = if (preferences.unseenBadge) {
                            anime.unseenCount
                        } else {
                            0
                        },
                        isLocal = if (preferences.localBadge) {
                            anime.anime.isLocal()
                        } else {
                            false
                        },
                        sourceLanguage = if (preferences.languageBadge) {
                            sourceManager.getOrStub(anime.anime.source).lang
                        } else {
                            ""
                        },
                    ),

                )
            }
        }
    }

    /**
     * Flow of tracking filter preferences
     *
     * @return map of track id with the filter value
     */
    private fun getTrackingFiltersFlow(): Flow<Map<Long, TriState>> {
        return trackerManager.loggedInTrackersFlow().flatMapLatest { loggedInTrackers ->
            if (loggedInTrackers.isEmpty()) {
                flowOf(emptyMap())
            } else {
                val filterFlows = loggedInTrackers.map { tracker ->
                    libraryPreferences.filterTracking(tracker.id.toInt()).changes().map { tracker.id to it }
                }
                combine(filterFlows) { it.toMap() }
            }
        }
    }

    /**
     * Returns the common categories for the given list of anime.
     *
     * @param animes the list of anime.
     */
    private suspend fun getCommonCategories(animes: List<Anime>): Collection<Category> {
        if (animes.isEmpty()) return emptyList()
        return animes
            // AY -->
            .map { getVisibleCategories.await(it.id).toSet() }
            // <-- AY
            .reduce { set1, set2 -> set1.intersect(set2) }
    }

    suspend fun getNextUnseenEpisode(anime: Anime): Episode? {
        return getEpisodesByAnimeId.await(anime.id, applyScanlatorFilter = true).getNextUnseen(anime, downloadManager)
    }

    /**
     * Returns the mix (non-common) categories for the given list of anime.
     *
     * @param animes the list of anime.
     */
    private suspend fun getMixCategories(animes: List<Anime>): Collection<Category> {
        if (animes.isEmpty()) return emptyList()
        // AY -->
        val animeCategories = animes.map { getVisibleCategories.await(it.id).toSet() }
        // <-- AY
        val common = animeCategories.reduce { set1, set2 -> set1.intersect(set2) }
        return animeCategories.flatten().distinct().subtract(common)
    }

    /**
     * Queues the amount specified of unseen episodes from the list of selected anime
     */
    fun performDownloadAction(action: DownloadAction) {
        when (action) {
            DownloadAction.NEXT_1_EPISODE -> downloadNextEpisodes(1)
            DownloadAction.NEXT_5_EPISODES -> downloadNextEpisodes(5)
            DownloadAction.NEXT_10_EPISODES -> downloadNextEpisodes(10)
            DownloadAction.NEXT_25_EPISODES -> downloadNextEpisodes(25)
            DownloadAction.UNSEEN_EPISODES -> downloadNextEpisodes(null)
            DownloadAction.BOOKMARKED_EPISODES -> downloadBookmarkedEpisodes()
        }

        clearSelection()
    }

    private fun downloadNextEpisodes(amount: Int?) {
        val animes = selectedAnime
        viewModelScope.launchNonCancellable {
            animes.forEach { anime ->
                val episodes = getNextEpisodes.await(anime.id)
                    .fastFilterNot { episode ->
                        downloadManager.getQueuedDownloadOrNull(episode.id) != null ||
                            downloadManager.isEpisodeDownloaded(
                                episode.name,
                                episode.scanlator,
                                episode.url,
                                // AM (CUSTOM_INFORMATION) -->
                                anime.ogTitle,
                                // <-- AM (CUSTOM_INFORMATION),
                                anime.source,
                            )
                    }
                    .let { if (amount != null) it.take(amount) else it }

                downloadManager.downloadEpisodes(anime, episodes)
            }
        }
    }

    private fun downloadBookmarkedEpisodes() {
        val animes = selectedAnime
        viewModelScope.launchNonCancellable {
            animes.forEach { anime ->
                val episodes = getBookmarkedEpisodesByAnimeId.await(anime.id)
                    .fastFilterNot { episode ->
                        downloadManager.getQueuedDownloadOrNull(episode.id) != null ||
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
                downloadManager.downloadEpisodes(anime, episodes)
            }
        }
    }

    /**
     * Marks animes' episodes seen status.
     */
    fun markSeenSelection(seen: Boolean) {
        val selection = selectedAnime
        viewModelScope.launchNonCancellable {
            selection.forEach { anime ->
                setSeenStatus.await(
                    anime = anime,
                    seen = seen,
                )
            }
        }
        clearSelection()
    }

    /**
     * Remove the selected anime.
     *
     * @param animes the list of anime to delete.
     * @param deleteFromLibrary whether to delete anime from library.
     * @param deleteEpisodes whether to delete downloaded episodes.
     */
    fun removeAnimes(animes: List<Anime>, deleteFromLibrary: Boolean, deleteEpisodes: Boolean) {
        viewModelScope.launchNonCancellable {
            if (deleteFromLibrary) {
                val toDelete = animes.map {
                    it.removeCovers(coverCache)
                    // AY -->
                    it.removeBackgrounds(backgroundCache)
                    // <-- AY
                    AnimeUpdate(it.id) {
                        favoriteAt = null
                    }
                }
                updateAnime.awaitAll(toDelete)
            }

            if (deleteEpisodes) {
                animes.forEach { anime ->
                    val source = sourceManager.get(anime.source) as? AnimeHttpSource
                    if (source != null) {
                        downloadManager.deleteAnime(anime, source)
                    }
                }
            }
        }
    }

    /**
     * Bulk update categories of anime using old and new common categories.
     *
     * @param animeList the list of anime to move.
     * @param addCategories the categories to add for all animes.
     * @param removeCategories the categories to remove in all animes.
     */
    fun setAnimeCategories(animeList: List<Anime>, addCategories: List<Long>, removeCategories: List<Long>) {
        viewModelScope.launchNonCancellable {
            animeList.forEach { anime ->
                // AY -->
                val categoryIds = getVisibleCategories.await(anime.id)
                    // <-- AY
                    .map { it.id }
                    .subtract(removeCategories.toSet())
                    .plus(addCategories)
                    .toList()

                setAnimeCategories.await(anime.id, categoryIds)
            }
        }
    }

    fun getDisplayMode(): PreferenceMutableState<LibraryDisplayMode> {
        return libraryPreferences.displayMode.asState(viewModelScope)
    }

    fun getColumnsForOrientation(isLandscape: Boolean): PreferenceMutableState<Int> {
        return (if (isLandscape) libraryPreferences.landscapeColumns else libraryPreferences.portraitColumns)
            .asState(viewModelScope)
    }

    fun getRandomLibraryItemForCurrentCategory(): LibraryItem? {
        val state = state.value
        return state.getItemsForCategoryId(state.activeCategory?.id).randomOrNull()
    }

    fun showSettingsDialog() {
        dialog.update { Dialog.SettingsSheet }
    }

    private var lastSelectionCategory: Long? = null

    /**
     * Reads from [selection] rather than [state], which is derived asynchronously and can still
     * hold the previous selection immediately after a toggle.
     */
    private val selectedAnime: List<Anime>
        get() {
            val favoritesById = state.value.libraryData.favoritesById
            return selection.value.mapNotNull { favoritesById[it]?.libraryAnime?.anime }
        }

    fun clearSelection() {
        lastSelectionCategory = null
        selection.update { setOf() }
    }

    fun toggleSelection(category: Category, anime: LibraryAnime) {
        selection.update { selection ->
            val newSelection = selection.mutate { set ->
                if (!set.remove(anime.id)) set.add(anime.id)
            }
            lastSelectionCategory = category.id.takeIf { newSelection.isNotEmpty() }
            newSelection
        }
    }

    /**
     * Selects all animes between and including the given anime and the last pressed anime from the
     * same category as the given anime
     */
    fun toggleRangeSelection(category: Category, anime: LibraryAnime) {
        val state = state.value
        selection.update { selection ->
            val newSelection = selection.mutate { list ->
                val lastSelected = list.lastOrNull()
                if (lastSelectionCategory != category.id) {
                    list.add(anime.id)
                    return@mutate
                }

                val items = state.getItemsForCategoryId(category.id).fastMap { it.id }
                val lastAnimeIndex = items.indexOf(lastSelected)
                val curAnimeIndex = items.indexOf(anime.id)

                val selectionRange = when {
                    lastAnimeIndex < curAnimeIndex -> lastAnimeIndex..curAnimeIndex
                    curAnimeIndex < lastAnimeIndex -> curAnimeIndex..lastAnimeIndex
                    // We shouldn't reach this point
                    else -> return@mutate
                }
                selectionRange.mapNotNull { items[it] }.let(list::addAll)
            }
            lastSelectionCategory = category.id
            newSelection
        }
    }

    fun selectAll() {
        lastSelectionCategory = null
        val state = state.value
        selection.update { selection ->
            selection.mutate { list ->
                state.getItemsForCategoryId(state.activeCategory?.id).map { it.id }.let(list::addAll)
            }
        }
    }

    fun invertSelection() {
        lastSelectionCategory = null
        val state = state.value
        selection.update { selection ->
            selection.mutate { list ->
                val itemIds = state.getItemsForCategoryId(state.activeCategory?.id).fastMap { it.id }
                val (toRemove, toAdd) = itemIds.partition { it in list }
                list.removeAll(toRemove)
                list.addAll(toAdd)
            }
        }
    }

    fun search(query: String?) {
        searchQuery.update { query }
    }

    fun updateActiveCategoryIndex(index: Int) {
        activeCategoryIndex.update { index }
        // Coerce here rather than reading it back off [state], which is derived asynchronously
        // and would still hold the previous index at this point.
        val lastIndex = state.value.displayedCategories.lastIndex.coerceAtLeast(0)
        libraryPreferences.lastUsedCategory.set(index.coerceIn(0, lastIndex))
    }

    fun openChangeCategoryDialog() {
        viewModelScope.launchIO {
            // Create a copy of selected anime
            val animeList = selectedAnime

            // Hide the default category because it has a different behavior than the ones from db.
            val categories = state.value.displayedCategories.filter { it.id != 0L }

            // Get indexes of the common categories to preselect.
            val common = getCommonCategories(animeList)
            // Get indexes of the mix categories to preselect.
            val mix = getMixCategories(animeList)
            val preselected = categories
                .map {
                    when (it) {
                        in common -> CheckboxState.State.Checked(it)
                        in mix -> CheckboxState.TriState.Exclude(it)
                        else -> CheckboxState.State.None(it)
                    }
                }

            dialog.update { Dialog.ChangeCategory(animeList, preselected) }
        }
    }

    fun openDeleteAnimeDialog() {
        dialog.update { Dialog.DeleteAnime(state.value.selectedAnime) }
    }

    fun closeDialog() {
        dialog.update { null }
    }

    // AM (GROUPING) -->
    private suspend fun getGroupedItems(
        groupType: Int,
        libraryAnime: List<LibraryItem>,
    ): Map<Category, List</* LibraryItem */ Long>> {
        val context = preferences.context
        return when (groupType) {
            LibraryGroup.BY_TRACK_STATUS -> {
                val tracks = runBlocking { getTracks.await() }.groupBy { it.animeId }
                val groupCache = mutableMapOf</* Track.status */ Int, MutableList</* LibraryItem */ Long>>()
                libraryAnime.forEach { item ->
                    val statuses = tracks[item.id]?.mapNotNull { track ->
                        TrackStatus.parseTrackerStatus(trackerManager, track.trackerId, track.status)
                    }
                        ?.takeIf { it.isNotEmpty() }
                        ?: listOf(TrackStatus.OTHER)
                    statuses.forEach { status ->
                        groupCache.getOrPut(status.int) { mutableListOf() }.add(item.id)
                    }
                }

                groupCache.mapKeys { (trackStatus) ->
                    Category(
                        id = trackStatus.toLong(),
                        name = TrackStatus.entries
                            .find { it.int == trackStatus }
                            .let { it ?: TrackStatus.OTHER }
                            .let { context.stringResource(it.res) },
                        order = TrackStatus.entries.toTypedArray().indexOfFirst {
                            it.int == trackStatus
                        }.takeUnless { it == -1 }?.toLong() ?: TrackStatus.OTHER.ordinal.toLong(),
                        flags = 0,
                        hidden = false,
                    )
                }
            }
            LibraryGroup.BY_SOURCE -> {
                val groupCache = mutableMapOf</* Source.id */ Long, MutableList</* LibraryItem */ Long>>()
                libraryAnime.forEach { item ->
                    groupCache.getOrPut(item.libraryAnime.anime.source) { mutableListOf() }.add(item.id)
                }
                val sources = groupCache.keys
                    .map { sourceManager.getOrStub(it) }
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name.ifBlank { it.id.toString() } })
                    .associateBy { it.id }
                val sourceIds = sources.map { it.key }

                groupCache.mapKeys { (sourceId) ->
                    Category(
                        id = sourceId.takeIf { it != LocalSource.ID } ?: -1L,
                        name = if (sourceId == LocalSource.ID) {
                            context.stringResource(MR.strings.local_source)
                        } else {
                            sourceManager.getOrStub(sourceId).name
                        },
                        order = sourceIds.indexOf(sourceId).takeUnless { it == -1 }?.toLong() ?: Long.MAX_VALUE,
                        flags = 0,
                        hidden = false,
                    )
                }
            }
            LibraryGroup.BY_STATUS -> {
                val groupCache = mutableMapOf</* Anime.status */ Long, MutableList</* LibraryItem */ Long>>()
                libraryAnime.forEach { item ->
                    groupCache.getOrPut(item.libraryAnime.anime.status) { mutableListOf() }.add(item.id)
                }

                groupCache.mapKeys { (status) ->
                    Category(
                        id = status + 1,
                        name = when (status) {
                            SAnime.ONGOING.toLong() -> context.stringResource(MR.strings.ongoing)
                            SAnime.LICENSED.toLong() -> context.stringResource(MR.strings.licensed)
                            SAnime.CANCELLED.toLong() -> context.stringResource(MR.strings.cancelled)
                            SAnime.ON_HIATUS.toLong() -> context.stringResource(MR.strings.on_hiatus)
                            SAnime.PUBLISHING_FINISHED.toLong() -> context.stringResource(
                                MR.strings.publishing_finished,
                            )
                            SAnime.COMPLETED.toLong() -> context.stringResource(MR.strings.completed)
                            else -> context.stringResource(MR.strings.unknown)
                        },
                        order = when (status) {
                            SAnime.ONGOING.toLong() -> 1
                            SAnime.LICENSED.toLong() -> 2
                            SAnime.CANCELLED.toLong() -> 3
                            SAnime.ON_HIATUS.toLong() -> 4
                            SAnime.PUBLISHING_FINISHED.toLong() -> 5
                            SAnime.COMPLETED.toLong() -> 6
                            else -> 7
                        },
                        flags = 0,
                        hidden = false,
                    )
                }
            }
            else -> emptyMap()
        }.toSortedMap(compareBy { it.order })
    }
    // <-- AM (GROUPING)

    sealed interface Dialog {
        data object SettingsSheet : Dialog
        data class ChangeCategory(
            val anime: List<Anime>,
            val initialSelection: List<CheckboxState<Category>>,
        ) : Dialog
        data class DeleteAnime(val anime: List<Anime>) : Dialog
    }

    @Immutable
    private data class ItemPreferences(
        val downloadBadge: Boolean,
        val unseenBadge: Boolean,
        val localBadge: Boolean,
        val languageBadge: Boolean,
        val skipOutsideReleasePeriod: Boolean,

        val globalFilterDownloaded: Boolean,
        val filterDownloaded: TriState,
        val filterUnseen: TriState,
        val filterStarted: TriState,
        val filterBookmarked: TriState,
        val filterCompleted: TriState,
        val filterIntervalCustom: TriState,
    )

    @Immutable
    data class LibraryData(
        val isInitialized: Boolean = false,
        val showSystemCategory: Boolean = false,
        val categories: List<Category> = emptyList(),
        val favorites: List<LibraryItem> = emptyList(),
        val tracksMap: Map</* Anime */ Long, List<Track>> = emptyMap(),
        val loggedInTrackerIds: Set<Long> = emptySet(),
    ) {
        val favoritesById by lazy { favorites.associateBy { it.id } }
    }

    @Immutable
    data class State(
        val isInitialized: Boolean = false,
        val isLoading: Boolean = true,
        val searchQuery: String? = null,
        val selection: Set</* Anime */ Long> = setOf(),
        val hasActiveFilters: Boolean = false,
        val showCategoryTabs: Boolean = false,
        val showAnimeCount: Boolean = false,
        val showAnimeContinueButton: Boolean = false,
        val dialog: Dialog? = null,
        // AM (GROUPING) -->
        val groupType: Int = LibraryGroup.BY_DEFAULT,
        val hasCategories: Boolean = false,
        // <-- AM (GROUPING)
        val libraryData: LibraryData = LibraryData(),
        private val activeCategoryIndex: Int = 0,
        private val groupedFavorites: Map<Category, List</* LibraryItem */ Long>> = emptyMap(),
    ) {
        val displayedCategories: List<Category> = groupedFavorites.keys.toList()

        val coercedActiveCategoryIndex = activeCategoryIndex.coerceIn(
            minimumValue = 0,
            maximumValue = displayedCategories.lastIndex.coerceAtLeast(0),
        )

        val activeCategory: Category? = displayedCategories.getOrNull(coercedActiveCategoryIndex)

        val isLibraryEmpty = libraryData.favorites.isEmpty()

        val selectionMode = selection.isNotEmpty()

        val selectedAnime by lazy { selection.mapNotNull { libraryData.favoritesById[it]?.libraryAnime?.anime } }

        fun getItemsForCategoryId(categoryId: Long?): List<LibraryItem> {
            if (categoryId == null) return emptyList()
            val category = displayedCategories.find { it.id == categoryId } ?: return emptyList()
            return getItemsForCategory(category)
        }

        fun getItemsForCategory(category: Category): List<LibraryItem> {
            return groupedFavorites[category].orEmpty().mapNotNull { libraryData.favoritesById[it] }
        }

        fun getItemCountForCategory(category: Category): Int? {
            return if (showAnimeCount || !searchQuery.isNullOrEmpty()) groupedFavorites[category]?.size else null
        }

        fun getToolbarTitle(
            defaultTitle: String,
            defaultCategoryTitle: String,
            page: Int,
        ): LibraryToolbarTitle {
            val category = displayedCategories.getOrNull(page) ?: return LibraryToolbarTitle(defaultTitle)
            val categoryName = category.let {
                if (it.isSystemCategory) defaultCategoryTitle else it.name
            }
            val title = if (showCategoryTabs) defaultTitle else categoryName
            val count = when {
                !showAnimeCount -> null
                !showCategoryTabs -> getItemCountForCategory(category)
                // Whole library count
                else -> libraryData.favorites.size
            }
            return LibraryToolbarTitle(title, count)
        }
    }
}
