package eu.kanade.tachiyomi.ui.deeplink

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.online.ResolvableAnimeSource
import eu.kanade.tachiyomi.animesource.online.UriType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import mihon.domain.anime.model.toDomainAnime
import mihon.domain.source.interactor.UpdateAnimeFromRemote
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.episode.interactor.GetEpisodeByUrlAndAnimeId
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.source.service.SourceManager

@AssistedInject
class DeepLinkViewModel(
    @Assisted query: String,
    private val sourceManager: SourceManager,
    private val networkToLocalAnime: NetworkToLocalAnime,
    private val getEpisodeByUrlAndAnimeId: GetEpisodeByUrlAndAnimeId,
    private val updateAnimeFromRemote: UpdateAnimeFromRemote,
) : ViewModel() {

    val state: StateFlow<DeepLinkViewModel.State>
        field = MutableStateFlow<DeepLinkViewModel.State>(State.Loading)

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(query: String): DeepLinkViewModel
    }

    init {
        viewModelScope.launchIO {
            val source = sourceManager.getAll()
                .filterIsInstance<ResolvableAnimeSource>()
                .firstOrNull { it.getUriType(query) != UriType.Unknown }

            val anime = source?.getAnime(query)?.let {
                networkToLocalAnime(it.toDomainAnime(source.id))
            }

            val episode = if (source?.getUriType(query) == UriType.Episode && anime != null) {
                source.getEpisode(query)?.let { getEpisodeFromSEpisode(it, anime, source) }
            } else {
                null
            }

            state.update {
                if (anime == null) {
                    State.NoResults
                } else {
                    if (episode == null) {
                        State.Result(anime)
                    } else {
                        State.Result(anime, episode.id)
                    }
                }
            }
        }
    }

    private suspend fun getEpisodeFromSEpisode(sEpisode: SEpisode, anime: Anime, source: AnimeSource): Episode? {
        val localEpisode = getEpisodeByUrlAndAnimeId.await(sEpisode.url, anime.id)

        if (localEpisode != null) return localEpisode
        updateAnimeFromRemote.awaitEpisodesUpdate(anime, fetchEpisodes = true).getOrElse { null }
        return getEpisodeByUrlAndAnimeId.await(sEpisode.url, anime.id)
    }

    sealed interface State {
        @Immutable
        data object Loading : State

        @Immutable
        data object NoResults : State

        @Immutable
        data class Result(val anime: Anime, val episodeId: Long? = null) : State
    }
}
