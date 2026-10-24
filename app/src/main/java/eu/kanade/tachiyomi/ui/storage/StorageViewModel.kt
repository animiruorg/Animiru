// AM (STORAGE_SCREEN) -->
package eu.kanade.tachiyomi.ui.storage

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.util.fastDistinctBy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.domain.anime.interactor.UpdateAnime
import eu.kanade.presentation.more.storage.StorageScreenState
import eu.kanade.presentation.more.storage.data.StorageData
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.util.storage.size
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mihon.core.metro.AppCoroutineScope
import tachiyomi.domain.anime.interactor.GetLibraryAnime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.GetVisibleCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.source.local.io.Format
import tachiyomi.source.local.io.LocalSourceFileSystem
import tachiyomi.source.local.isLocal
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class StorageViewModel(
    @AppCoroutineScope private val appScope: CoroutineScope,
    private val downloadCache: DownloadCache,
    private val downloadManager: DownloadManager,
    private val getLibraryAnime: GetLibraryAnime,
    private val getCategories: GetCategories,
    private val getVisibleCategories: GetVisibleCategories,
    private val updateAnime: UpdateAnime,
    private val sourceManager: SourceManager,
    private val sourceFileSystem: LocalSourceFileSystem,
    private val libraryPreferences: LibraryPreferences,
) : ViewModel() {

    val state: StateFlow<StorageScreenState>
        field = MutableStateFlow<StorageScreenState>(StorageScreenState.Loading(0))

    private val _selectedCategory = MutableStateFlow(allCategory)
    val selectedCategory = _selectedCategory.asStateFlow()

    private val skipDownloadChangeFlow = MutableStateFlow(false)
    private val _downloadedItems = MutableStateFlow<Pair<List<StorageData>, List<Category>>>(
        emptyList<StorageData>() to emptyList(),
    )
    val downloadedItems = _downloadedItems.asStateFlow()

    private val entries = MutableStateFlow<List<Long>>(emptyList())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val hideHiddenCategories = libraryPreferences.hideHiddenCategoriesSettings.get()

            val downloadCacheFlow = downloadCache.changes
                .debounce(0.5.seconds)
                .transformLatest {
                    if (skipDownloadChangeFlow.value) {
                        skipDownloadChangeFlow.value = false
                        return@transformLatest
                    } else {
                        emit(Unit)
                    }
                }

            combine(
                downloadCacheFlow,
                downloadCache.isInitializing,
                getLibraryAnime.subscribe().distinctUntilChanged { old, new ->
                    old.map { Pair(it.id, it.categories) }.toSet() == new.map { Pair(it.id, it.categories) }.toSet()
                },
                if (hideHiddenCategories) getVisibleCategories.subscribe() else getCategories.subscribe(),
            ) { _, _, libraries, categories ->
                val distinctEntries = libraries.fastDistinctBy {
                    it.id
                }.filter { libraryAnime ->
                    when {
                        // if all is selected, we want to make sure to include all entries
                        // from only visible categories
                        selectedCategory.value.id == ALL_CATEGORY_ID -> categories.any {
                            it.id in libraryAnime.categories
                        }
                        // else include only entries from the selected category
                        else -> selectedCategory.value.id in libraryAnime.categories
                    }
                }

                // If an anime is removed from the list, we don't want to recompute the size for all entries,
                // just remove the entry from the list
                if (downloadedItems.value.first.isNotEmpty() && distinctEntries.size < entries.value.size) {
                    val (items, categories) = downloadedItems.value
                    val libraryIds = libraries.map { it.anime.id }
                    val newItems = items.filter { it.anime.id in libraryIds }

                    entries.value = distinctEntries.map { it.id }

                    return@combine newItems to categories
                }

                entries.value = distinctEntries.map { it.id }

                val items = mutableListOf<StorageData>()

                state.update {
                    StorageScreenState.Loading(0)
                }

                distinctEntries.forEachIndexed { index, libraryAnime ->
                    val anime = libraryAnime.anime
                    val random = Random(anime.id)

                    val size = getSize(anime)
                    val episodeCount = getCount(anime)
                    val categories = getAnimeCategoryIds(anime)

                    state.update {
                        StorageScreenState.Loading((((index + 1.0) / distinctEntries.size) * 100).toInt())
                    }

                    if (size > 0) {
                        items.add(
                            StorageData(
                                anime = anime,
                                categories = categories,
                                size = size,
                                episodeCount = episodeCount,
                                color = Color(
                                    random.nextInt(255),
                                    random.nextInt(255),
                                    random.nextInt(255),
                                ),
                            ),
                        )
                    }
                }
                items to listOf(allCategory) + categories
            }
                .collectLatest {
                    _downloadedItems.value = it
                }
        }

        combine(
            downloadedItems,
            selectedCategory,
        ) { (items, categories), selectedCategory ->
            val filteredItems = if (selectedCategory.id == allCategory.id) {
                items
            } else {
                items.filter { item ->
                    item.categories.contains(selectedCategory.id)
                }
            }
                .sortedByDescending { it.size }

            filteredItems to categories
        }
            .onEach { (items, categories) ->
                if (items.isEmpty() && categories.isEmpty()) return@onEach

                state.update {
                    StorageScreenState.Success(
                        items = items,
                        categories = categories,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private suspend fun getAnimeCategoryIds(anime: Anime): List<Long> {
        return getCategories.await(anime.id)
            .map { it.id }
    }

    private fun getSize(anime: Anime): Long {
        return if (anime.isLocal()) {
            sourceFileSystem
                .getAnimeDirectory(anime.url)
                ?.size()
                ?: 0L
        } else {
            downloadManager.getDownloadSize(anime)
        }
    }

    private fun getCount(anime: Anime): Int {
        return if (anime.isLocal()) {
            sourceFileSystem
                .getFilesInAnimeDirectory(anime.url)
                .count { Format.isSupported(it) }
        } else {
            downloadManager.getDownloadCount(anime)
        }
    }

    fun setSelectedCategory(category: Category) {
        _selectedCategory.update { category }
    }

    fun deleteAnime(storageData: StorageData, removeFromLibrary: Boolean) {
        val anime = storageData.anime

        appScope.launch {
            skipDownloadChangeFlow.value = true

            if (anime.isLocal()) {
                sourceFileSystem
                    .getAnimeDirectory(anime.url)
                    ?.delete()
            } else {
                val source = sourceManager.get(anime.source) ?: return@launch
                downloadManager.deleteAnime(anime, source)
            }

            if (removeFromLibrary) {
                updateAnime.awaitUpdateFavorite(storageData.anime.id, false)
            }
        }

        _downloadedItems.update { (items, categories) ->
            items.filterNot { it.anime.id == anime.id } to categories
        }
    }

    companion object {
        /**
         * A dummy category used to display all entries irrespective of the category.
         */
        const val ALL_CATEGORY_ID = -1L

        val allCategory = Category(
            id = ALL_CATEGORY_ID,
            name = "All",
            order = 0L,
            flags = 0L,
            hidden = false,
        )
    }
}
// <-- AM (STORAGE_SCREEN)
