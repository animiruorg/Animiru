package mihon.core.migration.migrations

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import logcat.LogPriority
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.anime.model.AnimeCustomUpdate
import tachiyomi.domain.anime.repository.AnimeRepository
import java.io.File

@Inject
@ContributesIntoSet(AppScope::class)
class MoveCustomInfoMigration(
    private val context: Context,
    private val animeRepository: AnimeRepository,
) : Migration {
    override val version = 153f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val editJson = File(context.getExternalFilesDir(null), "edits.json")

        val json = try {
            editJson.inputStream().use {
                Json.decodeFromStream<AnimeList>(it)
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to read edits.json for migration" }
            return false
        }

        val updates = json.animes.orEmpty().mapNotNull {
            val id = it.id ?: return@mapNotNull null
            AnimeCustomUpdate(
                id = id,
                customArtist = it.artist,
                customAuthor = it.author,
                customDescription = it.description,
                customGenre = it.genre,
                customTitle = it.title,
                customStatus = it.status,
            )
        }

        animeRepository.updateCustomInfoAll(updates)

        editJson.delete()

        return true
    }

    @Serializable
    private data class AnimeList(
        val animes: List<AnimeJson>? = null,
    )

    @Serializable
    private data class AnimeJson(
        var id: Long? = null,
        val title: String? = null,
        val author: String? = null,
        val artist: String? = null,
        val description: String? = null,
        val genre: List<String>? = null,
        val status: Long? = null,
    )
}
