package mihon.core.migration.migrations

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.domain.anime.interactor.GetFavorites
import tachiyomi.domain.anime.interactor.SetAnimeEpisodeFlags

@Inject
@ContributesIntoSet(AppScope::class)
class EpisodeThumbnailSizeMigration(
    private val getFavorites: GetFavorites,
    private val setAnimeEpisodeFlags: SetAnimeEpisodeFlags,
) : Migration {
    override val version = 149f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val anime = getFavorites.await()
        anime.forEach { setAnimeEpisodeFlags.awaitSetThumbnailSize(it, 4) }
        return true
    }
}
