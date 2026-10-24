package eu.kanade.domain.anime.interactor

import aniyomi.domain.anime.repository.AnimeRelationRepository
import dev.zacsweers.metro.Inject
import eu.kanade.domain.anime.model.toSAnime
import mihon.domain.anime.model.toDomainAnime
import tachiyomi.domain.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.source.service.SourceManager
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

@Inject
class SyncRelatedAnimeWithSource(
    private val sourceManager: SourceManager,
    private val networkToLocalAnime: NetworkToLocalAnime,
    private val relationRepository: AnimeRelationRepository,
) {

    suspend fun await(anime: Anime, forceRefresh: Boolean = false) {
        val source = sourceManager.get(anime.source)?.takeIf { it.supportsRelatedAnime } ?: return

        val now = Clock.System.now()
        if (!forceRefresh) {
            val lastFetchedAt = relationRepository.getLastFetchedAt(anime.id)
            if (lastFetchedAt != null && now - lastFetchedAt < TTL) return
        }

        val groups = source.getRelatedAnimeList(anime.toSAnime())
            .map { relation ->
                relation.name to relation.animes
                    .distinctBy { it.url }
                    .filterNot { it.url == anime.url }
                    .take(MAX_PER_GROUP)
                    .map { networkToLocalAnime(it.toDomainAnime(anime.source)).id }
            }
            .filter { (_, ids) -> ids.isNotEmpty() }

        relationRepository.replaceRelations(anime.id, groups, now)
    }

    companion object {
        private val TTL = 7.days
        private const val MAX_PER_GROUP = 10
    }
}
