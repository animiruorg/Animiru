package tachiyomi.data.source

import androidx.paging.PagingState
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import kotlinx.coroutines.CancellationException
import mihon.domain.anime.model.toDomainAnime
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.source.repository.SourcePagingSource

class SourceSearchPagingSource(
    source: suspend () -> AnimeSource,
    private val query: String,
    private val filters: AnimeFilterList,
    networkToLocalAnime: NetworkToLocalAnime,
) : BaseSourcePagingSource(source, networkToLocalAnime) {
    override suspend fun requestNextPage(source: AnimeSource, currentPage: Int): AnimesPage {
        return source.getSearchAnime(currentPage, query, filters)
    }
}

class SourcePopularPagingSource(
    source: suspend () -> AnimeSource,
    networkToLocalAnime: NetworkToLocalAnime,
) : BaseSourcePagingSource(source, networkToLocalAnime) {
    override suspend fun requestNextPage(source: AnimeSource, currentPage: Int): AnimesPage {
        return source.getPopularAnime(currentPage)
    }
}

class SourceLatestPagingSource(
    source: suspend () -> AnimeSource,
    networkToLocalAnime: NetworkToLocalAnime,
) : BaseSourcePagingSource(source, networkToLocalAnime) {
    override suspend fun requestNextPage(source: AnimeSource, currentPage: Int): AnimesPage {
        return source.getLatestUpdates(currentPage)
    }
}

abstract class BaseSourcePagingSource(
    private val source: suspend () -> AnimeSource,
    private val networkToLocalAnime: NetworkToLocalAnime,
) : SourcePagingSource() {

    private val seenAnime = hashSetOf<String>()

    abstract suspend fun requestNextPage(source: AnimeSource, currentPage: Int): AnimesPage

    override suspend fun load(params: LoadParams<Long>): LoadResult<Long, Anime> {
        val page = params.key ?: 1

        return try {
            val source = source()
            val animesPage = withIOContext {
                requestNextPage(source, page.toInt())
                    .takeIf { it.animes.isNotEmpty() }
                    ?: throw NoResultsException()
            }

            val anime = animesPage.animes
                .map { it.toDomainAnime(source.id) }
                .filter { seenAnime.add(it.url) }
                .let { networkToLocalAnime(it) }

            LoadResult.Page(
                data = anime,
                prevKey = null,
                nextKey = if (animesPage.hasNextPage) page + 1 else null,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Long, Anime>): Long? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey ?: anchorPage?.nextKey
        }
    }
}

class NoResultsException : Exception()
