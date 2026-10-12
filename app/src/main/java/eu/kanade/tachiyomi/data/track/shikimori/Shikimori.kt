package eu.kanade.tachiyomi.data.track.shikimori

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.DeletableTracker
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.data.track.shikimori.dto.SMOAuth
import kotlinx.serialization.json.Json
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.domain.track.model.Track as DomainTrack

class Shikimori(id: Long) : BaseTracker(id, "Shikimori"), DeletableTracker {

    companion object {
        const val WATCHING = 1L
        const val COMPLETED = 2L
        const val ON_HOLD = 3L
        const val DROPPED = 4L
        const val PLAN_TO_WATCH = 5L
        const val REWATCHING = 6L

        private val SCORE_LIST = IntRange(0, 10)
            .map(Int::toString)

        private const val SEARCH_ID_PREFIX = "id:"
    }

    private val json: Json by lazy { appGraph.json }

    private val interceptor by lazy { ShikimoriInterceptor(this, json) }

    private val api by lazy { ShikimoriApi(id, client, json, interceptor) }

    override fun getScoreList(): List<String> = SCORE_LIST

    override fun displayScore(track: DomainTrack): String {
        return track.score.toInt().toString()
    }

    private suspend fun add(track: Track): Track {
        return api.addLibAnime(track, getUsername())
    }

    override suspend fun update(track: Track, didWatchEpisode: Boolean): Track {
        if (track.status != COMPLETED) {
            if (didWatchEpisode) {
                if (track.last_episode_seen.toLong() == track.total_episodes && track.total_episodes > 0) {
                    track.status = COMPLETED
                } else if (track.status != REWATCHING) {
                    track.status = WATCHING
                }
            }
        }

        return api.updateLibAnime(track)
    }

    override suspend fun delete(track: DomainTrack) {
        api.deleteLibAnime(track)
    }

    override suspend fun bind(track: Track, hasSeenEpisodes: Boolean): Track {
        val remoteTrack = api.findLibAnime(track)
        return if (remoteTrack != null) {
            track.copyPersonalFrom(remoteTrack)
            track.library_id = remoteTrack.library_id

            if (track.status != COMPLETED) {
                val isRewatching = track.status == REWATCHING
                track.status = if (!isRewatching && hasSeenEpisodes) WATCHING else track.status
            }

            update(track)
        } else {
            // Set default fields if it's not found in the list
            track.status = if (hasSeenEpisodes) WATCHING else PLAN_TO_WATCH
            track.score = 0.0
            add(track)
        }
    }

    override suspend fun search(query: String): List<TrackSearch> {
        if (query.startsWith(SEARCH_ID_PREFIX)) {
            query.substringAfter(SEARCH_ID_PREFIX).trim().toIntOrNull()?.let { id ->
                return api.getAnimeDetails(id)?.let { listOf(it) } ?: emptyList()
            }
        }

        return api.search(query)
    }

    override suspend fun refresh(track: Track): Track {
        val remoteTrack = api.findLibAnime(track) ?: throw Exception("Could not find anime")
        track.library_id = remoteTrack.library_id
        track.copyPersonalFrom(remoteTrack)
        track.total_episodes = remoteTrack.total_episodes
        return track
    }

    override fun getLogo() = R.drawable.brand_shikimori

    override fun getStatusList(): List<Long> {
        return listOf(WATCHING, COMPLETED, ON_HOLD, DROPPED, PLAN_TO_WATCH, REWATCHING)
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

    override suspend fun login(username: String, password: String) = login(password)

    suspend fun login(code: String) {
        try {
            val oauth = api.accessToken(code)
            interceptor.newAuth(oauth)
            val user = api.getCurrentUser()
            saveDisplayUsername(user.nickname)
            saveCredentials(user.id, oauth.accessToken)
        } catch (_: Throwable) {
            logout()
        }
    }

    override suspend fun updateUserConfig() {
        val currentUser = api.getCurrentUser()
        saveDisplayUsername(currentUser.nickname)
    }

    fun saveToken(oauth: SMOAuth?) {
        trackPreferences.trackToken(this).set(json.encodeToString(oauth))
    }

    fun restoreToken(): SMOAuth? {
        return try {
            json.decodeFromString<SMOAuth>(trackPreferences.trackToken(this).get())
        } catch (_: Exception) {
            null
        }
    }

    override fun logout() {
        super.logout()
        trackPreferences.trackToken(this).delete()
        interceptor.newAuth(null)
    }
}
