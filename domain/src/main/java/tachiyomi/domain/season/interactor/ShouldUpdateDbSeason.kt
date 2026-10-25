// AY -->
package tachiyomi.domain.season.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.anime.model.Anime

@Inject
class ShouldUpdateDbSeason {
    fun await(dbSeason: Anime, sourceSeason: Anime): Boolean {
        return dbSeason.title != sourceSeason.title ||
            dbSeason.seasonNumber != sourceSeason.seasonNumber ||
            dbSeason.seasonSourceOrder != sourceSeason.seasonSourceOrder ||
            dbSeason.parentId != sourceSeason.parentId
    }
}
// <-- AY
