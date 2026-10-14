package eu.kanade.tachiyomi.data.backup.restore.restorers

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.backup.models.BackupAnime
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import tachiyomi.domain.anime.interactor.FetchInterval
import tachiyomi.domain.backup.model.ParentAnime
import tachiyomi.domain.backup.model.RestoredAnime
import tachiyomi.domain.backup.model.RestoredHistory
import tachiyomi.domain.backup.repository.RestoreRepository
import tachiyomi.domain.category.interactor.GetCategories
import kotlin.time.Clock

@Inject
class AnimeRestorer(
    private val restoreRepository: RestoreRepository,
    private val getCategories: GetCategories,
    private val fetchInterval: FetchInterval,
) {

    private val timeZone = TimeZone.currentSystemDefault()
    private val now = Clock.System.now().toLocalDateTime(timeZone)
    private val currentFetchWindow = fetchInterval.getWindow(now.date, timeZone)

    suspend fun sortByNew(backupAnimes: List<BackupAnime>): List<BackupAnime> {
        val urlsBySource = restoreRepository.getAnimeUrlsBySourceId()

        val sortByNewComparator = compareBy<BackupAnime> { it.url in urlsBySource[it.source].orEmpty() }

        // AM -->
        val parents = backupAnimes.groupBy { it.parentId }
        val ids = backupAnimes.map { it.id }.toSet()
        val roots = backupAnimes
            .filter { it.parentId == null || it.parentId !in ids }
            .sortedWith(sortByNewComparator)

        val output = mutableListOf<BackupAnime>()
        val visited = mutableSetOf<Long?>()

        fun visit(anime: BackupAnime) {
            if (!visited.add(anime.id)) return
            output.add(anime)

            val children = parents[anime.id].orEmpty()
            children.sortedWith(sortByNewComparator).forEach(::visit)
        }

        roots.forEach(::visit)

        // For circular relationships
        if (output.size < backupAnimes.size) {
            val remaining = backupAnimes
                .filter { it.id !in visited }
                .sortedWith(sortByNewComparator)

            remaining.forEach(::visit)
        }

        return output
        // <-- AM
    }

    suspend fun restore(
        backupAnimes: List<BackupAnime>,
        backupCategories: List<BackupCategory>,
        // AM -->
        allBackupAnimes: List<BackupAnime>,
        ids: HashSet<Long>,
        // <-- AM
    ) {
        val dbCategoriesByName = getCategories.await().associateBy { it.name }
        val backupCategoriesByOrder = backupCategories.associateBy { it.order }

        val entries = backupAnimes.map { backupAnime ->
            // AM -->
            val animeToRestore = backupAnime.copy(
                parentId = backupAnime.parentId.takeIf {
                    backupAnime.id in ids && backupAnime.id != backupAnime.parentId
                },
            )

            val parentAnime = allBackupAnimes.find { it.id == backupAnime.parentId }?.let {
                ParentAnime(url = it.url, sourceId = it.source)
            }
            // <-- AM

            RestoredAnime(
                anime = animeToRestore.getAnimeImpl(),
                episodes = backupAnime.episodes.map { it.toEpisodeImpl() },
                categoryIds = backupAnime.categories.mapNotNull { backupCategoryOrder ->
                    backupCategoriesByOrder[backupCategoryOrder]?.let { backupCategory ->
                        dbCategoriesByName[backupCategory.name]?.id
                    }
                },
                history = backupAnime.history.map {
                    val history = it.getHistoryImpl()
                    RestoredHistory(it.url, history.seenAt)
                },
                tracks = backupAnime.tracking.map { it.getTrackImpl() },
                excludedScanlators = backupAnime.excludedScanlators,
                // AM -->
                parent = parentAnime,
                // <-- AM
            )
        }

        restoreRepository.restoreAnime(entries) {
            fetchInterval.withFetchInterval(it, now, timeZone, currentFetchWindow)
        }
    }
}
