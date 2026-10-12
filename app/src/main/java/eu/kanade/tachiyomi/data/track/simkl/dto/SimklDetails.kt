// AY -->
package eu.kanade.tachiyomi.data.track.simkl.dto

import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.data.track.simkl.SimklApi.Companion.POSTERS_URL
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SimklDetails(
    val ids: SimklDetailsIds,
    val title: String?,
    @SerialName("en_title")
    val titleEn: String?,
    @SerialName("total_episodes")
    val totalEpisodes: Long?,
    val poster: String?,
    val status: String?,
    @SerialName("anime_type")
    val animeType: String?,
    val year: Int?,
    val overview: String?,
) {
    fun toTrackSearch(trackerId: Long, fallbackType: String): TrackSearch {
        return TrackSearch.create(trackerId).apply {
            remote_id = ids.simkl
            title = this@SimklDetails.title ?: titleEn!!
            total_episodes = totalEpisodes ?: 1
            cover_url = poster?.let { "$POSTERS_URL${it}_m.webp" } ?: ""
            summary = overview ?: ""
            tracking_url = "/$fallbackType/${ids.simkl}/${ids.slug}"
            publishing_status = this@SimklDetails.status ?: "ended"
            publishing_type = animeType ?: fallbackType
            start_date = year?.toString() ?: ""
        }
    }
}

@Serializable
data class SimklDetailsIds(
    val simkl: Long,
    val slug: String,
)
// <-- AY
