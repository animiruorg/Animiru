package eu.kanade.tachiyomi.data.track.kitsu

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.DeletableTracker
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuOAuth
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import java.text.DecimalFormat
import tachiyomi.domain.track.model.Track as DomainTrack

class Kitsu(id: Long) : BaseTracker(id, "Kitsu"), DeletableTracker {

    private data class RatingSystem(
        val name: String,
        val scoreList: List<String>,
        val twentyScale: List<Int>,
    )

    companion object {
        const val WATCHING = 1L
        const val COMPLETED = 2L
        const val ON_HOLD = 3L
        const val DROPPED = 4L
        const val PLAN_TO_WATCH = 5L

        const val RATING_SIMPLE = "simple"
        const val RATING_REGULAR = "regular"
        const val RATING_ADVANCED = "advanced"

        private val ratingSystems = mapOf(
            // Smileys
            RATING_SIMPLE to RatingSystem(
                name = RATING_SIMPLE,
                scoreList = listOf("-", "😡", "😐", "😊", "😀"),
                twentyScale = (2..20 step 6).toList(), // 2, 8, 14, 20
            ),
            // DecimalFormatter is not thread safe, so new formatters for each map instead of extracted val attribute
            // to not incite reuse
            // Stars (0.5-5 step 0.5)
            RATING_REGULAR to RatingSystem(
                name = RATING_REGULAR,
                scoreList = (0..10).map { it / 2.0 }.map(DecimalFormat("0.#")::format).map { "$it ★" },
                twentyScale = (2..20 step 2).toList(), // 2, 4, ..., 18, 20
            ),
            // 10 point decimal (step 0.5, starting at 1) + 0 for our "not rated" placeholder
            RATING_ADVANCED to RatingSystem(
                name = RATING_ADVANCED,
                scoreList = listOf("0") + (2..20).map { it / 2.0 }.map(DecimalFormat("0.#")::format),
                twentyScale = (2..20).toList(), // 2, 3, ..., 19, 20
            ),
        )

        private const val SEARCH_ID_PREFIX = "id:"
    }

    override val supportsReadingDates: Boolean = true

    override val supportsPrivateTracking: Boolean = true

    private val json: Json by lazy { appGraph.json }

    private val interceptor by lazy { KitsuInterceptor(this, json) }

    private val api by lazy { KitsuApi(id, client, json, interceptor) }

    private val scorePreference by lazy { trackPreferences.kitsuScoreType }

    override fun getLogo() = R.drawable.brand_kitsu

    override fun getStatusList(): List<Long> {
        return listOf(WATCHING, COMPLETED, ON_HOLD, DROPPED, PLAN_TO_WATCH)
    }

    override fun getStatus(status: Long): StringResource? = when (status) {
        WATCHING -> AYMR.strings.currently_watching
        PLAN_TO_WATCH -> AYMR.strings.want_to_watch
        COMPLETED -> MR.strings.completed
        ON_HOLD -> MR.strings.on_hold
        DROPPED -> MR.strings.dropped
        else -> null
    }

    override fun getWatchingStatus(): Long = WATCHING

    override fun getRewatchingStatus(): Long = -1

    override fun getCompletionStatus(): Long = COMPLETED

    private fun getCurrentRatingSystem(): RatingSystem {
        val ratingSystem = scorePreference.get().lowercase()
        return ratingSystems[ratingSystem] ?: throw Exception("Unknown score type $ratingSystem")
    }

    override fun getScoreList(): List<String> = getCurrentRatingSystem().scoreList

    override fun get10PointScore(track: DomainTrack): Double {
        // score is stored in Kitsu's native 2-20 scale
        return track.score / 2.0
    }

    override fun indexToScore(index: Int): Double {
        if (index == 0) return 0.0
        return getCurrentRatingSystem().twentyScale[index - 1].toDouble()
    }

    override fun displayScore(track: DomainTrack): String {
        val ratingSystem = getCurrentRatingSystem()
        // Since Kitsu's valid score range is 2-20, unset values of -1.0 or 0.0 will both return -1 from indexOfLast
        // which is turned into index 0 of the scoreList, giving us the "unset" display score (- or 0).
        // Proper scores are "rounded down" to the nearest value of the scale (also what Kitsu's website does)
        return ratingSystem.scoreList[ratingSystem.twentyScale.indexOfLast { it <= track.score } + 1]
    }

    private suspend fun add(track: Track): Track {
        return api.addLibAnime(track)
    }

    override suspend fun update(track: Track, didWatchEpisode: Boolean): Track {
        if (track.status != COMPLETED) {
            if (didWatchEpisode) {
                if (track.last_episode_seen.toLong() == track.total_episodes && track.total_episodes > 0) {
                    track.status = COMPLETED
                    track.finished_watching_date = System.currentTimeMillis()
                } else {
                    track.status = WATCHING
                    if (track.last_episode_seen == 1.0) {
                        track.started_watching_date = System.currentTimeMillis()
                    }
                }
            }
        }

        return api.updateLibAnime(track)
    }

    override suspend fun delete(track: DomainTrack) {
        api.removeLibAnime(track)
    }

    override suspend fun bind(track: Track, hasSeenEpisodes: Boolean): Track {
        val remoteTrack = api.findLibAnime(track)
        return if (remoteTrack != null) {
            track.copyPersonalFrom(remoteTrack, copyRemotePrivate = false)
            track.remote_id = remoteTrack.remote_id
            track.library_id = remoteTrack.library_id

            if (track.status != COMPLETED) {
                track.status = if (hasSeenEpisodes) WATCHING else track.status
            }

            update(track)
        } else {
            track.status = if (hasSeenEpisodes) WATCHING else PLAN_TO_WATCH
            track.score = 0.0
            add(track)
        }
    }

    override suspend fun search(query: String): List<TrackSearch> {
        if (query.startsWith(SEARCH_ID_PREFIX)) {
            query.substringAfter(SEARCH_ID_PREFIX).trim().let { id ->
                return api.getAnimeDetails(id)?.let { listOf(it) } ?: emptyList()
            }
        }

        return api.search(query)
    }

    override suspend fun refresh(track: Track): Track {
        val remoteTrack = api.findLibAnime(track) ?: throw Exception("Could not find anime")
        track.copyPersonalFrom(remoteTrack)
        track.total_episodes = remoteTrack.total_episodes
        return track
    }

    override suspend fun login(username: String, password: String) {
        val token = api.login(username, password)
        interceptor.newAuth(token)
        val currentUser = api.getCurrentUser()

        val ratingSystem = currentUser.ratingSystem
        if (ratingSystem.lowercase() in listOf(RATING_SIMPLE, RATING_REGULAR, RATING_ADVANCED)) {
            scorePreference.set(ratingSystem)
        } else {
            logcat(LogPriority.ERROR) { "Unsupported Kitsu score type: $ratingSystem" }
            scorePreference.set(RATING_ADVANCED)
        }
        saveDisplayUsername(currentUser.profile.name)
        saveCredentials(username, currentUser.id)
    }

    override suspend fun updateUserConfig() {
        val currentUser = api.getCurrentUser()

        val ratingSystem = currentUser.ratingSystem
        if (ratingSystem.lowercase() in listOf(RATING_SIMPLE, RATING_REGULAR, RATING_ADVANCED)) {
            scorePreference.set(ratingSystem.lowercase())
        } else {
            logcat(LogPriority.ERROR) { "Unsupported Kitsu score type: $ratingSystem" }
            scorePreference.set(RATING_ADVANCED)
        }
        saveDisplayUsername(currentUser.profile.name)
    }

    override fun logout() {
        super.logout()
        interceptor.newAuth(null)
    }

    fun saveToken(oauth: KitsuOAuth?) {
        trackPreferences.trackToken(this).set(json.encodeToString(oauth))
    }

    fun restoreToken(): KitsuOAuth? {
        return try {
            json.decodeFromString<KitsuOAuth>(trackPreferences.trackToken(this).get())
        } catch (_: Exception) {
            null
        }
    }
}
