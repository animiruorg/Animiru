package mihon.core.migration.migrations

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.library.LibraryUpdateWorker
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext

@Inject
@ContributesIntoSet(AppScope::class)
class UseWorkManagerMigration(
    private val context: Context,
) : Migration {
    override val version = 96f

    // Fully utilize WorkManager for library updates
    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        LibraryUpdateWorker.cancelAllWorks(context)
        LibraryUpdateWorker.setupTask(context)

        return true
    }
}
