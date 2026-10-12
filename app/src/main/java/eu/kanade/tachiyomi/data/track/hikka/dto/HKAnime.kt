package eu.kanade.tachiyomi.data.track.hikka.dto

import eu.kanade.tachiyomi.data.track.hikka.HikkaApi
import eu.kanade.tachiyomi.data.track.hikka.stringToNumber
import eu.kanade.tachiyomi.data.track.hikka.toTrackStatus
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
data class HKAnime(
    @SerialName("data_type")
    val dataType: String,
    @SerialName("title_ja")
    val titleJa: String? = null,
    @SerialName("media_type")
    val mediaType: String? = null,
    @SerialName("title_ua")
    val titleUa: String? = null,
    @SerialName("title_en")
    val titleEn: String? = null,
    @SerialName("episodes_total")
    val episodesTotal: Int? = null,
    @SerialName("translated_ua")
    val translatedUa: Boolean,
    val status: String? = null,
    val image: String? = null,
    val year: Int? = null,
    @SerialName("scored_by")
    val scoredBy: Int,
    val score: Double,
    val slug: String,
    val studios: List<HKStudio>,
    @SerialName("start_date")
    val startDate: Long? = null,
    val watch: List<HKWatch>? = emptyList(),
) {
    fun toTrack(trackerId: Long): TrackSearch {
        return TrackSearch.create(trackerId).apply {
            remote_id = stringToNumber(this@HKAnime.slug)
            title = this@HKAnime.titleUa ?: this@HKAnime.titleEn ?: this@HKAnime.titleJa ?: ""
            total_episodes = this@HKAnime.episodesTotal?.toLong() ?: 0
            cover_url = this@HKAnime.image ?: ""
            score = this@HKAnime.score
            tracking_url = "${HikkaApi.BASE_URL}/anime/${this@HKAnime.slug}"
            publishing_status = this@HKAnime.status ?: "finished"
            publishing_type = this@HKAnime.mediaType?.replace("_", " ").orEmpty()
            authors = studios.map { it.name }

            startDate?.takeIf { it != 0L }?.let {
                val outputDf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                start_date = try {
                    outputDf.format(it * 1000)
                } catch (_: Exception) {
                    ""
                }
            }

            val userProgress = watch?.firstOrNull()
            if (userProgress != null) {
                status = toTrackStatus(userProgress.status)
                last_episode_seen = userProgress.episodes.toDouble()
                score = userProgress.score.toDouble()
                started_watching_date = (userProgress.startDate ?: 0L) * 1000
                finished_watching_date = (userProgress.endDate ?: 0L) * 1000
            }
        }
    }
}
