package tachiyomi.data.anime

import aniyomi.domain.anime.model.SeasonAnime
import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import logcat.LogPriority
import tachiyomi.core.common.util.lang.toLong
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.Database
import tachiyomi.data.subscribeToList
import tachiyomi.data.subscribeToOne
import tachiyomi.data.subscribeToOneOrNull
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.model.AnimeCustomUpdate
import tachiyomi.domain.anime.model.AnimeRemoteUpdate
import tachiyomi.domain.anime.model.AnimeUpdate
import tachiyomi.domain.anime.model.AnimeWithEpisodeCount
import tachiyomi.domain.anime.repository.AnimeRepository
import tachiyomi.domain.library.model.LibraryAnime
import tachiyomi.domain.source.model.DeletableAnime
import kotlin.time.Clock

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AnimeRepositoryImpl(
    private val database: Database,
) : AnimeRepository {

    override suspend fun getAnimeById(id: Long): Anime {
        return database.animeQueries.getAnimeById(id, AnimeMapper::mapAnime).awaitAsOne()
    }

    override fun getAnimeByIdAsFlow(id: Long): Flow<Anime> {
        return database.animeQueries.getAnimeById(id, AnimeMapper::mapAnime).subscribeToOne()
    }

    override suspend fun getAnimeByUrlAndSourceId(url: String, sourceId: Long): Anime? {
        return database.animeQueries.getAnimeByUrlAndSource(
            sourceId = sourceId,
            remoteUrl = url,
            mapper = AnimeMapper::mapAnime,
        ).awaitAsOneOrNull()
    }

    override fun getAnimeByUrlAndSourceIdAsFlow(url: String, sourceId: Long): Flow<Anime?> {
        return database.animeQueries.getAnimeByUrlAndSource(
            sourceId = sourceId,
            remoteUrl = url,
            mapper = AnimeMapper::mapAnime,
        ).subscribeToOneOrNull()
    }

    override suspend fun getFavorites(): List<Anime> {
        return database.animeQueries.getFavorites(AnimeMapper::mapAnime).awaitAsList()
    }

    override suspend fun getSeenAnimeNotInLibrary(): List<Anime> {
        return database.animeQueries.getSeenAnimeNotInLibrary(AnimeMapper::mapAnime).awaitAsList()
    }

    override suspend fun getLibraryAnime(): List<LibraryAnime> {
        return database.libraryViewQueries.library(AnimeMapper::mapLibraryAnime).awaitAsList()
    }

    override fun getLibraryAnimeAsFlow(): Flow<List<LibraryAnime>> {
        return database.libraryViewQueries.library(AnimeMapper::mapLibraryAnime).subscribeToList()
    }

    override fun getFavoritesBySourceId(sourceId: Long): Flow<List<Anime>> {
        return database.animeQueries.getFavoriteBySourceId(sourceId, AnimeMapper::mapAnime).subscribeToList()
    }

    override suspend fun getDuplicateLibraryAnime(id: Long, title: String): List<AnimeWithEpisodeCount> {
        return database.animeQueries.getDuplicateLibraryAnime(
            id = id,
            remoteTitle = title,
            mapper = AnimeMapper::mapAnimeWithEpisodeCount,
        ).awaitAsList()
    }

    override suspend fun getUpcomingAnime(
        statuses: Set<Long>,
        excludedCategories: List<Long>,
        includedCategories: List<Long>,
    ): Flow<List<Anime>> {
        val timeZone = TimeZone.currentSystemDefault()
        val startOfDay = Clock.System.now().toLocalDateTime(timeZone).date.atStartOfDayIn(timeZone)
        return database.animeQueries
            .getUpcomingAnime(
                startOfDay = startOfDay,
                statuses = statuses,
                includedEmpty = includedCategories.isEmpty(),
                includedCategories = includedCategories,
                excludedEmpty = excludedCategories.isEmpty(),
                excludedCategories = excludedCategories,
                AnimeMapper::mapAnime,
            )
            .subscribeToList()
    }

    override suspend fun resetViewerFlags(): Boolean {
        return try {
            database.animeQueries.resetViewerFlags()
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    override suspend fun deleteNonLibraryAnime(sourceIds: List<Long>, keepSeenAnime: Boolean) {
        database.animeQueries.deleteNonLibraryAnimeByAnimeIds(sourceIds, keepSeenAnime.toLong())
    }

    override suspend fun setAnimeCategories(animeId: Long, categoryIds: List<Long>) {
        database.transaction {
            database.anime_categoryQueries.deleteAnimeCategoryByAnimeId(animeId)
            categoryIds.forEach { categoryId ->
                database.anime_categoryQueries.insert(animeId = animeId, categoryId = categoryId)
            }
        }
    }

    override suspend fun getExcludedScanlators(animeId: Long): Set<String> {
        return database.excluded_scanlatorQueries
            .getExcludedScanlatorsByAnimeId(animeId)
            .awaitAsList()
            .toSet()
    }

    override suspend fun getExcludedScanlatorsByAnimeIds(animeIds: List<Long>): Map<Long, List<String>> {
        return database.excluded_scanlatorQueries
            .getExcludedScanlatorsByAnimeIds(animeIds)
            .awaitAsList()
            .groupBy({ it.anime_id }, { it.scanlator })
    }

    override fun getExcludedScanlatorsAsFlow(animeId: Long): Flow<Set<String>> {
        return database.excluded_scanlatorQueries
            .getExcludedScanlatorsByAnimeId(animeId)
            .subscribeToList()
            .map { it.toSet() }
    }

    override suspend fun setExcludedScanlators(animeId: Long, excludedScanlators: Set<String>) {
        database.transaction {
            val current = database.excluded_scanlatorQueries
                .getExcludedScanlatorsByAnimeId(animeId)
                .awaitAsList()
                .toSet()
            excludedScanlators.minus(current).forEach { scanlator ->
                database.excluded_scanlatorQueries.insert(animeId, scanlator)
            }
            val toRemove = current.minus(excludedScanlators)
            // An empty delete still tells every episoide query to run again
            if (toRemove.isNotEmpty()) {
                database.excluded_scanlatorQueries.remove(animeId, toRemove)
            }
        }
    }

    override suspend fun update(update: AnimeUpdate): Boolean {
        return try {
            partialUpdate(update)
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    override suspend fun updateAll(animeUpdates: List<AnimeUpdate>): Boolean {
        return try {
            partialUpdate(*animeUpdates.toTypedArray())
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    // AM -->
    override suspend fun updateFromRemote(
        removedIds: List<Long>,
        added: List<Anime>,
        updated: List<AnimeRemoteUpdate>,
    ): List<Anime> {
        return database.transactionWithResult {
            if (removedIds.isNotEmpty()) {
                database.animeQueries.removeParentIdByIds(removedIds)
            }
            val existing = added.map { it.id to it.url }
                .distinct()
                .toMutableSet()
            val stored = added.filter { existing.add(it.id to it.url) }.map { season ->
                val animeId = database.animeQueries.insertReturningId(
                    sourceId = season.id,
                    remoteUrl = season.url,
                    remoteTitle = season.ogTitle,
                    remoteAuthor = season.ogAuthor,
                    remoteArtist = season.ogArtist,
                    remoteDescription = season.ogDescription,
                    remoteGenre = season.ogGenre,
                    remoteStatus = season.ogStatus,
                    remoteCover = season.thumbnailUrl,
                    remoteUpdateStrategy = season.updateStrategy,
                    remoteMemo = season.memo,
                    remoteFetchType = season.fetchType,
                    remoteSeasonNumber = season.seasonNumber,
                    remoteSeasonSourceOrder = season.seasonSourceOrder,
                    remoteBackground = season.backgroundUrl,
                    userFavoriteAt = season.favoriteAt,
                    userNotes = season.notes,
                    userPlayerFlags = season.viewerFlags,
                    userEpisodeFlags = season.episodeFlags,
                    userSeasonFlags = season.seasonFlags,
                    stateEpisodeLastUpdate = season.lastUpdate,
                    stateEpisodeNextUpdate = season.nextUpdate,
                    stateEpisodeFetchInterval = season.fetchInterval.toLong(),
                    stateCoverLastModified = season.coverLastModified,
                    stateInitialized = season.initialized,
                    stateParentId = season.parentId,
                    stateBackgroundLastModified = season.backgroundLastModified,
                )
                    .awaitAsOne()
                season.copy(id = animeId)
            }
            updated.forEach { seasonUpdate ->
                database.animeQueries.updateRemote(
                    remoteArtist = seasonUpdate.artist,
                    remoteAuthor = seasonUpdate.author,
                    remoteDescription = seasonUpdate.description,
                    remoteGenre = seasonUpdate.genre,
                    remoteTitle = seasonUpdate.title,
                    remoteStatus = seasonUpdate.status,
                    remoteCover = seasonUpdate.thumbnailUrl,
                    stateInitialized = seasonUpdate.initialized,
                    stateCoverLastModified = seasonUpdate.coverLastModified,
                    remoteUpdateStrategy = seasonUpdate.updateStrategy,
                    stateParentId = seasonUpdate.parentId,
                    remoteSeasonNumber = seasonUpdate.seasonNumber,
                    remoteSeasonSourceOrder = seasonUpdate.seasonSourceOrder,
                    remoteBackground = seasonUpdate.backgroundUrl,
                    stateBackgroundLastModified = seasonUpdate.backgroundLastModified,
                    remoteMemo = seasonUpdate.memo,
                    id = seasonUpdate.id,
                )
            }
            stored
        }
    }
    // <-- AM

    override suspend fun insertNetworkAnime(anime: List<Anime>): List<Anime> {
        return database.transactionWithResult {
            anime.map {
                database.animeQueries.insertNetworkAnime(
                    sourceId = it.source,
                    remoteUrl = it.url,
                    remoteArtist = it.ogArtist,
                    remoteAuthor = it.ogAuthor,
                    remoteDescription = it.ogDescription,
                    remoteGenre = it.ogGenre,
                    remoteTitle = it.ogTitle,
                    remoteStatus = it.ogStatus,
                    remoteCover = it.thumbnailUrl,
                    // AY -->
                    remoteBackground = it.backgroundUrl,
                    // <-- AY
                    userFavoriteAt = it.favoriteAt,
                    stateEpisodeLastUpdate = it.lastUpdate,
                    stateEpisodeNextUpdate = it.nextUpdate,
                    stateEpisodeFetchInterval = it.fetchInterval.toLong(),
                    stateInitialized = it.initialized,
                    userPlayerFlags = it.viewerFlags,
                    userEpisodeFlags = it.episodeFlags,
                    stateCoverLastModified = it.coverLastModified,
                    // AY -->
                    stateBackgroundLastModified = it.backgroundLastModified,
                    // <-- AY
                    remoteUpdateStrategy = it.updateStrategy,
                    // AY -->
                    remoteFetchType = it.fetchType,
                    stateParentId = it.parentId,
                    userSeasonFlags = it.seasonFlags,
                    remoteSeasonNumber = it.seasonNumber,
                    remoteSeasonSourceOrder = it.seasonSourceOrder,
                    // <-- AY
                    remoteMemo = it.memo,
                    updateTitle = it.title.isNotBlank(),
                    updateCover = !it.thumbnailUrl.isNullOrBlank(),
                    updateDetails = it.initialized,
                    mapper = AnimeMapper::mapAnime,
                )
                    .awaitAsOne()
            }
        }
    }

    // AY -->
    override suspend fun getAnimeSeasonsById(parentId: Long): List<SeasonAnime> {
        return database.animeseasonsViewQueries.getAnimeSeasonsById(parentId, AnimeMapper::mapSeasonAnime).awaitAsList()
    }

    override fun getAnimeSeasonsByIdAsFlow(parentId: Long): Flow<List<SeasonAnime>> {
        return database.animeseasonsViewQueries.getAnimeSeasonsById(
            parentId,
            AnimeMapper::mapSeasonAnime,
        ).subscribeToList()
    }

    override suspend fun removeParentIdByIds(animeIds: List<Long>) {
        try {
            database.animeQueries.removeParentIdByIds(animeIds)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }

    override fun getDeletableParentAnime(): Flow<List<DeletableAnime>> {
        return database.animedeletableViewQueries.getDeletableParentAnime(AnimeMapper::mapDeletableAnime)
            .subscribeToList()
    }

    override suspend fun getChildrenByParentId(parentId: Long): List<Anime> {
        return database.animeQueries.getChildrenByParentId(parentId, AnimeMapper::mapAnime).awaitAsList()
    }
    // <-- AY

    override suspend fun updateRemote(update: AnimeRemoteUpdate): Boolean {
        return try {
            database.animeQueries.updateRemote(
                remoteArtist = update.artist,
                remoteAuthor = update.author,
                remoteDescription = update.description,
                remoteGenre = update.genre,
                remoteTitle = update.title,
                remoteStatus = update.status,
                remoteCover = update.thumbnailUrl,
                stateInitialized = update.initialized,
                stateCoverLastModified = update.coverLastModified,
                remoteUpdateStrategy = update.updateStrategy,
                remoteMemo = update.memo,
                id = update.id,
                // AM -->
                remoteBackground = update.backgroundUrl,
                stateBackgroundLastModified = update.backgroundLastModified,
                stateParentId = update.parentId,
                remoteSeasonNumber = update.seasonNumber,
                remoteSeasonSourceOrder = update.seasonSourceOrder,
                // <-- AM
            )
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    private suspend fun partialUpdate(vararg animeUpdates: AnimeUpdate) {
        database.transaction {
            animeUpdates.forEach { value ->
                with(value) {
                    database.animeQueries.update(
                        userFavoriteAtSet = isSet(::favoriteAt),
                        userFavoriteAt = favoriteAt,
                        stateEpisodeLastUpdate = lastUpdate,
                        stateEpisodeNextUpdate = nextUpdate,
                        userPlayerFlags = viewerFlags,
                        userEpisodeFlags = episodeFlags,
                        stateCoverLastModified = coverLastModified,
                        stateEpisodeFetchInterval = fetchInterval?.toLong(),
                        userNotes = notes,
                        // AM -->
                        userSeasonFlags = seasonFlags,
                        stateBackgroundLastModified = backgroundLastModified,
                        // <-- AM
                        id = id,
                    )
                }
            }
        }
    }

    // AM -->
    override suspend fun updateCustomInfo(customInfo: AnimeCustomUpdate) {
        database.animeQueries.updateCustomInformation(
            customArtist = customInfo.customArtist,
            customAuthor = customInfo.customAuthor,
            customDescription = customInfo.customDescription,
            customGenre = customInfo.customGenre,
            customTitle = customInfo.customTitle,
            customStatus = customInfo.customStatus,
            animeId = customInfo.id,
        )
    }

    override suspend fun updateCustomInfoAll(customInfos: List<AnimeCustomUpdate>) {
        database.transaction {
            customInfos.forEach { customInfo ->
                database.animeQueries.updateCustomInformation(
                    customArtist = customInfo.customArtist,
                    customAuthor = customInfo.customAuthor,
                    customDescription = customInfo.customDescription,
                    customGenre = customInfo.customGenre,
                    customTitle = customInfo.customTitle,
                    customStatus = customInfo.customStatus,
                    animeId = customInfo.id,
                )
            }
        }
    }
    // <-- AM
}
