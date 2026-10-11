package eu.kanade.tachiyomi.ui.browse.migration.search

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
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SearchItemResult
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SearchViewModel
import kotlinx.coroutines.launch
import tachiyomi.domain.anime.interactor.GetAnime
import tachiyomi.domain.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.source.service.SourceManager

@AssistedInject
class MigrateSearchViewModel(
    @Assisted val animeId: Long,
    sourcePreferences: SourcePreferences,
    extensionManager: ExtensionManager,
    networkToLocalAnime: NetworkToLocalAnime,
    getAnime: GetAnime,
    preferences: SourcePreferences,
    private val sourceManager: SourceManager,
) : SearchViewModel(
    sourcePreferences = sourcePreferences,
    sourceManager = sourceManager,
    extensionManager = extensionManager,
    networkToLocalAnime = networkToLocalAnime,
    getAnime = getAnime,
    preferences = preferences,
) {
    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(animeId: Long): MigrateSearchViewModel
    }

    private val migrationSources by lazy { sourcePreferences.migrationSources.get() }

    override val sortComparator = { map: Map<AnimeSource, SearchItemResult> ->
        compareBy<AnimeSource>(
            { (map[it] as? SearchItemResult.Success)?.isEmpty ?: true },
            { migrationSources.indexOf(it.id) },
        )
    }

    init {
        viewModelScope.launch {
            val anime = getAnime.await(animeId)!!
            updateState {
                it.copy(
                    from = anime,
                    searchQuery = anime.title,
                )
            }
            search()
        }
    }

    override suspend fun getEnabledSources(): List<AnimeSource> {
        return migrationSources.mapNotNull { sourceManager.get(it) }
    }
}
