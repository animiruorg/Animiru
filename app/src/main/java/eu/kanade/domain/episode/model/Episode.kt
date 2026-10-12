package eu.kanade.domain.episode.model

import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.data.database.models.EpisodeImpl
import tachiyomi.domain.episode.model.Episode
import eu.kanade.tachiyomi.data.database.models.Episode as DbEpisode

// TODO: Remove when all deps are migrated
fun Episode.toSEpisode(): SEpisode {
    return SEpisode.create().also {
        it.url = url
        it.name = name
        it.date_upload = dateUpload
        it.episode_number = episodeNumber.toFloat()
        // AY -->
        it.fillermark = fillermark
        // <-- AY
        it.scanlator = scanlator
        // AY -->
        it.summary = summary
        it.preview_url = previewUrl
        // <-- AY
        it.memo = memo
    }
}

fun Episode.copyFromSEpisode(sEpisode: SEpisode): Episode {
    return this.copy(
        name = sEpisode.name,
        url = sEpisode.url,
        dateUpload = sEpisode.date_upload,
        episodeNumber = sEpisode.episode_number.toDouble(),
        // AY -->
        fillermark = sEpisode.fillermark,
        // <-- AY
        scanlator = sEpisode.scanlator?.ifBlank { null }?.trim(),
        // AY -->
        summary = sEpisode.summary?.ifBlank { null },
        previewUrl = sEpisode.preview_url?.ifBlank { null },
        // <-- AY
        memo = sEpisode.memo,
    )
}

fun Episode.toDbEpisode(): DbEpisode = EpisodeImpl().also {
    it.id = id
    it.anime_id = animeId
    it.url = url
    it.name = name
    it.scanlator = scanlator
    // AY -->
    it.summary = summary
    it.preview_url = previewUrl
    // <-- AY
    it.seen = seen
    it.bookmark = bookmark
    // AY -->
    it.fillermark = fillermark
    // <-- AY
    it.last_second_seen = lastSecondSeen
    it.date_fetch = dateFetch
    it.date_upload = dateUpload
    it.episode_number = episodeNumber.toFloat()
    it.source_order = sourceOrder.toInt()
    it.memo = memo
}
