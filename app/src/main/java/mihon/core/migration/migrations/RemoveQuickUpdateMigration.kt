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
class RemoveQuickUpdateMigration(
    private val context: Context,
    private val libraryPreferences: LibraryPreferences,
) : Migration {
    override val version = 71f

    // Handle removed every 3, 4, 6, and 8 hour library updates
    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val updateInterval = libraryPreferences.autoUpdateInterval.get()
        if (updateInterval in listOf(3, 4, 6, 8)) {
            libraryPreferences.autoUpdateInterval.set(12)
            LibraryUpdateWorker.setupTask(context, 12)
        }

        return true
    }
}
