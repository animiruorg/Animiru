package eu.kanade.tachiyomi.data.track.shikimori

import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import mihon.graphql.shikimori.ShikimoriGetAnimeDetailsQuery
import mihon.graphql.shikimori.ShikimoriGetLibAnimeQuery
import mihon.graphql.shikimori.ShikimoriSearchAnimeQuery
import mihon.graphql.shikimori.fragment.AnimeFragment
import mihon.graphql.shikimori.type.AnimeKindEnum
import mihon.graphql.shikimori.type.AnimeStatusEnum
import mihon.graphql.shikimori.type.UserRateStatusEnum

internal fun ShikimoriGetLibAnimeQuery.Anime.toTrack(trackId: Long): Track {
    val sAnime = this
    return Track.create(trackId).apply {
        remote_id = sAnime.id.toLong()
        title = sAnime.name
        total_episodes = sAnime.episodes.toLong()
        tracking_url = sAnime.url

        if (sAnime.userRate != null) {
            // null if not in user's list, must not throw here because it'd break adding titles
            // throws in the findLibAnime method of ShikimoriApi if null and shouldn't be
            library_id = sAnime.userRate.id.toLong()
            last_episode_seen = sAnime.userRate.episodes.toDouble()
            score = sAnime.userRate.score.toDouble()
            status = sAnime.userRate.status.toLocalStatus()
        }
    }
}

internal fun ShikimoriGetAnimeDetailsQuery.Anime.toTrackSearch(trackId: Long): TrackSearch {
    return animeFragment.toTrackSearch(trackId)
}

internal fun ShikimoriSearchAnimeQuery.Anime.toTrackSearch(trackId: Long): TrackSearch {
    return animeFragment.toTrackSearch(trackId)
}

private fun AnimeFragment.toTrackSearch(trackId: Long): TrackSearch {
    val sAnime = this
    return TrackSearch.create(trackId).apply {
        remote_id = sAnime.id.toLong()
        title = sAnime.name
        total_episodes = sAnime.episodes.toLong()
        cover_url = sAnime.poster?.mainUrl.orEmpty()
        summary = sAnime.description.orEmpty()
        score = sAnime.score?.takeIf { it > 0.0 } ?: -1.0
        tracking_url = sAnime.url
        publishing_status = sAnime.status.toLocalString()
        publishing_type = sAnime.kind.toLocalString()
        start_date = sAnime.airedOn?.date.orEmpty()
        // AM -->
        authors = sAnime.studios.map { it.name }
        // <-- AM
    }
}

private fun AnimeKindEnum?.toLocalString() = when (this) {
    AnimeKindEnum.tv -> "TV"
    AnimeKindEnum.movie -> "Movie"
    AnimeKindEnum.ova -> "OVA"
    AnimeKindEnum.ona -> "ONA"
    AnimeKindEnum.special -> "Special"
    AnimeKindEnum.tv_special -> "TV Special"
    AnimeKindEnum.music -> "Music"
    AnimeKindEnum.pv -> "PV"
    AnimeKindEnum.cm -> "CM"
    AnimeKindEnum.UNKNOWN__ -> ""
    null -> ""
}

private fun AnimeStatusEnum?.toLocalString() = when (this) {
    AnimeStatusEnum.anons -> "Planned"
    AnimeStatusEnum.ongoing -> "Airing"
    AnimeStatusEnum.released -> "Released"
    AnimeStatusEnum.UNKNOWN__ -> ""
    null -> ""
}

private fun UserRateStatusEnum.toLocalStatus() = when (this) {
    UserRateStatusEnum.planned -> Shikimori.PLAN_TO_WATCH
    UserRateStatusEnum.watching -> Shikimori.WATCHING
    UserRateStatusEnum.rewatching -> Shikimori.REWATCHING
    UserRateStatusEnum.completed -> Shikimori.COMPLETED
    UserRateStatusEnum.on_hold -> Shikimori.ON_HOLD
    UserRateStatusEnum.dropped -> Shikimori.DROPPED
    else -> throw NotImplementedError("Unknown status: $this")
}

internal fun Track.toShikimoriStatus() = when (status) {
    // rawValues because Shikimori doesn't have GraphQL mutations and this goes through the v2 API
    // if/when Shikimori adds GraphQL mutations, this should return UserRateStatusEnum members
    Shikimori.WATCHING -> UserRateStatusEnum.watching.rawValue
    Shikimori.COMPLETED -> UserRateStatusEnum.completed.rawValue
    Shikimori.ON_HOLD -> UserRateStatusEnum.on_hold.rawValue
    Shikimori.DROPPED -> UserRateStatusEnum.dropped.rawValue
    Shikimori.PLAN_TO_WATCH -> UserRateStatusEnum.planned.rawValue
    Shikimori.REWATCHING -> UserRateStatusEnum.rewatching.rawValue
    else -> throw NotImplementedError("Unknown status: $status")
}
