package eu.kanade.tachiyomi.data.track.myanimelist

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.DeletableTracker
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALOAuth
import kotlinx.serialization.json.Json
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.domain.track.model.Track as DomainTrack

class MyAnimeList(id: Long) : BaseTracker(id, "MyAnimeList"), DeletableTracker {

    companion object {
        const val WATCHING = 1L
        const val COMPLETED = 2L
        const val ON_HOLD = 3L
        const val DROPPED = 4L
        const val PLAN_TO_WATCH = 6L
        const val REWATCHING = 7L

        private const val SEARCH_ID_PREFIX = "id:"
        private const val SEARCH_LIST_PREFIX = "my:"

        private val SCORE_LIST = IntRange(0, 10)
            .map(Int::toString)
    }

    private val json: Json by lazy { appGraph.json }

    private val interceptor by lazy { MyAnimeListInterceptor(this, json) }
    private val api by lazy { MyAnimeListApi(id, client, json, interceptor) }

    override val supportsReadingDates: Boolean = true

    override fun getLogo() = R.drawable.brand_myanimelist

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

    override fun getScoreList(): List<String> = SCORE_LIST

    override fun displayScore(track: DomainTrack): String {
        return track.score.toInt().toString()
    }

    private suspend fun add(track: Track): Track {
        return api.updateItem(track)
    }

    override suspend fun update(track: Track, didWatchEpisode: Boolean): Track {
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

        return api.updateItem(track)
    }

    override suspend fun delete(track: DomainTrack) {
        api.deleteItem(track)
    }

    override suspend fun bind(track: Track, hasSeenEpisodes: Boolean): Track {
        val remoteTrack = api.findListItem(track)
        return if (remoteTrack != null) {
            track.copyPersonalFrom(remoteTrack)
            track.remote_id = remoteTrack.remote_id

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
                return listOf(api.getAnimeDetails(id))
            }
        }

        if (query.startsWith(SEARCH_LIST_PREFIX)) {
            query.substringAfter(SEARCH_LIST_PREFIX).let { title ->
                return api.findListItems(title)
            }
        }

        return api.search(query)
    }

    override suspend fun refresh(track: Track): Track {
        return api.findListItem(track) ?: add(track)
    }

    override suspend fun login(username: String, password: String) = login(password)

    suspend fun login(authCode: String) {
        try {
            val oauth = api.getAccessToken(authCode)
            interceptor.setAuth(oauth)
            val username = api.getCurrentUser()
            saveDisplayUsername(username)
            saveCredentials(username, oauth.accessToken)
        } catch (_: Throwable) {
            logout()
        }
    }

    override suspend fun updateUserConfig() {
        val username = api.getCurrentUser()
        saveDisplayUsername(username)
    }

    override fun logout() {
        super.logout()
        trackPreferences.trackToken(this).delete()
        interceptor.setAuth(null)
    }

    fun getIfAuthExpired(): Boolean {
        return trackPreferences.trackAuthExpired(this).get()
    }

    fun setAuthExpired() {
        trackPreferences.trackAuthExpired(this).set(true)
    }

    fun saveOAuth(oAuth: MALOAuth?) {
        trackPreferences.trackToken(this).set(json.encodeToString(oAuth))
    }

    fun loadOAuth(): MALOAuth? {
        return try {
            json.decodeFromString<MALOAuth>(trackPreferences.trackToken(this).get())
        } catch (_: Exception) {
            null
        }
    }
}
