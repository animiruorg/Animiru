package tachiyomi.domain.episode.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.episode.model.EpisodeRemoteUpdate
import tachiyomi.domain.episode.model.EpisodeUpdate

interface EpisodeRepository {

    suspend fun addAll(episodes: List<Episode>): List<Episode>

    suspend fun update(episodeUpdate: EpisodeUpdate)

    suspend fun updateAll(episodeUpdates: List<EpisodeUpdate>)

    suspend fun updateAllRemote(episodeUpdates: List<EpisodeRemoteUpdate>)

    suspend fun removeEpisodesWithIds(episodeIds: List<Long>)

    suspend fun getEpisodeByAnimeId(animeId: Long, applyScanlatorFilter: Boolean = false): List<Episode>

    suspend fun getScanlatorsByAnimeId(animeId: Long): List<String>

    fun getScanlatorsByAnimeIdAsFlow(animeId: Long): Flow<List<String>>

    suspend fun getBookmarkedEpisodesByAnimeId(animeId: Long): List<Episode>

    suspend fun getEpisodeById(id: Long): Episode?

    suspend fun getEpisodeByAnimeIdAsFlow(animeId: Long, applyScanlatorFilter: Boolean = false): Flow<List<Episode>>

    suspend fun getEpisodeByUrlAndAnimeId(url: String, animeId: Long): Episode?
}
