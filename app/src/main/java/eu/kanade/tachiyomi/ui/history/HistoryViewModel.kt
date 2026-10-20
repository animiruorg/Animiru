package eu.kanade.tachiyomi.ui.history

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.core.util.insertSeparators
import eu.kanade.domain.anime.interactor.UpdateAnime
import eu.kanade.domain.track.interactor.AddTracks
import eu.kanade.presentation.history.HistoryUiModel
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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.preference.mapAsCheckboxState
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.anime.interactor.GetAnime
import tachiyomi.domain.anime.interactor.GetDuplicateLibraryAnime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.model.AnimeWithEpisodeCount
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetAnimeCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.history.interactor.GetNextEpisodes
import tachiyomi.domain.history.interactor.RemoveHistory
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.service.SourceManager
import kotlin.time.Duration.Companion.seconds

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class HistoryViewModel(
    private val addTracks: AddTracks,
    private val getCategories: GetCategories,
    private val getDuplicateLibraryAnime: GetDuplicateLibraryAnime,
    private val getHistory: GetHistory,
    private val getAnime: GetAnime,
    private val getNextEpisodes: GetNextEpisodes,
    private val libraryPreferences: LibraryPreferences,
    private val removeHistory: RemoveHistory,
    private val setAnimeCategories: SetAnimeCategories,
    private val updateAnime: UpdateAnime,
    private val sourceManager: SourceManager,
) : ViewModel() {

    val snackbarHostState: SnackbarHostState = SnackbarHostState()

    private val _events: Channel<Event> = Channel(Channel.UNLIMITED)
    val events: Flow<Event> = _events.receiveAsFlow()

    private val searchQuery = MutableStateFlow<String?>(null)

    private val dialog = MutableStateFlow<Dialog?>(null)

    private val history = searchQuery
        .flatMapLatest { query ->
            getHistory.subscribe(query ?: "")
                .distinctUntilChanged()
                .catch { error ->
                    logcat(LogPriority.ERROR, error)
                    _events.send(Event.InternalError)
                }
                .map { it.toHistoryUiModels() }
                .flowOn(Dispatchers.IO)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), emptyList())

    val state: StateFlow<State> = combine(
        searchQuery,
        history,
        dialog,
    ) { searchQuery, history, dialog ->
        State(searchQuery = searchQuery, list = history, dialog = dialog)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), State())

    private fun List<HistoryWithRelations>.toHistoryUiModels(): List<HistoryUiModel> {
        return map { HistoryUiModel.Item(it) }
            .insertSeparators { before, after ->
                val beforeDate = before?.item?.seenAt?.toLocalDate()
                val afterDate = after?.item?.seenAt?.toLocalDate()
                when {
                    beforeDate != afterDate && afterDate != null -> HistoryUiModel.Header(afterDate)
                    // Return null to avoid adding a separator between two items.
                    else -> null
                }
            }
    }

    suspend fun getNextEpisode(): Episode? {
        return withIOContext { getNextEpisodes.await(onlyUnseen = false).firstOrNull() }
    }

    fun getNextEpisodeForAnime(animeId: Long, episodeId: Long) {
        viewModelScope.launchIO {
            sendNextEpisodeEvent(getNextEpisodes.await(animeId, episodeId, onlyUnseen = false))
        }
    }

    private suspend fun sendNextEpisodeEvent(episodes: List<Episode>) {
        val episode = episodes.firstOrNull()
        _events.send(Event.OpenEpisode(episode))
    }

    fun removeFromHistory(history: HistoryWithRelations) {
        viewModelScope.launchIO {
            removeHistory.await(history)
        }
    }

    fun removeAllFromHistory(animeId: Long) {
        viewModelScope.launchIO {
            removeHistory.await(animeId)
        }
    }

    fun removeAllHistory() {
        viewModelScope.launchIO {
            val result = removeHistory.awaitAll()
            if (!result) return@launchIO
            _events.send(Event.HistoryCleared)
        }
    }

    fun updateSearchQuery(query: String?) {
        searchQuery.update { query }
    }

    fun setDialog(dialog: Dialog?) {
        this.dialog.update { dialog }
    }

    /**
     * Get user categories.
     *
     * @return List of categories, not including the default category
     */
    suspend fun getCategories(): List<Category> {
        return getCategories.await().filterNot { it.isSystemCategory }
    }

    private fun moveAnimeToCategory(animeId: Long, categories: Category?) {
        val categoryIds = listOfNotNull(categories).map { it.id }
        moveAnimeToCategory(animeId, categoryIds)
    }

    private fun moveAnimeToCategory(animeId: Long, categoryIds: List<Long>) {
        viewModelScope.launchIO {
            setAnimeCategories.await(animeId, categoryIds)
        }
    }

    fun moveAnimeToCategoriesAndAddToLibrary(anime: Anime, categories: List<Long>) {
        moveAnimeToCategory(anime.id, categories)
        if (anime.favorite) return

        viewModelScope.launchIO {
            updateAnime.awaitUpdateFavorite(anime.id, true)
        }
    }

    private suspend fun getAnimeCategoryIds(anime: Anime): List<Long> {
        return getCategories.await(anime.id)
            .map { it.id }
    }

    fun addFavorite(animeId: Long) {
        viewModelScope.launchIO {
            val anime = getAnime.await(animeId) ?: return@launchIO

            val duplicates = getDuplicateLibraryAnime(anime)
            if (duplicates.isNotEmpty()) {
                dialog.update { Dialog.DuplicateAnime(anime, duplicates) }
                return@launchIO
            }

            addFavorite(anime)
        }
    }

    fun addFavorite(anime: Anime) {
        viewModelScope.launchIO {
            // Move to default category if applicable
            val categories = getCategories()
            val defaultCategoryId = libraryPreferences.defaultCategory.get().toLong()
            val defaultCategory = categories.find { it.id == defaultCategoryId }

            when {
                // Default category set
                defaultCategory != null -> {
                    val result = updateAnime.awaitUpdateFavorite(anime.id, true)
                    if (!result) return@launchIO
                    moveAnimeToCategory(anime.id, defaultCategory)
                }

                // Automatic 'Default' or no categories
                defaultCategoryId == 0L || categories.isEmpty() -> {
                    val result = updateAnime.awaitUpdateFavorite(anime.id, true)
                    if (!result) return@launchIO
                    moveAnimeToCategory(anime.id, null)
                }

                // Choose a category
                else -> showChangeCategoryDialog(anime)
            }

            // Sync with tracking services if applicable
            addTracks.bindEnhancedTrackers(anime, sourceManager.getOrStub(anime.source))
        }
    }

    fun showMigrateDialog(target: Anime, current: Anime) {
        dialog.update { Dialog.Migrate(target = target, current = current) }
    }

    fun showChangeCategoryDialog(anime: Anime) {
        viewModelScope.launch {
            val categories = getCategories()
            val selection = getAnimeCategoryIds(anime)
            dialog.update {
                Dialog.ChangeCategory(
                    anime = anime,
                    initialSelection = categories.mapAsCheckboxState { it.id in selection },
                )
            }
        }
    }

    @Immutable
    data class State(
        val searchQuery: String? = null,
        val list: List<HistoryUiModel>? = null,
        val dialog: Dialog? = null,
    )

    sealed interface Dialog {
        data object DeleteAll : Dialog
        data class Delete(val history: HistoryWithRelations) : Dialog
        data class DuplicateAnime(val anime: Anime, val duplicates: List<AnimeWithEpisodeCount>) : Dialog
        data class ChangeCategory(
            val anime: Anime,
            val initialSelection: List<CheckboxState<Category>>,
        ) : Dialog
        data class Migrate(val target: Anime, val current: Anime) : Dialog
    }

    sealed interface Event {
        data class OpenEpisode(val episode: Episode?) : Event
        data object InternalError : Event
        data object HistoryCleared : Event
    }
}
