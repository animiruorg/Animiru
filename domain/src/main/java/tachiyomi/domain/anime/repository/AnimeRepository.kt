package tachiyomi.domain.anime.repository

import aniyomi.domain.anime.model.SeasonAnime
import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.model.AnimeCustomUpdate
import tachiyomi.domain.anime.model.AnimeRemoteUpdate
import tachiyomi.domain.anime.model.AnimeUpdate
import tachiyomi.domain.anime.model.AnimeWithEpisodeCount
import tachiyomi.domain.library.model.LibraryAnime
import tachiyomi.domain.source.model.DeletableAnime

interface AnimeRepository {

    suspend fun getAnimeById(id: Long): Anime

    fun getAnimeByIdAsFlow(id: Long): Flow<Anime>

    suspend fun getAnimeByUrlAndSourceId(url: String, sourceId: Long): Anime?

    fun getAnimeByUrlAndSourceIdAsFlow(url: String, sourceId: Long): Flow<Anime?>

    suspend fun getFavorites(): List<Anime>

    suspend fun getSeenAnimeNotInLibrary(): List<Anime>

    suspend fun getLibraryAnime(): List<LibraryAnime>

    fun getLibraryAnimeAsFlow(): Flow<List<LibraryAnime>>

    fun getFavoritesBySourceId(sourceId: Long): Flow<List<Anime>>

    suspend fun getDuplicateLibraryAnime(id: Long, title: String): List<AnimeWithEpisodeCount>

    suspend fun getUpcomingAnime(
        statuses: Set<Long>,
        excludedCategories: List<Long>,
        includedCategories: List<Long>,
    ): Flow<List<Anime>>

    suspend fun resetViewerFlags(): Boolean

    suspend fun deleteNonLibraryAnime(sourceIds: List<Long>, keepSeenAnime: Boolean)

    suspend fun setAnimeCategories(animeId: Long, categoryIds: List<Long>)

    suspend fun getExcludedScanlators(animeId: Long): Set<String>

    fun getExcludedScanlatorsAsFlow(animeId: Long): Flow<Set<String>>

    suspend fun setExcludedScanlators(animeId: Long, excludedScanlators: Set<String>)

    suspend fun update(update: AnimeUpdate): Boolean

    suspend fun updateRemote(update: AnimeRemoteUpdate): Boolean

    suspend fun updateAll(animeUpdates: List<AnimeUpdate>): Boolean

    // AM -->
    suspend fun updateFromRemote(
        removedIds: List<Long>,
        added: List<Anime>,
        updated: List<AnimeRemoteUpdate>,
    ): List<Anime>
    // <-- AM

    suspend fun insertNetworkAnime(anime: List<Anime>): List<Anime>

    // AY -->
    suspend fun getAnimeSeasonsById(parentId: Long): List<SeasonAnime>

    fun getAnimeSeasonsByIdAsFlow(parentId: Long): Flow<List<SeasonAnime>>

    suspend fun removeParentIdByIds(animeIds: List<Long>)

    fun getDeletableParentAnime(): Flow<List<DeletableAnime>>

    suspend fun getChildrenByParentId(parentId: Long): List<Anime>
    // <-- AY

    // AM -->
    suspend fun getAllAnime(): List<Anime>

    suspend fun getAllAnimeThatNeedsSync(): List<Anime>

    suspend fun updateAnime(anime: Anime)

    suspend fun updateCustomInfo(customInfo: AnimeCustomUpdate)

    suspend fun updateCustomInfoAll(customInfos: List<AnimeCustomUpdate>)
    // <-- AM
}
