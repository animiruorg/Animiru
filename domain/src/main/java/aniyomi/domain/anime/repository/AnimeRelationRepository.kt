package aniyomi.domain.anime.repository

import aniyomi.domain.anime.model.AnimeRelationGroup
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

interface AnimeRelationRepository {

    fun subscribeRelatedAnime(animeId: Long): Flow<List<AnimeRelationGroup>>

    suspend fun getLastFetchedAt(animeId: Long): Instant?

    suspend fun replaceRelations(
        animeId: Long,
        groups: List<Pair<String, List<Long>>>,
        fetchedAt: Instant,
    )
}
