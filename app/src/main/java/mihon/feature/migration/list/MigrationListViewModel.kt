package mihon.feature.migration.list

import androidx.annotation.FloatRange
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.source.getNameForAnimeInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import logcat.LogPriority
import mihon.domain.migration.usecases.MigrateAnimeUseCase
import mihon.domain.source.interactor.UpdateAnimeFromRemote
import mihon.feature.migration.list.models.MigratingAnime
import mihon.feature.migration.list.models.MigratingAnime.SearchResult
import mihon.feature.migration.list.search.SmartSourceSearchEngine
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.anime.interactor.GetAnime
import tachiyomi.domain.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.episode.interactor.GetEpisodeSummaryByAnimeId
import tachiyomi.domain.source.service.SourceManager

@AssistedInject
class MigrationListViewModel(
    @Assisted animeIds: Collection<Long>,
    @Assisted extraSearchQuery: String?,
    private val preferences: SourcePreferences,
    private val sourceManager: SourceManager,
    private val getAnime: GetAnime,
    private val networkToLocalAnime: NetworkToLocalAnime,
    private val getEpisodeSummaryByAnimeId: GetEpisodeSummaryByAnimeId,
    private val migrateAnime: MigrateAnimeUseCase,
    private val updateAnimeFromRemote: UpdateAnimeFromRemote,
) : ViewModel() {

    val state: StateFlow<MigrationListViewModel.State>
        field = MutableStateFlow<MigrationListViewModel.State>(State())

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(animeIds: Collection<Long>, extraSearchQuery: String?): MigrationListViewModel
    }

    private val smartSearchEngine = SmartSourceSearchEngine(extraSearchQuery)

    val items
        inline get() = state.value.items

    private val hideUnmatched = preferences.migrationHideUnmatched.get()
    private val hideWithoutUpdates = preferences.migrationHideWithoutUpdates.get()

    private val navigateBackChannel = Channel<Unit>()
    val navigateBackEvent = navigateBackChannel.receiveAsFlow()

    private var migrateJob: Job? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val anime = animeIds
                .map {
                    async {
                        val anime = getAnime.await(it) ?: return@async null
                        val episodeInfo = getEpisodeInfo(it)
                        MigratingAnime(
                            anime = anime,
                            episodeCount = episodeInfo.episodeCount,
                            latestEpisode = episodeInfo.latestEpisode,
                            source = sourceManager.getOrStub(anime.source).getNameForAnimeInfo(),
                            parentContext = viewModelScope.coroutineContext,
                        )
                    }
                }
                .awaitAll()
                .filterNotNull()
            state.update { it.copy(items = anime) }
            runMigrations(anime)
        }
    }

    private suspend fun getEpisodeInfo(id: Long) = getEpisodeSummaryByAnimeId.await(id).let { summary ->
        EpisodeInfo(
            latestEpisode = summary.latestEpisodeNumber,
            episodeCount = summary.episodeCount.toInt(),
        )
    }

    // AY -->
    private suspend fun Anime.toMismatchSearchResult(): SearchResult.MismatchedFetchType {
        val episodeInfo = getEpisodeInfo(id)
        val source = sourceManager.getOrStub(source).getNameForAnimeInfo()
        return SearchResult.MismatchedFetchType(
            anime = this,
            episodeCount = episodeInfo.episodeCount,
            source = source,
        )
    }
    // <-- AY

    private suspend fun Anime.toSuccessSearchResult(): SearchResult.Success {
        val episodeInfo = getEpisodeInfo(id)
        val source = sourceManager.getOrStub(source).getNameForAnimeInfo()
        return SearchResult.Success(
            anime = this,
            episodeCount = episodeInfo.episodeCount,
            latestEpisode = episodeInfo.latestEpisode,
            source = source,
        )
    }

    private suspend fun runMigrations(animes: List<MigratingAnime>) {
        val prioritizeByEpisodes = preferences.migrationPrioritizeByEpisodes.get()
        val deepSearchMode = preferences.migrationDeepSearchMode.get()

        val sources = preferences.migrationSources.get()
            .mapNotNull { sourceManager.get(it) }

        for (anime in animes) {
            if (!currentCoroutineContext().isActive) break
            if (anime.anime.id !in state.value.animeIds) continue
            if (anime.searchResult.value != SearchResult.Searching) continue
            if (!anime.migrationScope.isActive) continue

            val result = try {
                anime.migrationScope.async {
                    if (prioritizeByEpisodes) {
                        val sourceSemaphore = Semaphore(5)
                        sources.map { source ->
                            async innerAsync@{
                                sourceSemaphore.withPermit {
                                    val result = searchSource(anime.anime, source, deepSearchMode)
                                    if (result == null || result.second.episodeCount == 0) return@innerAsync null
                                    result
                                }
                            }
                        }
                            .mapNotNull { it.await() }
                            .maxByOrNull { it.second.latestEpisode ?: 0.0 }
                    } else {
                        sources.forEach { source ->
                            val result = searchSource(anime.anime, source, deepSearchMode)
                            if (result != null) return@async result
                        }
                        null
                    }
                }
                    .await()
            } catch (_: CancellationException) {
                continue
            }

            if (result != null && result.first.thumbnailUrl == null) {
                try {
                    updateAnimeFromRemote.awaitEpisodesUpdate(result.first, fetchDetails = true, manualFetch = true)
                        .getOrThrow()
                        .anime
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
            }

            anime.searchResult.value = result?.first?.let {
                // AY -->
                if (anime.anime.fetchType == it.fetchType) {
                    it.toSuccessSearchResult()
                } else {
                    it.toMismatchSearchResult()
                }
                // <-- AY
            } ?: SearchResult.NotFound

            if (result == null && hideUnmatched) {
                removeAnime(anime)
            }
            if (result != null &&
                hideWithoutUpdates &&
                (result.second.latestEpisode ?: 0.0) <= (anime.latestEpisode ?: 0.0)
            ) {
                removeAnime(anime)
            }

            updateMigrationProgress()
        }
    }

    private suspend fun searchSource(
        anime: Anime,
        source: AnimeSource,
        deepSearchMode: Boolean,
    ): Pair<Anime, EpisodeInfo>? {
        return try {
            val searchResult = if (deepSearchMode) {
                smartSearchEngine.deepSearch(source, anime.title)
            } else {
                smartSearchEngine.regularSearch(source, anime.title)
            }

            if (searchResult == null || (searchResult.url == anime.url && source.id == anime.source)) return null

            val localAnime = networkToLocalAnime(searchResult)
            try {
                updateAnimeFromRemote.awaitEpisodesUpdate(localAnime, fetchEpisodes = true).getOrThrow()
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
            }
            localAnime to getEpisodeInfo(localAnime.id)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun updateMigrationProgress() {
        state.update { state ->
            state.copy(
                finishedCount = items.count { it.searchResult.value != SearchResult.Searching },
                migrationComplete = migrationComplete(),
            )
        }
        if (items.isEmpty()) {
            navigateBack()
        }
    }

    private fun migrationComplete() = items.all {
        it.searchResult.value != SearchResult.Searching &&
            // AY -->
            it.searchResult.value !is SearchResult.MismatchedFetchType
        // <-- AY
    } &&
        items.any { it.searchResult.value is SearchResult.Success }

    // AY -->
    sealed interface MigrateSearchResult {
        data class Success(val anime: Anime) : MigrateSearchResult
        data class Failure(val fetchType: FetchType) : MigrateSearchResult
    }
    // <-- AY

    fun useAnimeForMigration(current: Long, target: Long, onMissingEntries: (FetchType) -> Unit) {
        val migratingAnime = items.find { it.anime.id == current } ?: return
        migratingAnime.searchResult.value = SearchResult.Searching
        viewModelScope.launch(Dispatchers.IO) {
            val result = migratingAnime.migrationScope.async {
                val anime = getAnime.await(target) ?: return@async null
                try {
                    val source = sourceManager.get(anime.source)!!
                    when (anime.fetchType) {
                        // AY -->
                        FetchType.Seasons -> {
                            updateAnimeFromRemote.awaitSeasonsUpdate(
                                source = source,
                                anime = anime,
                                fetchSeasons = true,
                            )
                                .getOrThrow()
                                .anime
                        }
                        // <-- AY
                        FetchType.Episodes -> {
                            updateAnimeFromRemote.awaitEpisodesUpdate(
                                source = source,
                                anime = anime,
                                fetchEpisodes = true,
                            )
                                .getOrThrow()
                                .anime
                        }
                    }
                        .let { MigrateSearchResult.Success(it) }
                } catch (_: Exception) {
                    MigrateSearchResult.Failure(anime.fetchType)
                }
            }
                .await()

            if (result is MigrateSearchResult.Failure) {
                migratingAnime.searchResult.value = SearchResult.NotFound
                withContext(Dispatchers.Main) { onMissingEntries(result.fetchType) }
                return@launch
            }

            // AY -->
            val resultAnime = (result as MigrateSearchResult.Success).anime
            if (migratingAnime.anime.fetchType != resultAnime.fetchType) {
                migratingAnime.searchResult.value = resultAnime.toMismatchSearchResult()
                return@launch
            }
            // <-- AY

            migratingAnime.searchResult.value = resultAnime.toSuccessSearchResult()
            updateMigrationProgress()
        }
    }

    fun migrateAnimes() {
        migrateAnimes(replace = true)
    }

    fun copyAnimes() {
        migrateAnimes(replace = false)
    }

    private fun migrateAnimes(replace: Boolean) {
        migrateJob = viewModelScope.launch(Dispatchers.IO) {
            state.update { it.copy(dialog = Dialog.Progress(0f)) }
            val items = items
            try {
                items.forEachIndexed { index, anime ->
                    try {
                        ensureActive()
                        val target = anime.searchResult.value.let {
                            if (it is SearchResult.Success) {
                                it.anime
                            } else {
                                null
                            }
                        }
                        if (target != null) {
                            migrateAnime(current = anime.anime, target = target, replace = replace)
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        logcat(LogPriority.WARN, throwable = e)
                    }
                    state.update {
                        it.copy(dialog = Dialog.Progress((index.toFloat() / items.size).coerceAtMost(1f)))
                    }
                }

                navigateBack()
            } finally {
                state.update { it.copy(dialog = null) }
                migrateJob = null
            }
        }
    }

    fun cancelMigrate() {
        migrateJob?.cancel()
        migrateJob = null
    }

    private suspend fun navigateBack() {
        navigateBackChannel.send(Unit)
    }

    fun migrateNow(animeId: Long, replace: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val anime = items.find { it.anime.id == animeId } ?: return@launch
            val target = (anime.searchResult.value as? SearchResult.Success)?.anime ?: return@launch
            migrateAnime(current = anime.anime, target = target, replace = replace)

            removeAnime(animeId)
        }
    }

    fun removeAnime(animeId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val item = items.find { it.anime.id == animeId } ?: return@launch
            removeAnime(item)
            item.migrationScope.cancel()
            updateMigrationProgress()
        }
    }

    private fun removeAnime(item: MigratingAnime) {
        state.update { it.copy(items = items.toMutableList().apply { remove(item) }) }
    }

    override fun onCleared() {
        items.forEach {
            it.migrationScope.cancel()
        }
    }

    fun showMigrateDialog(copy: Boolean) {
        state.update { state ->
            state.copy(
                dialog = Dialog.Migrate(
                    copy = copy,
                    totalCount = items.size,
                    skippedCount = items.count { it.searchResult.value == SearchResult.NotFound },
                ),
            )
        }
    }

    fun showExitDialog() {
        state.update {
            it.copy(dialog = Dialog.Exit)
        }
    }

    fun dismissDialog() {
        state.update { it.copy(dialog = null) }
    }

    data class EpisodeInfo(
        val latestEpisode: Double?,
        val episodeCount: Int,
    )

    sealed interface Dialog {
        data class Migrate(val copy: Boolean, val totalCount: Int, val skippedCount: Int) : Dialog
        data class Progress(@FloatRange(0.0, 1.0) val progress: Float) : Dialog
        data object Exit : Dialog
    }

    data class State(
        val items: List<MigratingAnime> = listOf(),
        val finishedCount: Int = 0,
        val migrationComplete: Boolean = false,
        val dialog: Dialog? = null,
    ) {
        val animeIds: List<Long> = items.map { it.anime.id }
    }
}
