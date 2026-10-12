package eu.kanade.tachiyomi.data.track.shikimori.dto

import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.shikimori.toTrackStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SMUserListResult(
    val data: SMUserListEntries,
)

@Serializable
data class SMUserListEntries(
    val animes: List<SMUserListAnime>,
)

@Serializable
data class SMUserListAnime(
    val id: String,
    val url: String,
    val name: String,
    @SerialName("episodes")
    val totalEpisodes: Long, // the title's total episodes
    val userRate: SMUserRate?,
) {
    fun toTrack(trackerId: Long): Track {
        return Track.create(trackerId).apply {
            title = name
            total_episodes = totalEpisodes
            tracking_url = url
            if (userRate != null) {
                // null if not in user's list, must not throw here because it'd break adding titles
                // throws in the findLibAnime method of ShikimoriApi if null and shouldn't be
                remote_id = userRate.rateId.toLong()
                library_id = userRate.rateId.toLong()
                last_episode_seen = userRate.episodes.toDouble()
                score = userRate.score
                status = toTrackStatus(userRate.status)
            }
        }
    }
}

@Serializable
data class SMUserRate(
    @SerialName("id")
    val rateId: String, // ID of the list entry (NOT the title)
    val episodes: Long, // the user's episode progress
    val status: String,
    val score: Double,
)
