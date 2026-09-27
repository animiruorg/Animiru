@file:Suppress("PropertyName")

package eu.kanade.tachiyomi.data.database.models

import eu.kanade.tachiyomi.animesource.model.SEpisode
import java.io.Serializable
import tachiyomi.domain.episode.model.Episode as DomainEpisode

interface Episode : SEpisode, Serializable {

    var id: Long?

    var anime_id: Long?

    var seen: Boolean

    var bookmark: Boolean

    var last_second_seen: Long

    // AY -->
    var total_seconds: Long
    // <-- AY

    var date_fetch: Long

    var source_order: Int

    var last_modified: Long

    var version: Long
}

val Episode.isRecognizedNumber: Boolean
    get() = episode_number >= 0f

fun Episode.toDomainEpisode(): DomainEpisode? {
    if (id == null || anime_id == null) return null
    return DomainEpisode(
        id = id!!,
        animeId = anime_id!!,
        seen = seen,
        bookmark = bookmark,
        // AY -->
        fillermark = fillermark,
        // <-- AY
        lastSecondSeen = last_second_seen,
        // AY -->
        totalSeconds = total_seconds,
        // <-- AY
        dateFetch = date_fetch,
        sourceOrder = source_order.toLong(),
        url = url,
        name = name,
        dateUpload = date_upload,
        episodeNumber = episode_number.toDouble(),
        scanlator = scanlator,
        // AY -->
        summary = summary,
        previewUrl = preview_url,
        // <-- AY
        lastModifiedAt = last_modified,
        version = version,
        memo = memo,
        // AM -->
        thumbnailLastModifiedAt = 0,
        // <-- AM
    )
}
