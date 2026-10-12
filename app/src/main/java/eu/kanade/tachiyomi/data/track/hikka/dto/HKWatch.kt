package eu.kanade.tachiyomi.data.track.hikka.dto

import eu.kanade.tachiyomi.data.track.hikka.HikkaApi
import eu.kanade.tachiyomi.data.track.hikka.stringToNumber
import eu.kanade.tachiyomi.data.track.hikka.toTrackStatus
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HKWatch(
    val reference: String,
    val note: String?,
    val updated: Long,
    val created: Long,
    val status: String,
    val episodes: Int,
    val rewatches: Int,
    val score: Int,
    @SerialName("start_date")
    val startDate: Long? = null,
    @SerialName("end_date")
    val endDate: Long? = null,
    val anime: HKAnime? = null,
) {
    fun toTrack(trackerId: Long): TrackSearch {
        return TrackSearch.create(trackerId).apply {
            val animeContent = this@HKWatch.anime
            if (animeContent != null) {
                title = animeContent.titleUa ?: animeContent.titleEn ?: animeContent.titleJa ?: ""
                remote_id = stringToNumber(animeContent.slug)
                library_id = stringToNumber(animeContent.slug)
                total_episodes = animeContent.episodesTotal?.toLong() ?: 0
                tracking_url = "${HikkaApi.BASE_URL}/anime/${animeContent.slug}"
            }

            last_episode_seen = this@HKWatch.episodes.toDouble()
            score = this@HKWatch.score.toDouble()
            status = toTrackStatus(this@HKWatch.status)

            started_watching_date = startDate?.let { it * 1000 } ?: 0L
            finished_watching_date = endDate?.let { it * 1000 } ?: 0L
        }
    }
}
