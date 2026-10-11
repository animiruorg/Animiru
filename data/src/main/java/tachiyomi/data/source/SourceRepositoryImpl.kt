package tachiyomi.data.source

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import tachiyomi.data.Database
import tachiyomi.data.subscribeToList
import tachiyomi.domain.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.source.model.StubSource
import tachiyomi.domain.source.repository.SourcePagingSource
import tachiyomi.domain.source.repository.SourceRepository
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.source.model.Source as DomainSource

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class SourceRepositoryImpl(
    private val sourceManager: SourceManager,
    private val database: Database,
    private val networkToLocalAnime: NetworkToLocalAnime,
) : SourceRepository {

    override fun getSources(): Flow<List<DomainSource>> {
        return sourceManager.sources.map { sources ->
            sources.map {
                mapSourceToDomainSource(it).copy(
                    supportsLatest = it.supportsLatest,
                )
            }
        }
    }

    override fun getOnlineSources(): Flow<List<DomainSource>> {
        return sourceManager.sources.map { sources ->
            sources
                .filterIsInstance<AnimeHttpSource>()
                .map(::mapSourceToDomainSource)
        }
    }

    override fun getSourcesWithFavoriteCount(): Flow<List<Pair<DomainSource, Long>>> {
        val sourceIdWithFavoriteCountFlow = database.animesQueries
            .getSourceIdWithFavoriteCount()
            .subscribeToList()

        return combine(sourceIdWithFavoriteCountFlow, sourceManager.sources) { sourceIdWithFavoriteCount, _ ->
            sourceIdWithFavoriteCount
        }
            .map {
                it.map { (sourceId, count) ->
                    val source = sourceManager.getOrStub(sourceId)
                    val domainSource = mapSourceToDomainSource(source).copy(
                        isStub = source is StubSource,
                    )
                    domainSource to count
                }
            }
    }

    override fun search(
        sourceId: Long,
        query: String,
        filterList: AnimeFilterList,
    ): SourcePagingSource {
        return SourceSearchPagingSource(
            { sourceManager.getOrStub(sourceId) },
            query,
            filterList,
            networkToLocalAnime,
        )
    }

    override fun getPopular(sourceId: Long): SourcePagingSource {
        return SourcePopularPagingSource({ sourceManager.getOrStub(sourceId) }, networkToLocalAnime)
    }

    override fun getLatest(sourceId: Long): SourcePagingSource {
        return SourceLatestPagingSource({ sourceManager.getOrStub(sourceId) }, networkToLocalAnime)
    }
}

fun mapSourceToDomainSource(source: AnimeSource): DomainSource = DomainSource(
    id = source.id,
    lang = source.lang,
    name = source.name,
    supportsLatest = false,
    isStub = false,
)
