package aniyomi.data.anime

import aniyomi.domain.anime.model.AnimeRelationGroup
import aniyomi.domain.anime.repository.AnimeRelationRepository
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.data.Database
import tachiyomi.data.anime.AnimeMapper
import tachiyomi.data.subscribeToList
import kotlin.time.Instant

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AnimeRelationRepositoryImpl(
    private val database: Database,
) : AnimeRelationRepository {

    override suspend fun getLastFetchedAt(animeId: Long): Instant? {
        return database.anime_relationQueries.getLastFetchedAt(animeId).awaitAsOneOrNull()
    }

    override suspend fun replaceRelations(
        animeId: Long,
        groups: List<Pair<String, List<Long>>>,
        fetchedAt: Instant,
    ) {
        database.transaction {
            database.anime_relationQueries.deleteByAnimeId(animeId)
            var sortOrder = 0L
            groups.forEach { (name, relatedIds) ->
                relatedIds.forEach { relatedId ->
                    database.anime_relationQueries.insert(
                        animeId = animeId,
                        relatedAnimeId = relatedId,
                        name = name,
                        sortOrder = sortOrder++,
                        lastFetchedAt = fetchedAt,
                    )
                }
            }
        }
    }

    override fun subscribeRelatedAnime(animeId: Long): Flow<List<AnimeRelationGroup>> {
        return database.anime_relationQueries.getRelatedAnimeByAnimeId(
            animeId,
            AnimeMapper::mapRelatedAnime,
        )
            .subscribeToList()
            .map { rows ->
                // The query is already ordered, and groupBy preserves encounter order.
                rows.groupBy({ it.first }, { it.second })
                    .map { (name, anime) -> AnimeRelationGroup(name, anime) }
            }
    }
}
