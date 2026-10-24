package mihon.core.migration.migrations

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.library.LibraryUpdateWorker
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.domain.library.service.LibraryPreferences

@Inject
@ContributesIntoSet(AppScope::class)
class RemoveOneTwoHourUpdateMigration(
    private val context: Context,
    private val libraryPreferences: LibraryPreferences,
) : Migration {
    override val version = 61f

    // Handle removed every 1 or 2 hour library updates
    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val updateInterval = libraryPreferences.autoUpdateInterval.get()
        if (updateInterval == 1 || updateInterval == 2) {
            libraryPreferences.autoUpdateInterval.set(3)
            LibraryUpdateWorker.setupTask(context, 3)
        }

        return true
    }
}
