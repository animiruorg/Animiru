package eu.kanade.domain.episode.model

import eu.kanade.tachiyomi.animesource.model.SEpisode
import mihon.core.common.extensions.toEpochMillisOrZero
import mihon.core.common.extensions.toInstantOrNull
import tachiyomi.domain.episode.model.Episode

fun Episode.toSEpisode(): SEpisode {
    return SEpisode.create().also {
        it.url = url
        it.name = name
        it.date_upload = dateUpload.toEpochMillisOrZero()
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
        dateUpload = sEpisode.date_upload.toInstantOrNull(),
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
