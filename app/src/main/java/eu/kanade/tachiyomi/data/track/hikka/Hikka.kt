package eu.kanade.tachiyomi.data.track.hikka

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.DeletableTracker
import eu.kanade.tachiyomi.data.track.hikka.dto.HKOAuth
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import kotlinx.serialization.json.Json
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.domain.track.model.Track as DomainTrack

class Hikka(id: Long) : BaseTracker(id, "Hikka"), DeletableTracker {

    companion object {
        const val WATCHING = 0L
        const val COMPLETED = 1L
        const val ON_HOLD = 2L
        const val DROPPED = 3L
        const val PLAN_TO_WATCH = 4L
        const val REWATCHING = 5L

        private val SCORE_LIST = IntRange(0, 10)
            .map(Int::toString)

        private const val SEARCH_ID_PREFIX = "id:"
    }

    private val json: Json by lazy { appGraph.json }

    private val interceptor by lazy { HikkaInterceptor(this, json) }

    private val api by lazy { HikkaApi(id, client, json, interceptor) }

    override val supportsReadingDates: Boolean = true

    override fun getLogo(): Int = R.drawable.brand_hikka

    override fun getStatusList(): List<Long> {
        return listOf(
            WATCHING,
            COMPLETED,
            ON_HOLD,
            DROPPED,
            PLAN_TO_WATCH,
            REWATCHING,
        )
    }

    override fun getStatus(status: Long): StringResource? = when (status) {
        WATCHING -> AYMR.strings.watching
        PLAN_TO_WATCH -> AYMR.strings.plan_to_watch
        COMPLETED -> MR.strings.completed
        ON_HOLD -> MR.strings.on_hold
        DROPPED -> MR.strings.dropped
        REWATCHING -> AYMR.strings.repeating_anime
        else -> null
    }

    override fun getWatchingStatus(): Long = WATCHING

    override fun getRewatchingStatus(): Long = REWATCHING

    override fun getCompletionStatus(): Long = COMPLETED

    override fun getScoreList(): List<String> = SCORE_LIST

    override fun displayScore(track: DomainTrack): String {
        return track.score.toInt().toString()
    }

    override suspend fun update(
        track: Track,
        didWatchEpisode: Boolean,
    ): Track {
        if (track.status != COMPLETED) {
            if (didWatchEpisode) {
                if (track.last_episode_seen.toLong() == track.total_episodes && track.total_episodes > 0) {
                    track.status = COMPLETED
                    track.finished_watching_date = System.currentTimeMillis()
                } else if (track.status != REWATCHING) {
                    track.status = WATCHING
                    if (track.last_episode_seen == 1.0) {
                        track.started_watching_date = System.currentTimeMillis()
                    }
                }
            }
        }
        return api.updateUserAnime(track)
    }

    override suspend fun bind(track: Track, hasSeenEpisodes: Boolean): Track {
        val seenContent = api.getSeen(track)
        val remoteTrack = api.getAnime(track)

        track.copyPersonalFrom(remoteTrack)
        track.remote_id = remoteTrack.remote_id
        track.library_id = remoteTrack.library_id

        if (track.status != COMPLETED) {
            val isRewatching = track.status == REWATCHING
            track.status = if (!isRewatching && hasSeenEpisodes) WATCHING else track.status
        }

        return if (seenContent != null) {
            track.score = seenContent.score.toDouble()
            track.last_episode_seen = seenContent.episodes.toDouble()
            track.started_watching_date = (seenContent.startDate ?: 0L) * 1000
            track.finished_watching_date = (seenContent.endDate ?: 0L) * 1000
            update(track)
        } else {
            track.status = if (hasSeenEpisodes) WATCHING else PLAN_TO_WATCH
            track.score = 0.0
            update(track)
        }
    }

    override suspend fun search(query: String): List<TrackSearch> {
        if (query.startsWith(SEARCH_ID_PREFIX)) {
            query.substringAfter(SEARCH_ID_PREFIX).trim().let { slug ->
                return api.getAnimeDetails(slug)?.let { listOf(it) } ?: emptyList()
            }
        }

        return api.searchAnime(query)
    }

    override suspend fun refresh(track: Track): Track {
        val remoteTrack = api.getAnime(track)
        track.copyPersonalFrom(remoteTrack)
        track.total_episodes = remoteTrack.total_episodes

        val seenContent = api.getSeen(track) ?: throw Exception("Could not find anime")

        track.score = seenContent.score.toDouble()
        track.last_episode_seen = seenContent.episodes.toDouble()
        track.status = toTrackStatus(seenContent.status)
        track.started_watching_date = (seenContent.startDate ?: 0L) * 1000
        track.finished_watching_date = (seenContent.endDate ?: 0L) * 1000

        return track
    }

    override suspend fun updateUserConfig() {
        val currentUser = api.getCurrentUser()
        saveDisplayUsername(currentUser.username)
    }

    override suspend fun login(username: String, password: String) = login(password)

    suspend fun login(reference: String) {
        try {
            val oauth = api.accessToken(reference)
            interceptor.setAuth(oauth)
            val user = api.getCurrentUser()
            saveDisplayUsername(user.username)
            saveCredentials(user.reference, oauth.accessToken)
        } catch (_: Throwable) {
            logout()
        }
    }

    override suspend fun delete(track: DomainTrack) = api.deleteUserAnime(track)

    override fun logout() {
        super.logout()
        trackPreferences.trackToken(this).delete()
        interceptor.setAuth(null)
    }

    fun saveOAuth(oAuth: HKOAuth?) {
        trackPreferences.trackToken(this).set(json.encodeToString(oAuth))
    }

    fun loadOAuth(): HKOAuth? {
        return try {
            json.decodeFromString<HKOAuth>(trackPreferences.trackToken(this).get())
        } catch (_: Exception) {
            null
        }
    }
}
