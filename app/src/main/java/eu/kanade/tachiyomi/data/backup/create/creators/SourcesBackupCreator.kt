package eu.kanade.tachiyomi.data.backup.create.creators

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.data.backup.models.BackupAnime
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import tachiyomi.domain.source.service.SourceManager

@Inject
class SourcesBackupCreator(
    private val sourceManager: SourceManager,
) {

    suspend operator fun invoke(sourceIds: Set<Long>): List<BackupSource> {
        return sourceIds.map { sourceManager.getOrStub(it).toBackupSource() }
    }
}

private fun AnimeSource.toBackupSource() =
    BackupSource(
        name = this.name,
        sourceId = this.id,
    )
