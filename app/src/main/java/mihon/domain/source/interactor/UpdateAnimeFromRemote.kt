package mihon.domain.source.interactor

import animiru.core.cache.ThumbnailCache
import dev.zacsweers.metro.Inject
import eu.kanade.domain.anime.interactor.SyncSeasonsWithSource
import eu.kanade.domain.anime.model.hasCustomBackground
import eu.kanade.domain.anime.model.hasCustomCover
import eu.kanade.domain.anime.model.toSAnime
import eu.kanade.domain.episode.interactor.SyncEpisodesWithSource
import eu.kanade.domain.episode.model.copyFromSEpisode
import eu.kanade.domain.episode.model.toSEpisode
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.data.cache.BackgroundCache
import eu.kanade.tachiyomi.data.cache.CoverCache
import logcat.LogPriority
import mihon.domain.source.interactor.models.RemoteAnimeEpisodeUpdate
import mihon.domain.source.interactor.models.RemoteAnimeSeasonUpdate
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.episode.EpisodeSanitizer
import tachiyomi.data.episode.EpisodeSanitizer.sanitize
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.model.AnimeUpdate
import tachiyomi.domain.anime.repository.AnimeRepository
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.episode.repository.EpisodeRepository
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.source.local.isLocal
import kotlin.time.Clock

@Inject
class UpdateAnimeFromRemote(
    private val sourceManager: SourceManager,
    private val episodeRepository: EpisodeRepository,
    private val animeRepository: AnimeRepository,
    private val syncEpisodesWithSource: SyncEpisodesWithSource,
    private val syncSeasonsWithSource: SyncSeasonsWithSource,
    private val coverCache: CoverCache,
    // AM -->
    private val backgroundCache: BackgroundCache,
    private val thumbnailCache: ThumbnailCache,
    // <-- AM
) {
    suspend fun awaitEpisodesUpdate(
        anime: Anime,
        fetchDetails: Boolean = false,
        fetchEpisodes: Boolean = false,
        manualFetch: Boolean = false,
        fetchWindow: Pair<Long, Long> = Pair(0, 0),
    ): Result<RemoteAnimeEpisodeUpdate> {
        val source = sourceManager.getOrStub(anime.source)
        return awaitEpisodesUpdate(
            source = source,
            anime = anime,
            fetchDetails = fetchDetails,
            fetchEpisodes = fetchEpisodes,
            manualFetch = manualFetch,
            fetchWindow = fetchWindow,
        )
    }

    suspend fun awaitEpisodesUpdate(
        source: AnimeSource,
        anime: Anime,
        fetchDetails: Boolean = false,
        fetchEpisodes: Boolean = false,
        manualFetch: Boolean = false,
        fetchWindow: Pair<Long, Long> = Pair(0, 0),
    ): Result<RemoteAnimeEpisodeUpdate> {
        return try {
            val episodes = episodeRepository.getEpisodeByAnimeId(anime.id)
                .sortedBy { it.sourceOrder }
            val update = withIOContext {
                source.getAnimeEpisodeUpdate(
                    anime = anime.toSAnime(),
                    episodes = episodes.map(Episode::toSEpisode),
                    fetchDetails = fetchDetails,
                    fetchEpisodes = fetchEpisodes,
                )
            }
            awaitUpdateFromSource(anime, update.anime, manualFetch)

            // AM -->
            val sourceEpisodes = update.episodes
                .distinctBy { it.url }
                .mapIndexed { i, sEpisode ->
                    val ep = Episode.create()
                        .copyFromSEpisode(sEpisode)
                        .copy(name = with(EpisodeSanitizer) { sEpisode.name.sanitize(anime.title) })
                        .copy(animeId = anime.id, sourceOrder = i.toLong())

                    val localEpisode = episodes.find { it.url == ep.url }

                    val thumbnailLastModified =
                        when {
                            // Never refresh thumbnails if the url is empty to avoid "losing" existing thumbnails
                            ep.previewUrl.isNullOrEmpty() -> null
                            !manualFetch && localEpisode?.previewUrl == ep.previewUrl -> null
                            anime.isLocal() -> Clock.System.now().toEpochMilliseconds()
                            else -> {
                                localEpisode?.previewUrl?.let {
                                    thumbnailCache.deleteFromCache(it)
                                }
                                Clock.System.now().toEpochMilliseconds()
                            }
                        }

                    ep.copy(
                        thumbnailLastModifiedAt = thumbnailLastModified ?: ep.thumbnailLastModifiedAt,
                    )
                }
            // <-- AM

            val newEpisodes = syncEpisodesWithSource.await(
                sourceEpisodes = sourceEpisodes,
                anime = anime,
                source = source,
                manualFetch = manualFetch,
                fetchWindow = fetchWindow,
            )
            val updatedAnime = animeRepository.getAnimeById(anime.id)
            Result.success(RemoteAnimeEpisodeUpdate(anime = updatedAnime, newEpisodes = newEpisodes))
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            Result.failure(e)
        }
    }

    suspend fun awaitSeasonsUpdate(
        anime: Anime,
        fetchDetails: Boolean = false,
        fetchSeasons: Boolean = false,
        manualFetch: Boolean = false,
        fetchWindow: Pair<Long, Long> = Pair(0, 0),
    ): Result<RemoteAnimeSeasonUpdate> {
        val source = sourceManager.getOrStub(anime.source)
        return awaitSeasonsUpdate(
            source = source,
            anime = anime,
            fetchDetails = fetchDetails,
            fetchSeasons = fetchSeasons,
            manualFetch = manualFetch,
            fetchWindow = fetchWindow,
        )
    }

    suspend fun awaitSeasonsUpdate(
        source: AnimeSource,
        anime: Anime,
        fetchDetails: Boolean = false,
        fetchSeasons: Boolean = false,
        manualFetch: Boolean = false,
        fetchWindow: Pair<Long, Long> = Pair(0, 0),
    ): Result<RemoteAnimeSeasonUpdate> {
        return try {
            val seasons = animeRepository.getAnimeSeasonsById(anime.id)
                .sortedBy { it.anime.seasonSourceOrder }
            val update = withIOContext {
                source.getAnimeSeasonUpdate(
                    anime = anime.toSAnime(),
                    seasons = seasons.map { it.anime.toSAnime() },
                    fetchDetails = fetchDetails,
                    fetchSeasons = fetchSeasons,
                )
            }
            awaitUpdateFromSource(anime, update.anime, manualFetch)
            val newSeasons = syncSeasonsWithSource.await(
                rawSourceSeasons = update.seasons,
                anime = anime,
                source = source,
                manualFetch = manualFetch,
                fetchWindow = fetchWindow,
            )
            val updatedAnime = animeRepository.getAnimeById(anime.id)
            Result.success(RemoteAnimeSeasonUpdate(anime = updatedAnime, newSeasons = newSeasons))
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            Result.failure(e)
        }
    }

    private suspend fun awaitUpdateFromSource(
        localAnime: Anime,
        remoteAnime: SAnime,
        manualFetch: Boolean,
    ): Boolean {
        val remoteTitle = try {
            remoteAnime.title
        } catch (_: UninitializedPropertyAccessException) {
            ""
        }

        // if the anime isn't a favorite, set its title from source and update in db
        val title =
            if (remoteTitle.isNotEmpty() && !localAnime.favorite) {
                remoteTitle
            } else {
                null
            }

        val coverLastModified =
            when {
                // Never refresh covers if the url is empty to avoid "losing" existing covers
                remoteAnime.thumbnail_url.isNullOrEmpty() -> null
                !manualFetch && localAnime.thumbnailUrl == remoteAnime.thumbnail_url -> null
                localAnime.isLocal() -> Clock.System.now().toEpochMilliseconds()
                localAnime.hasCustomCover(coverCache) -> {
                    coverCache.deleteFromCache(localAnime, false)
                    null
                }
                else -> {
                    coverCache.deleteFromCache(localAnime, false)
                    Clock.System.now().toEpochMilliseconds()
                }
            }

        val backgroundLastModified =
            when {
                // Never refresh backgrounds if the url is empty to avoid "losing" existing backgrounds
                remoteAnime.background_url.isNullOrEmpty() -> null
                !manualFetch && localAnime.backgroundUrl == remoteAnime.background_url -> null
                localAnime.isLocal() -> Clock.System.now().toEpochMilliseconds()
                localAnime.hasCustomBackground(backgroundCache) -> {
                    backgroundCache.deleteFromCache(localAnime, false)
                    null
                }
                else -> {
                    backgroundCache.deleteFromCache(localAnime, false)
                    Clock.System.now().toEpochMilliseconds()
                }
            }

        val thumbnailUrl = remoteAnime.thumbnail_url?.takeIf { it.isNotEmpty() }
        val backgroundUrl = remoteAnime.background_url?.takeIf { it.isNotEmpty() }

        return animeRepository.update(
            AnimeUpdate(
                id = localAnime.id,
                title = title,
                coverLastModified = coverLastModified,
                backgroundLastModified = backgroundLastModified,
                author = remoteAnime.author,
                artist = remoteAnime.artist,
                description = remoteAnime.description,
                genre = remoteAnime.getGenres(),
                thumbnailUrl = thumbnailUrl,
                backgroundUrl = backgroundUrl,
                status = remoteAnime.status.toLong(),
                updateStrategy = remoteAnime.update_strategy,
                initialized = true,
                memo = remoteAnime.memo,
            ),
        )
    }
}
