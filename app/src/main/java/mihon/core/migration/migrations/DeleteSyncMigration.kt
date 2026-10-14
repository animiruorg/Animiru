package mihon.core.migration.migrations

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext

@Inject
@ContributesIntoSet(AppScope::class)
class DeleteSyncMigration(
    private val context: Context,
) : Migration {
    override val version = 152f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit(commit = true) {
            remove("connection_sync_client_host")
            remove("connection_sync_client_api_key")
            remove("last_sync_timestamp")
            remove("sync_etag")
            remove("sync_interval")
            remove("connection_google_drive_access_token")
            remove("connection_google_drive_refresh_token")
            remove("unique_device_id")
            remove("sync_anime_lib_entries")
            remove("sync_anime_categories")
            remove("sync_episodes")
            remove("sync_anime_tracking")
            remove("sync_anime_history")
            remove("sync_app_settings")
            remove("sync_source_settings")
            remove("sync_private_settings")
            remove("sync_custom_information")
            remove("sync_anime_lib_entries")
            remove("sync_anime_categories")
            remove("sync_episodes")
            remove("sync_anime_tracking")
            remove("sync_app_settings")
            remove("sync_source_settings")
            remove("sync_private_settings")
            remove("sync_custom_information")
            remove("sync_on_app_start")
            remove("sync_on_app_resume")
            remove("sync_on_episode_seen")
            remove("sync_on_episode_open")
            remove("sync_on_app_start")
            remove("sync_on_app_resume")
            remove("sync_on_episode_seen")
            remove("sync_on_episode_open")
        }

        return true
    }
}
