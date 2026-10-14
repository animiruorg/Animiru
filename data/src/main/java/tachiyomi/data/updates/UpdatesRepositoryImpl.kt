package tachiyomi.data.updates

import app.cash.sqldelight.async.coroutines.awaitAsList
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import tachiyomi.core.common.util.lang.toLong
import tachiyomi.data.Database
import tachiyomi.data.subscribeToList
import tachiyomi.domain.anime.model.AnimeCover
import tachiyomi.domain.updates.model.UpdatesWithRelations
import tachiyomi.domain.updates.repository.UpdatesRepository

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class UpdatesRepositoryImpl(
    private val database: Database,
) : UpdatesRepository {

    override suspend fun awaitWithSeen(
        seen: Boolean,
        after: Long,
        limit: Long,
    ): List<UpdatesWithRelations> {
        return database.updatesViewQueries.getUpdatesBySeenStatus(
            seen = seen,
            after = after,
            limit = limit,
            mapper = ::mapUpdatesWithRelations,
        ).awaitAsList()
    }

    override fun subscribeAll(
        after: Long,
        limit: Long,
        unseen: Boolean?,
        started: Boolean?,
        bookmarked: Boolean?,
        fillermarked: Boolean?,
        hideExcludedScanlators: Boolean,
        includedCategories: List<Long>,
        excludedCategories: List<Long>,
    ): Flow<List<UpdatesWithRelations>> {
        return database.updatesViewQueries.getRecentUpdatesWithFilters(
            after = after,
            limit = limit,
            // invert because unseen in Kotlin -> seen column in SQL
            seen = unseen?.let { !it },
            started = started?.toLong(),
            bookmarked = bookmarked,
            fillermarked = fillermarked,
            hideExcludedScanlators = hideExcludedScanlators.toLong(),
            includedEmpty = includedCategories.isEmpty(),
            excludedEmpty = excludedCategories.isEmpty(),
            includedCategories = includedCategories,
            excludedCategories = excludedCategories,
            mapper = ::mapUpdatesWithRelations,
        ).subscribeToList()
    }

    override fun subscribeWithSeen(
        seen: Boolean,
        after: Long,
        limit: Long,
    ): Flow<List<UpdatesWithRelations>> {
        return database.updatesViewQueries.getUpdatesBySeenStatus(
            seen = seen,
            after = after,
            limit = limit,
            mapper = ::mapUpdatesWithRelations,
        )
            .subscribeToList()
    }

    @Suppress("UNUSED_PARAMETER")
    private fun mapUpdatesWithRelations(
        animeId: Long,
        // AM -->
        customAnimeTitle: String?,
        // <-- AM
        animeTitle: String,
        episodeId: Long,
        episodeName: String,
        scanlator: String?,
        episodeUrl: String,
        seen: Boolean,
        bookmark: Boolean,
        // AY -->
        fillermark: Boolean,
        // <-- AY
        lastSecondSeen: Long,
        // AY -->
        totalSeconds: Long,
        // <-- AY
        sourceId: Long,
        favorite: Boolean,
        thumbnailUrl: String?,
        coverLastModified: Long,
        dateUpload: Long,
        dateFetch: Long,
        excludedScanlator: String?,
    ): UpdatesWithRelations = UpdatesWithRelations(
        animeId = animeId,
        // AM (CUSTOM_INFORMATION) -->
        customAnimeTitle = customAnimeTitle,
        ogAnimeTitle = animeTitle,
        // <-- AM (CUSTOM_INFORMATION)
        episodeId = episodeId,
        episodeName = episodeName,
        scanlator = scanlator,
        episodeUrl = episodeUrl,
        seen = seen,
        bookmark = bookmark,
        // AY -->
        fillermark = fillermark,
        // <-- AY
        lastSecondSeen = lastSecondSeen,
        // AY -->
        totalSeconds = totalSeconds,
        // <-- AY
        sourceId = sourceId,
        dateFetch = dateFetch,
        coverData = AnimeCover(
            animeId = animeId,
            sourceId = sourceId,
            isAnimeFavorite = favorite,
            url = thumbnailUrl,
            lastModified = coverLastModified,
        ),
    )
}
