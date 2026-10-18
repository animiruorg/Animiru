package tachiyomi.data.anime

import aniyomi.domain.anime.model.SeasonAnime
import eu.kanade.tachiyomi.animesource.model.AnimeUpdateStrategy
import eu.kanade.tachiyomi.animesource.model.FetchType
import kotlinx.serialization.json.JsonObject
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.model.AnimeWithEpisodeCount
import tachiyomi.domain.library.model.LibraryAnime
import tachiyomi.domain.source.model.DeletableAnime

object AnimeMapper {
    fun mapAnime(
        id: Long,
        sourceId: Long,
        remoteUrl: String,
        remoteTitle: String,
        remoteAuthor: String?,
        remoteArtist: String?,
        remoteDescription: String?,
        remoteGenre: List<String>?,
        remoteStatus: Long,
        remoteCover: String?,
        remoteUpdateStrategy: AnimeUpdateStrategy,
        remoteMemo: JsonObject,
        // AM -->
        remoteFetchType: FetchType,
        remoteSeasonNumber: Double,
        remoteSeasonSourceOrder: Long,
        remoteBackground: String?,
        // <-- AM
        userFavoriteAt: Long?,
        userNotes: String,
        userPlayerFlags: Long,
        userEpisodeFlags: Long,
        // AM -->
        userSeasonFlags: Long,
        userCustomArtist: String?,
        userCustomAuthor: String?,
        userCustomDescription: String?,
        userCustomGenre: List<String>?,
        userCustomTitle: String?,
        userCustomStatus: Long?,
        // <-- AM
        stateEpisodeLastUpdate: Long?,
        stateEpisodeNextUpdate: Long?,
        stateEpisodeFetchInterval: Long,
        stateCoverLastModified: Long,
        stateInitialized: Boolean,
        // AM -->
        stateParentId: Long?,
        stateBackgroundLastModified: Long,
        // <-- AM
    ): Anime = Anime(
        id = id,
        source = sourceId,
        lastUpdate = stateEpisodeLastUpdate ?: 0,
        nextUpdate = stateEpisodeNextUpdate ?: 0,
        fetchInterval = stateEpisodeFetchInterval.toInt(),
        favoriteAt = userFavoriteAt,
        viewerFlags = userPlayerFlags,
        episodeFlags = userEpisodeFlags,
        coverLastModified = stateCoverLastModified,
        // AY -->
        backgroundLastModified = stateBackgroundLastModified,
        // <-- AY
        url = remoteUrl,
        // AM (CUSTOM_INFORMATION) -->
        ogTitle = remoteTitle,
        ogArtist = remoteArtist,
        ogAuthor = remoteAuthor,
        ogDescription = remoteDescription,
        ogGenre = remoteGenre,
        ogStatus = remoteStatus,
        // <-- AM (CUSTOM_INFORMATION)
        thumbnailUrl = remoteCover,
        // AY -->
        backgroundUrl = remoteBackground,
        // <-- AY
        updateStrategy = remoteUpdateStrategy,
        initialized = stateInitialized,
        notes = userNotes,
        // AY -->
        fetchType = remoteFetchType,
        parentId = stateParentId,
        seasonFlags = userSeasonFlags,
        seasonNumber = remoteSeasonNumber,
        seasonSourceOrder = remoteSeasonSourceOrder,
        // <-- AY
        memo = remoteMemo,
        customArtist = userCustomArtist,
        customAuthor = userCustomAuthor,
        customDescription = userCustomDescription,
        customGenre = userCustomGenre,
        customTitle = userCustomTitle,
        customStatus = userCustomStatus,
    )

    fun mapLibraryAnime(
        id: Long,
        sourceId: Long,
        remoteUrl: String,
        remoteTitle: String,
        remoteAuthor: String?,
        remoteArtist: String?,
        remoteDescription: String?,
        remoteGenre: List<String>?,
        remoteStatus: Long,
        remoteCover: String?,
        remoteUpdateStrategy: AnimeUpdateStrategy,
        remoteMemo: JsonObject,
        // AM -->
        remoteFetchType: FetchType,
        remoteSeasonNumber: Double,
        remoteSeasonSourceOrder: Long,
        remoteBackground: String?,
        // <-- AM
        userFavoriteAt: Long?,
        userNotes: String,
        userPlayerFlags: Long,
        userEpisodeFlags: Long,
        // AM -->
        userSeasonFlags: Long,
        userCustomArtist: String?,
        userCustomAuthor: String?,
        userCustomDescription: String?,
        userCustomGenre: List<String>?,
        userCustomTitle: String?,
        userCustomStatus: Long?,
        // <-- AM
        stateEpisodeLastUpdate: Long?,
        stateEpisodeNextUpdate: Long?,
        stateEpisodeFetchInterval: Long,
        stateCoverLastModified: Long,
        stateInitialized: Boolean,
        // AM -->
        stateParentId: Long?,
        stateBackgroundLastModified: Long,
        // <-- AM
        totalCount: Long,
        seenCount: Double,
        latestUpload: Long,
        episodeFetchedAt: Long,
        lastSeen: Long,
        bookmarkCount: Double,
        // AY -->
        fillermarkCount: Double,
        // <-- AY
        categories: String,
    ): LibraryAnime = LibraryAnime(
        anime = mapAnime(
            id = id,
            sourceId = sourceId,
            remoteUrl = remoteUrl,
            remoteTitle = remoteTitle,
            remoteAuthor = remoteAuthor,
            remoteArtist = remoteArtist,
            remoteDescription = remoteDescription,
            remoteGenre = remoteGenre,
            remoteStatus = remoteStatus,
            remoteCover = remoteCover,
            remoteUpdateStrategy = remoteUpdateStrategy,
            remoteMemo = remoteMemo,
            // AM -->
            remoteFetchType = remoteFetchType,
            remoteSeasonNumber = remoteSeasonNumber,
            remoteSeasonSourceOrder = remoteSeasonSourceOrder,
            remoteBackground = remoteBackground,
            // <-- AM
            userFavoriteAt = userFavoriteAt,
            userNotes = userNotes,
            userPlayerFlags = userPlayerFlags,
            userEpisodeFlags = userEpisodeFlags,
            // AM -->
            userSeasonFlags = userSeasonFlags,
            userCustomArtist = userCustomArtist,
            userCustomAuthor = userCustomAuthor,
            userCustomDescription = userCustomDescription,
            userCustomGenre = userCustomGenre,
            userCustomTitle = userCustomTitle,
            userCustomStatus = userCustomStatus,
            // <-- AM
            stateEpisodeLastUpdate = stateEpisodeLastUpdate,
            stateEpisodeNextUpdate = stateEpisodeNextUpdate,
            stateEpisodeFetchInterval = stateEpisodeFetchInterval,
            stateCoverLastModified = stateCoverLastModified,
            stateInitialized = stateInitialized,
            // AM -->
            stateParentId = stateParentId,
            stateBackgroundLastModified = stateBackgroundLastModified,
            // <-- AM
        ),
        categories = categories.split(",").map { it.toLong() },
        totalCount = totalCount,
        seenCount = seenCount.toLong(),
        bookmarkCount = bookmarkCount.toLong(),
        // AY -->
        fillermarkCount = fillermarkCount.toLong(),
        // <-- AY
        latestUpload = latestUpload,
        episodeFetchedAt = episodeFetchedAt,
        lastSeen = lastSeen,
    )

    fun mapAnimeWithEpisodeCount(
        id: Long,
        sourceId: Long,
        remoteUrl: String,
        remoteTitle: String,
        remoteAuthor: String?,
        remoteArtist: String?,
        remoteDescription: String?,
        remoteGenre: List<String>?,
        remoteStatus: Long,
        remoteCover: String?,
        remoteUpdateStrategy: AnimeUpdateStrategy,
        remoteMemo: JsonObject,
        // AM -->
        remoteFetchType: FetchType,
        remoteSeasonNumber: Double,
        remoteSeasonSourceOrder: Long,
        remoteBackground: String?,
        // <-- AM
        userFavoriteAt: Long?,
        userNotes: String,
        userPlayerFlags: Long,
        userEpisodeFlags: Long,
        // AM -->
        userSeasonFlags: Long,
        userCustomArtist: String?,
        userCustomAuthor: String?,
        userCustomDescription: String?,
        userCustomGenre: List<String>?,
        userCustomTitle: String?,
        userCustomStatus: Long?,
        // <-- AM
        stateEpisodeLastUpdate: Long?,
        stateEpisodeNextUpdate: Long?,
        stateEpisodeFetchInterval: Long,
        stateCoverLastModified: Long,
        stateInitialized: Boolean,
        // AM -->
        stateParentId: Long?,
        stateBackgroundLastModified: Long,
        // <-- AM

        // <-- AM
        totalCount: Long,
    ): AnimeWithEpisodeCount = AnimeWithEpisodeCount(
        anime = mapAnime(
            id = id,
            sourceId = sourceId,
            remoteUrl = remoteUrl,
            remoteTitle = remoteTitle,
            remoteAuthor = remoteAuthor,
            remoteArtist = remoteArtist,
            remoteDescription = remoteDescription,
            remoteGenre = remoteGenre,
            remoteStatus = remoteStatus,
            remoteCover = remoteCover,
            remoteUpdateStrategy = remoteUpdateStrategy,
            remoteMemo = remoteMemo,
            // AM -->
            remoteFetchType = remoteFetchType,
            remoteSeasonNumber = remoteSeasonNumber,
            remoteSeasonSourceOrder = remoteSeasonSourceOrder,
            remoteBackground = remoteBackground,
            // <-- AM
            userFavoriteAt = userFavoriteAt,
            userNotes = userNotes,
            userPlayerFlags = userPlayerFlags,
            userEpisodeFlags = userEpisodeFlags,
            // AM -->
            userSeasonFlags = userSeasonFlags,
            userCustomArtist = userCustomArtist,
            userCustomAuthor = userCustomAuthor,
            userCustomDescription = userCustomDescription,
            userCustomGenre = userCustomGenre,
            userCustomTitle = userCustomTitle,
            userCustomStatus = userCustomStatus,
            // <-- AM
            stateEpisodeLastUpdate = stateEpisodeLastUpdate,
            stateEpisodeNextUpdate = stateEpisodeNextUpdate,
            stateEpisodeFetchInterval = stateEpisodeFetchInterval,
            stateCoverLastModified = stateCoverLastModified,
            stateInitialized = stateInitialized,
            // AM -->
            stateParentId = stateParentId,
            stateBackgroundLastModified = stateBackgroundLastModified,
            // <-- AM
        ),
        episodeCount = totalCount,
    )

    // AY -->
    fun mapSeasonAnime(
        id: Long,
        sourceId: Long,
        remoteUrl: String,
        remoteTitle: String,
        remoteAuthor: String?,
        remoteArtist: String?,
        remoteDescription: String?,
        remoteGenre: List<String>?,
        remoteStatus: Long,
        remoteCover: String?,
        remoteUpdateStrategy: AnimeUpdateStrategy,
        remoteMemo: JsonObject,
        // AM -->
        remoteFetchType: FetchType,
        remoteSeasonNumber: Double,
        remoteSeasonSourceOrder: Long,
        remoteBackground: String?,
        // <-- AM
        userFavoriteAt: Long?,
        userNotes: String,
        userPlayerFlags: Long,
        userEpisodeFlags: Long,
        // AM -->
        userSeasonFlags: Long,
        userCustomArtist: String?,
        userCustomAuthor: String?,
        userCustomDescription: String?,
        userCustomGenre: List<String>?,
        userCustomTitle: String?,
        userCustomStatus: Long?,
        // <-- AM
        stateEpisodeLastUpdate: Long?,
        stateEpisodeNextUpdate: Long?,
        stateEpisodeFetchInterval: Long,
        stateCoverLastModified: Long,
        stateInitialized: Boolean,
        // AM -->
        stateParentId: Long?,
        stateBackgroundLastModified: Long,
        // <-- AM
        totalCount: Long,
        seenCount: Double,
        latestUpload: Long,
        fetchedAt: Long,
        lastSeen: Long,
        bookmarkCount: Double,
        fillermarkCount: Double,
    ): SeasonAnime = SeasonAnime(
        anime = mapAnime(
            id = id,
            sourceId = sourceId,
            remoteUrl = remoteUrl,
            remoteTitle = remoteTitle,
            remoteAuthor = remoteAuthor,
            remoteArtist = remoteArtist,
            remoteDescription = remoteDescription,
            remoteGenre = remoteGenre,
            remoteStatus = remoteStatus,
            remoteCover = remoteCover,
            remoteUpdateStrategy = remoteUpdateStrategy,
            remoteMemo = remoteMemo,
            // AM -->
            remoteFetchType = remoteFetchType,
            remoteSeasonNumber = remoteSeasonNumber,
            remoteSeasonSourceOrder = remoteSeasonSourceOrder,
            remoteBackground = remoteBackground,
            // <-- AM
            userFavoriteAt = userFavoriteAt,
            userNotes = userNotes,
            userPlayerFlags = userPlayerFlags,
            userEpisodeFlags = userEpisodeFlags,
            // AM -->
            userSeasonFlags = userSeasonFlags,
            userCustomArtist = userCustomArtist,
            userCustomAuthor = userCustomAuthor,
            userCustomDescription = userCustomDescription,
            userCustomGenre = userCustomGenre,
            userCustomTitle = userCustomTitle,
            userCustomStatus = userCustomStatus,
            // <-- AM
            stateEpisodeLastUpdate = stateEpisodeLastUpdate,
            stateEpisodeNextUpdate = stateEpisodeNextUpdate,
            stateEpisodeFetchInterval = stateEpisodeFetchInterval,
            stateCoverLastModified = stateCoverLastModified,
            stateInitialized = stateInitialized,
            // AM -->
            stateParentId = stateParentId,
            stateBackgroundLastModified = stateBackgroundLastModified,
            // <-- AM
        ),
        totalCount = totalCount,
        seenCount = seenCount.toLong(),
        bookmarkCount = bookmarkCount.toLong(),
        fillermarkCount = fillermarkCount.toLong(),
        latestUpload = latestUpload,
        fetchedAt = fetchedAt,
        lastSeen = lastSeen,
    )

    fun mapRelatedAnime(
        id: Long,
        sourceId: Long,
        remoteUrl: String,
        remoteTitle: String,
        remoteAuthor: String?,
        remoteArtist: String?,
        remoteDescription: String?,
        remoteGenre: List<String>?,
        remoteStatus: Long,
        remoteCover: String?,
        remoteUpdateStrategy: AnimeUpdateStrategy,
        remoteMemo: JsonObject,
        // AM -->
        remoteFetchType: FetchType,
        remoteSeasonNumber: Double,
        remoteSeasonSourceOrder: Long,
        remoteBackground: String?,
        // <-- AM
        userFavoriteAt: Long?,
        userNotes: String,
        userPlayerFlags: Long,
        userEpisodeFlags: Long,
        // AM -->
        userSeasonFlags: Long,
        userCustomArtist: String?,
        userCustomAuthor: String?,
        userCustomDescription: String?,
        userCustomGenre: List<String>?,
        userCustomTitle: String?,
        userCustomStatus: Long?,
        // <-- AM
        stateEpisodeLastUpdate: Long?,
        stateEpisodeNextUpdate: Long?,
        stateEpisodeFetchInterval: Long,
        stateCoverLastModified: Long,
        stateInitialized: Boolean,
        // AM -->
        stateParentId: Long?,
        stateBackgroundLastModified: Long,
        // <-- AM
        name: String,
    ): Pair<String, Anime> = name to mapAnime(
        id = id,
        sourceId = sourceId,
        remoteUrl = remoteUrl,
        remoteTitle = remoteTitle,
        remoteAuthor = remoteAuthor,
        remoteArtist = remoteArtist,
        remoteDescription = remoteDescription,
        remoteGenre = remoteGenre,
        remoteStatus = remoteStatus,
        remoteCover = remoteCover,
        remoteUpdateStrategy = remoteUpdateStrategy,
        remoteMemo = remoteMemo,
        // AM -->
        remoteFetchType = remoteFetchType,
        remoteSeasonNumber = remoteSeasonNumber,
        remoteSeasonSourceOrder = remoteSeasonSourceOrder,
        remoteBackground = remoteBackground,
        // <-- AM
        userFavoriteAt = userFavoriteAt,
        userNotes = userNotes,
        userPlayerFlags = userPlayerFlags,
        userEpisodeFlags = userEpisodeFlags,
        // AM -->
        userSeasonFlags = userSeasonFlags,
        userCustomArtist = userCustomArtist,
        userCustomAuthor = userCustomAuthor,
        userCustomDescription = userCustomDescription,
        userCustomGenre = userCustomGenre,
        userCustomTitle = userCustomTitle,
        userCustomStatus = userCustomStatus,
        // <-- AM
        stateEpisodeLastUpdate = stateEpisodeLastUpdate,
        stateEpisodeNextUpdate = stateEpisodeNextUpdate,
        stateEpisodeFetchInterval = stateEpisodeFetchInterval,
        stateCoverLastModified = stateCoverLastModified,
        stateInitialized = stateInitialized,
        // AM -->
        stateParentId = stateParentId,
        stateBackgroundLastModified = stateBackgroundLastModified,
        // <-- AM
    )

    fun mapDeletableAnime(
        id: Long,
        source: Long,
        fetchType: FetchType,
    ): DeletableAnime = DeletableAnime(
        animeId = id,
        sourceId = source,
        fetchType = fetchType,
    )
    // <-- AY
}
