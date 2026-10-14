package tachiyomi.domain.backup.repository

import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.model.AnimeUpdate
import tachiyomi.domain.backup.model.RestoredAnime

interface RestoreRepository {

    suspend fun getAnimeUrlsBySourceId(): Map<Long, List<String>>

    /**
     * Restores [entries] all together: either every one of them is restored or none is. [update] is what to change on each
     * entry once its episodes and history are in place, and is part of the same all-or-nothing restore.
     */
    suspend fun restoreAnime(entries: List<RestoredAnime>, update: suspend (Anime) -> AnimeUpdate)
}
