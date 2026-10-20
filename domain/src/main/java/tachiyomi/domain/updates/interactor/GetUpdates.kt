package tachiyomi.domain.updates.interactor

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.updates.model.UpdatesWithRelations
import tachiyomi.domain.updates.repository.UpdatesRepository
import kotlin.time.Instant

@Inject
class GetUpdates(
    private val repository: UpdatesRepository,
) {

    suspend fun await(seen: Boolean, after: Instant): List<UpdatesWithRelations> {
        return repository.awaitWithSeen(seen, after, limit = 500)
    }

    fun subscribe(
        instant: Instant,
        unseen: Boolean?,
        started: Boolean?,
        bookmarked: Boolean?,
        fillermarked: Boolean?,
        hideExcludedScanlators: Boolean,
        includedCategories: List<Long>,
        excludedCategories: List<Long>,
    ): Flow<List<UpdatesWithRelations>> {
        return repository.subscribeAll(
            instant,
            limit = 500,
            unseen = unseen,
            started = started,
            bookmarked = bookmarked,
            fillermarked = fillermarked,
            hideExcludedScanlators = hideExcludedScanlators,
            includedCategories = includedCategories,
            excludedCategories = excludedCategories,
        )
    }

    fun subscribe(seen: Boolean, after: Instant): Flow<List<UpdatesWithRelations>> {
        return repository.subscribeWithSeen(seen, after, limit = 500)
    }
}
