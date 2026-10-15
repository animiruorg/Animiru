package eu.kanade.tachiyomi.data.track.kitsu

import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuUser
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import mihon.graphql.kitsu.KitsuFindLibAnimeQuery
import mihon.graphql.kitsu.KitsuGetAnimeDetailsByIdQuery
import mihon.graphql.kitsu.KitsuGetAnimeDetailsBySlugQuery
import mihon.graphql.kitsu.KitsuGetCurrentAccountQuery
import mihon.graphql.kitsu.KitsuSearchAnimeByTitleQuery
import mihon.graphql.kitsu.fragment.AnimeFragment
import mihon.graphql.kitsu.type.LibraryEntryStatusEnum
import mihon.graphql.kitsu.type.MediaProductionRoleEnum
import mihon.graphql.kitsu.type.ReleaseStatusEnum

internal fun Track.toKitsuStatus() = when (this.status) {
    Kitsu.WATCHING -> LibraryEntryStatusEnum.CURRENT
    Kitsu.PLAN_TO_WATCH -> LibraryEntryStatusEnum.PLANNED
    Kitsu.COMPLETED -> LibraryEntryStatusEnum.COMPLETED
    Kitsu.ON_HOLD -> LibraryEntryStatusEnum.ON_HOLD
    Kitsu.DROPPED -> LibraryEntryStatusEnum.DROPPED
    else -> throw Exception("Unknown status: ${this.status}")
}

internal fun LibraryEntryStatusEnum.toLocalStatus(): Long = when (this) {
    LibraryEntryStatusEnum.CURRENT -> Kitsu.WATCHING
    LibraryEntryStatusEnum.PLANNED -> Kitsu.PLAN_TO_WATCH
    LibraryEntryStatusEnum.COMPLETED -> Kitsu.COMPLETED
    LibraryEntryStatusEnum.ON_HOLD -> Kitsu.ON_HOLD
    LibraryEntryStatusEnum.DROPPED -> Kitsu.DROPPED
    else -> throw Exception("Unknown status: $this")
}

internal fun KitsuGetCurrentAccountQuery.CurrentAccount.toKitsuUser(): KitsuUser {
    return KitsuUser(
        id = id,
        name = profile.name,
        ratingSystem = ratingSystem.rawValue,
    )
}

internal fun KitsuGetAnimeDetailsByIdQuery.FindAnimeById.toTrackSearch(trackId: Long): TrackSearch {
    return animeFragment.toTrackSearch(trackId)
}

internal fun KitsuGetAnimeDetailsBySlugQuery.FindAnimeBySlug.toTrackSearch(trackId: Long): TrackSearch {
    return animeFragment.toTrackSearch(trackId)
}

internal fun KitsuSearchAnimeByTitleQuery.Node.toTrackSearch(trackId: Long): TrackSearch {
    return animeFragment.toTrackSearch(trackId)
}

internal fun KitsuFindLibAnimeQuery.FindAnimeById.toTrackSearch(trackId: Long): TrackSearch? {
    if (myLibraryEntry == null) return null

    return animeFragment.toTrackSearch(trackId).apply {
        library_id = myLibraryEntry.id.toLong()
        started_watching_date = myLibraryEntry.startedAt?.toEpochMilliseconds() ?: 0
        finished_watching_date = myLibraryEntry.finishedAt?.toEpochMilliseconds() ?: 0
        status = myLibraryEntry.status.toLocalStatus()
        score = myLibraryEntry.rating?.toDouble() ?: 0.0
        last_episode_seen = myLibraryEntry.progress.toDouble()
        private = myLibraryEntry.private
    }
}

private fun AnimeFragment.toTrackSearch(trackId: Long): TrackSearch {
    val kitsuAnime = this
    return TrackSearch.create(trackId).apply {
        remote_id = kitsuAnime.id.toLong()
        title = kitsuAnime.titles.preferred
        total_episodes = kitsuAnime.episodeCount?.toLong() ?: 0
        cover_url = kitsuAnime.posterImage?.views?.firstOrNull()?.url ?: kitsuAnime.posterImage?.original?.url.orEmpty()
        summary = (kitsuAnime.description["en"] as? String).orEmpty()
        tracking_url = "https://kitsu.app/anime/${kitsuAnime.slug}"
        score = kitsuAnime.averageRating ?: -1.0
        publishing_status = when (kitsuAnime.status) {
            ReleaseStatusEnum.TBA -> ReleaseStatusEnum.TBA.rawValue
            else -> kitsuAnime.status.rawValue.lowercase().replaceFirstChar { it.uppercase() }
        }
        // AM -->
        publishing_type = kitsuAnime.subtype.rawValue.lowercase().replaceFirstChar { it.uppercase() }
        // <-- AM
        start_date = kitsuAnime.startDate.orEmpty()
        // AM -->
        authors = productions.nodes
            ?.filterNotNull()
            ?.filter { it.role == MediaProductionRoleEnum.STUDIO }
            ?.map { it.company.name }
            ?: emptyList()
        // <-- AM
    }
}
