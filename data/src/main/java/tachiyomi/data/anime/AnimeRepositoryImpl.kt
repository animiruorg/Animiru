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

    override suspend fun deleteNonLibraryAnime(sourceIds: List<Long>, keepSeenAnime: Boolean) {
        database.animesQueries.deleteAnimesNotInLibraryByAnimeIds(sourceIds, keepSeenAnime.toLong())
    }

    override suspend fun setAnimeCategories(animeId: Long, categoryIds: List<Long>) {
        database.transaction {
            database.animes_categoriesQueries.deleteAnimeCategoryByAnimeId(animeId)
            categoryIds.forEach { categoryId ->
                database.animes_categoriesQueries.insert(animeId, categoryId)
            }
        }
    }

    override suspend fun getExcludedScanlators(animeId: Long): Set<String> {
        return database.excluded_scanlatorsQueries
            .getExcludedScanlatorsByAnimeId(animeId)
            .awaitAsList()
            .toSet()
    }

    override fun getExcludedScanlatorsAsFlow(animeId: Long): Flow<Set<String>> {
        return database.excluded_scanlatorsQueries
            .getExcludedScanlatorsByAnimeId(animeId)
            .subscribeToList()
            .map { it.toSet() }
    }

    override suspend fun setExcludedScanlators(animeId: Long, excludedScanlators: Set<String>) {
        database.transaction {
            val current = database.excluded_scanlatorsQueries
                .getExcludedScanlatorsByAnimeId(animeId)
                .awaitAsList()
                .toSet()
            excludedScanlators.minus(current).forEach { scanlator ->
                database.excluded_scanlatorsQueries.insert(animeId, scanlator)
            }
            database.excluded_scanlatorsQueries.remove(animeId, current.minus(excludedScanlators))
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
                database.animesQueries.removeParentIdByIds(removedIds)
            }
            val existing = added.map { it.id to it.url }
                .distinct()
                .toMutableSet()
            val stored = added.filter { existing.add(it.id to it.url) }.map { season ->
                val animeId = database.animesQueries.insertReturningId(
                    season.source,
                    season.url,
                    season.artist,
                    season.author,
                    season.description,
                    season.genre,
                    season.title,
                    season.status,
                    season.thumbnailUrl,
                    season.favoriteAt,
                    season.lastUpdate,
                    season.nextUpdate,
                    season.initialized,
                    season.viewerFlags,
                    season.episodeFlags,
                    season.coverLastModified,
                    season.updateStrategy,
                    season.fetchInterval.toLong(),
                    season.notes,
                    season.fetchType,
                    season.parentId,
                    season.seasonFlags,
                    season.seasonNumber,
                    season.seasonSourceOrder,
                    season.backgroundUrl,
                    season.backgroundLastModified,
                    season.memo,
                )
                    .awaitAsOne()
                season.copy(id = animeId)
            }
            updated.forEach { seasonUpdate ->
                database.animesQueries.updateRemote(
                    artist = seasonUpdate.artist,
                    author = seasonUpdate.author,
                    description = seasonUpdate.description,
                    genre = seasonUpdate.genre,
                    title = seasonUpdate.title,
                    status = seasonUpdate.status,
                    thumbnailUrl = seasonUpdate.thumbnailUrl,
                    initialized = seasonUpdate.initialized,
                    coverLastModified = seasonUpdate.coverLastModified,
                    updateStrategy = seasonUpdate.updateStrategy,
                    parentId = seasonUpdate.parentId,
                    seasonNumber = seasonUpdate.seasonNumber,
                    seasonSourceOrder = seasonUpdate.seasonSourceOrder,
                    backgroundUrl = seasonUpdate.backgroundUrl,
                    backgroundLastModified = seasonUpdate.backgroundLastModified,
                    memo = seasonUpdate.memo,
                    animeId = seasonUpdate.id,
                )
            }
            stored
        }
    }
    // <-- AM

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
                    favoriteAt = it.favoriteAt,
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

    override suspend fun updateRemote(update: AnimeRemoteUpdate): Boolean {
        return try {
            database.animesQueries.updateRemote(
                artist = update.artist,
                author = update.author,
                description = update.description,
                genre = update.genre,
                title = update.title,
                status = update.status,
                thumbnailUrl = update.thumbnailUrl,
                initialized = update.initialized,
                coverLastModified = update.coverLastModified,
                updateStrategy = update.updateStrategy,
                memo = update.memo,
                animeId = update.id,
                // AM -->
                backgroundUrl = update.backgroundUrl,
                backgroundLastModified = update.backgroundLastModified,
                parentId = update.parentId,
                seasonNumber = update.seasonNumber,
                seasonSourceOrder = update.seasonSourceOrder,
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
                    database.animesQueries.update(
                        favoriteAtSet = isSet(::favoriteAt),
                        favoriteAt = favoriteAt,
                        lastUpdate = lastUpdate,
                        nextUpdate = nextUpdate,
                        viewer = viewerFlags,
                        episodeFlags = episodeFlags,
                        coverLastModified = coverLastModified,
                        calculateInterval = fetchInterval?.toLong(),
                        notes = notes,
                        // AM -->
                        seasonFlags = seasonFlags,
                        backgroundLastModified = backgroundLastModified,
                        // <-- AM
                        animeId = id,
                    )
                }
            }
        }
    }

    // AM -->
    override suspend fun getAllAnime(): List<Anime> {
        return database.animesQueries.getAllAnime(AnimeMapper::mapAnime).awaitAsList()
    }

    override suspend fun getAllAnimeThatNeedsSync(): List<Anime> {
        return database.animesQueries.getFavorites(AnimeMapper::mapAnime).awaitAsList()
    }

    override suspend fun updateAnime(anime: Anime) {
        database.animesQueries.updateFromBackup(
            artist = anime.artist,
            author = anime.author,
            description = anime.description,
            genre = anime.genre,
            title = anime.title,
            status = anime.status,
            thumbnailUrl = anime.thumbnailUrl,
            favoriteAt = anime.favoriteAt,
            lastUpdate = anime.lastUpdate,
            initialized = anime.initialized,
            viewer = anime.viewerFlags,
            episodeFlags = anime.episodeFlags,
            coverLastModified = anime.coverLastModified,
            updateStrategy = anime.updateStrategy,
            notes = anime.notes,
            // AM -->
            fetchType = anime.fetchType,
            parentId = anime.parentId,
            seasonFlags = anime.seasonFlags,
            seasonNumber = anime.seasonNumber,
            seasonSourceOrder = anime.seasonSourceOrder,
            backgroundUrl = anime.backgroundUrl,
            backgroundLastModified = anime.backgroundLastModified,
            // <-- AM
            memo = anime.memo,
            animeId = anime.id,
        )
    }
    // <-- AM
}
