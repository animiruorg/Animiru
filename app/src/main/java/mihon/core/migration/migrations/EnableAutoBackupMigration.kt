package mihon.core.migration.migrations

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.backup.create.BackupCreateWorker
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.domain.backup.service.BackupPreferences

@Inject
@ContributesIntoSet(AppScope::class)
class EnableAutoBackupMigration(
    private val context: Context,
    private val backupPreferences: BackupPreferences,
) : Migration {
    override val version = 84f

    // Always attempt automatic backup creation
    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        if (backupPreferences.backupInterval.get() == 0) {
            backupPreferences.backupInterval.set(12)
            BackupCreateWorker.setupTask(context)
        }

        return true
    }
}
