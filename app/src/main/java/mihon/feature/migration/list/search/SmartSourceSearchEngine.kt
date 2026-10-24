package mihon.feature.migration.list.search

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.SAnime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.domain.anime.model.toDomainAnime
import tachiyomi.domain.anime.model.Anime

class SmartSourceSearchEngine(extraSearchParams: String?) : BaseSmartSearchEngine<SAnime>(extraSearchParams) {

    override fun getTitle(result: SAnime) = result.title

    suspend fun regularSearch(source: AnimeSource, title: String): Anime? {
        return regularSearch(makeSearchAction(source), title).let {
            it?.toDomainAnime(source.id)
        }
    }

    suspend fun deepSearch(source: AnimeSource, title: String): Anime? {
        return deepSearch(makeSearchAction(source), title).let {
            it?.toDomainAnime(source.id)
        }
    }

    // Sources aren't main-safe: their default implementations block on the network
    private fun makeSearchAction(source: AnimeSource): SearchAction<SAnime> = { query ->
        withContext(Dispatchers.IO) { source.getSearchAnime(1, query, source.getFilterList()).animes }
    }
}
