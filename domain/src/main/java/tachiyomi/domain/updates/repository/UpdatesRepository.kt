package tachiyomi.domain.updates.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.updates.model.UpdatesWithRelations
import kotlin.time.Instant

interface UpdatesRepository {

    suspend fun awaitWithSeen(seen: Boolean, after: Instant, limit: Long): List<UpdatesWithRelations>

    fun subscribeAll(
        after: Instant,
        limit: Long,
        unseen: Boolean?,
        started: Boolean?,
        bookmarked: Boolean?,
        fillermarked: Boolean?,
        hideExcludedScanlators: Boolean,
        includedCategories: List<Long>,
        excludedCategories: List<Long>,
    ): Flow<List<UpdatesWithRelations>>

    fun subscribeWithSeen(seen: Boolean, after: Instant, limit: Long): Flow<List<UpdatesWithRelations>>
}
