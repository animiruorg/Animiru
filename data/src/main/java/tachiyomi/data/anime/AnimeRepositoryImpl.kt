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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.Database
import tachiyomi.data.FetchTypeColumnAdapter
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.UpdateStrategyColumnAdapter
import tachiyomi.data.subscribeToList
import tachiyomi.data.subscribeToOne
import tachiyomi.data.subscribeToOneOrNull
import tachiyomi.domain.anime.model.Anime
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
        return database.animesQueries.getAnimeById(id, AnimeMapper::mapAnime).awaitAsOne()
    }

    override fun getAnimeByIdAsFlow(id: Long): Flow<Anime> {
        return database.animesQueries.getAnimeById(id, AnimeMapper::mapAnime).subscribeToOne()
    }

    override suspend fun getAnimeByUrlAndSourceId(url: String, sourceId: Long): Anime? {
        return database.animesQueries.getAnimeByUrlAndSource(
            url,
            sourceId,
            AnimeMapper::mapAnime,
        ).awaitAsOneOrNull()
    }

    override fun getAnimeByUrlAndSourceIdAsFlow(url: String, sourceId: Long): Flow<Anime?> {
        return database.animesQueries.getAnimeByUrlAndSource(
            url,
            sourceId,
            AnimeMapper::mapAnime,
        ).subscribeToOneOrNull()
    }

    override suspend fun getFavorites(): List<Anime> {
        return database.animesQueries.getFavorites(AnimeMapper::mapAnime).awaitAsList()
    }

    override suspend fun getSeenAnimeNotInLibrary(): List<Anime> {
        return database.animesQueries.getSeenAnimeNotInLibrary(AnimeMapper::mapAnime).awaitAsList()
    }

    override suspend fun getLibraryAnime(): List<LibraryAnime> {
        return database.libraryViewQueries.library(AnimeMapper::mapLibraryAnime).awaitAsList()
    }

    override fun getLibraryAnimeAsFlow(): Flow<List<LibraryAnime>> {
        return database.libraryViewQueries.library(AnimeMapper::mapLibraryAnime).subscribeToList()
    }

    override fun getFavoritesBySourceId(sourceId: Long): Flow<List<Anime>> {
        return database.animesQueries.getFavoriteBySourceId(sourceId, AnimeMapper::mapAnime).subscribeToList()
    }

    override suspend fun getDuplicateLibraryAnime(id: Long, title: String): List<AnimeWithEpisodeCount> {
        return database.animesQueries.getDuplicateLibraryAnime(
            id,
            title,
            AnimeMapper::mapAnimeWithEpisodeCount,
        ).awaitAsList()
    }

    override suspend fun getUpcomingAnime(
        statuses: Set<Long>,
        excludedCategories: List<Long>,
        includedCategories: List<Long>,
    ): Flow<List<Anime>> {
        val timeZone = TimeZone.currentSystemDefault()
        val epochMillis =
            Clock.System.now().toLocalDateTime(timeZone).date.atStartOfDayIn(timeZone).toEpochMilliseconds()
        return database.animesQueries
            .getUpcomingAnime(
                startOfDay = epochMillis,
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
            database.animesQueries.resetViewerFlags()
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    override suspend fun setAnimeCategories(animeId: Long, categoryIds: List<Long>) {
        database.transaction {
            database.animes_categoriesQueries.deleteAnimeCategoryByAnimeId(animeId)
            categoryIds.forEach { categoryId ->
                database.animes_categoriesQueries.insert(animeId, categoryId)
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

    override suspend fun insertNetworkAnime(anime: List<Anime>): List<Anime> {
        return database.transactionWithResult {
            anime.map {
                database.animesQueries.insertNetworkAnime(
                    source = it.source,
                    url = it.url,
                    artist = it.artist,
                    author = it.author,
                    description = it.description,
                    genre = it.genre,
                    title = it.title,
                    status = it.status,
                    thumbnailUrl = it.thumbnailUrl,
                    // AY -->
                    backgroundUrl = it.backgroundUrl,
                    // <-- AY
                    favorite = it.favorite,
                    lastUpdate = it.lastUpdate,
                    nextUpdate = it.nextUpdate,
                    calculateInterval = it.fetchInterval.toLong(),
                    initialized = it.initialized,
                    viewerFlags = it.viewerFlags,
                    episodeFlags = it.episodeFlags,
                    coverLastModified = it.coverLastModified,
                    // AY -->
                    backgroundLastModified = it.backgroundLastModified,
                    // <-- AY
                    dateAdded = it.dateAdded,
                    updateStrategy = it.updateStrategy,
                    // AY -->
                    fetchType = it.fetchType,
                    parentId = it.parentId,
                    seasonFlags = it.seasonFlags,
                    seasonNumber = it.seasonNumber,
                    seasonSourceOrder = it.seasonSourceOrder,
                    // <-- AY
                    memo = it.memo,
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
            database.animesQueries.removeParentIdByIds(animeIds)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }

    override fun getDeletableParentAnime(): Flow<List<DeletableAnime>> {
        return database.animedeletableViewQueries.getDeletableParentAnime(AnimeMapper::mapDeletableAnime)
            .subscribeToList()
    }

    override suspend fun getChildrenByParentId(parentId: Long): List<Anime> {
        return database.animesQueries.getChildrenByParentId(parentId, AnimeMapper::mapAnime).awaitAsList()
    }
    // <-- AY

    private suspend fun partialUpdate(vararg animeUpdates: AnimeUpdate) {
        database.transaction {
            animeUpdates.forEach { value ->
                database.animesQueries.update(
                    source = value.source,
                    url = value.url,
                    artist = value.artist,
                    author = value.author,
                    description = value.description,
                    genre = value.genre,
                    title = value.title,
                    status = value.status,
                    thumbnailUrl = value.thumbnailUrl,
                    // AY -->
                    backgroundUrl = value.backgroundUrl,
                    // <-- AY
                    favorite = value.favorite,
                    lastUpdate = value.lastUpdate,
                    nextUpdate = value.nextUpdate,
                    calculateInterval = value.fetchInterval?.toLong(),
                    initialized = value.initialized,
                    viewer = value.viewerFlags,
                    episodeFlags = value.episodeFlags,
                    coverLastModified = value.coverLastModified,
                    // AY -->
                    backgroundLastModified = value.backgroundLastModified,
                    // <-- AY
                    dateAdded = value.dateAdded,
                    animeId = value.id,
                    updateStrategy = value.updateStrategy,
                    notes = value.notes,
                    // AY -->
                    fetchType = value.fetchType,
                    parentId = value.parentId,
                    seasonFlags = value.seasonFlags,
                    seasonNumber = value.seasonNumber,
                    seasonSourceOrder = value.seasonSourceOrder,
                    // <-- AY
                    memo = value.memo,
                )
            }
        }
    }
}
