package tachiyomi.domain.source.model

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimeRelation
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SAnimeEpisodeUpdate
import eu.kanade.tachiyomi.animesource.model.SAnimeSeasonUpdate
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video

data class StubSource(
    override val id: Long,
    override val lang: String,
    override val name: String,
) : AnimeSource {

    private val isInvalid: Boolean = name.isBlank() || lang.isBlank()

    override val supportsLatest: Boolean = false

    override suspend fun getPopularAnime(page: Int): AnimesPage =
        throw SourceNotInstalledException()

    override suspend fun getLatestUpdates(page: Int): AnimesPage =
        throw SourceNotInstalledException()

    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage =
        throw SourceNotInstalledException()

    override suspend fun getAnimeEpisodeUpdate(
        anime: SAnime,
        episodes: List<SEpisode>,
        fetchDetails: Boolean,
        fetchEpisodes: Boolean,
    ): SAnimeEpisodeUpdate = throw SourceNotInstalledException()

    // AY -->
    override suspend fun getAnimeSeasonUpdate(
        anime: SAnime,
        seasons: List<SAnime>,
        fetchDetails: Boolean,
        fetchSeasons: Boolean,
    ): SAnimeSeasonUpdate = throw SourceNotInstalledException()

    override val supportsRelatedAnime: Boolean = false

    override suspend fun getRelatedAnimeList(anime: SAnime): List<AnimeRelation> =
        throw SourceNotInstalledException()

    override suspend fun getHosterList(episode: SEpisode): List<Hoster> =
        throw SourceNotInstalledException()

    // <-- AY
    override suspend fun getVideoList(hoster: Hoster): List<Video> =
        throw SourceNotInstalledException()

    override fun toString(): String =
        if (!isInvalid) "$name (${lang.uppercase()})" else id.toString()

    companion object {
        fun from(source: AnimeSource): StubSource {
            return StubSource(id = source.id, lang = source.lang, name = source.name)
        }
    }
}

class SourceNotInstalledException : Exception()
