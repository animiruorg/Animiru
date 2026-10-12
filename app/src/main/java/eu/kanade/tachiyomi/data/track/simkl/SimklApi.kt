// AY -->
package eu.kanade.tachiyomi.data.track.simkl

import android.net.Uri
import androidx.core.net.toUri
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.data.track.simkl.dto.SimklDetails
import eu.kanade.tachiyomi.data.track.simkl.dto.SimklOAuth
import eu.kanade.tachiyomi.data.track.simkl.dto.SimklSearchResult
import eu.kanade.tachiyomi.data.track.simkl.dto.SimklSyncResult
import eu.kanade.tachiyomi.data.track.simkl.dto.SimklSyncWatched
import eu.kanade.tachiyomi.data.track.simkl.dto.SimklUser
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.lang.withIOContext

class SimklApi(
    private val trackerId: Long,
    private val client: OkHttpClient,
    private val json: Json,
    interceptor: SimklInterceptor,
) {

    private val authClient = client.newBuilder().addInterceptor(interceptor).build()

    suspend fun addLibAnime(track: Track): Track {
        return withIOContext {
            val type = track.tracking_url
                .substringAfter("/")
                .substringBefore("/")
            val mediaType = if (type == "movies") "movies" else "shows"
            addToList(track, mediaType)

            track
        }
    }

    private suspend fun addToList(track: Track, mediaType: String) {
        val payload = buildJsonObject {
            putJsonArray(mediaType) {
                addJsonObject {
                    putJsonObject("ids") {
                        put("simkl", track.remote_id)
                    }
                    put("to", track.toSimklStatus())
                }
            }
        }.toString().toRequestBody(jsonMime)
        authClient.newCall(
            POST("$API_URL/sync/add-to-list", body = payload),
        ).awaitSuccess()
    }

    private suspend fun updateRating(track: Track, mediaType: String) {
        val payload = buildJsonObject {
            putJsonArray(mediaType) {
                addJsonObject {
                    putJsonObject("ids") {
                        put("simkl", track.remote_id)
                    }
                    put("rating", track.score.toInt())
                }
            }
        }.toString().toRequestBody(jsonMime)

        if (track.score == 0.0) {
            authClient.newCall(
                POST("$API_URL/sync/ratings/remove", body = payload),
            ).awaitSuccess()
        } else {
            authClient.newCall(
                POST("$API_URL/sync/ratings", body = payload),
            ).awaitSuccess()
        }
    }

    private suspend fun updateProgress(track: Track) {
        // first remove
        authClient.newCall(
            POST("$API_URL/sync/history/remove", body = buildProgressObject(track, false)),
        ).awaitSuccess()
        // then add again
        authClient.newCall(
            POST("$API_URL/sync/history", body = buildProgressObject(track, true)),
        ).awaitSuccess()
    }

    private fun buildProgressObject(track: Track, add: Boolean = true) = buildJsonObject {
        putJsonArray("shows") {
            addJsonObject {
                putJsonObject("ids") {
                    put("simkl", track.remote_id)
                }
                putJsonArray("seasons") {
                    addJsonObject {
                        put("number", 1)
                        if (add) {
                            putJsonArray("episodes") {
                                for (epNum in 1..track.last_episode_seen.toInt()) {
                                    addJsonObject {
                                        put("number", epNum)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }.toString().toRequestBody(jsonMime)

    suspend fun updateLibAnime(track: Track): Track {
        return withIOContext {
            // determine media type
            val type = track.tracking_url
                .substringAfter("/")
                .substringBefore("/")
            val mediaType = if (type == "movies") "movies" else "shows"
            // update progress only for shows
            if (type != "movies") {
                updateProgress(track)
            }
            // add to correct list
            addToList(track, mediaType)
            // update rating
            updateRating(track, mediaType)

            track
        }
    }

    suspend fun getAnimeDetails(id: Int): List<TrackSearch> {
        return listOfNotNull(
            getEntryDetails("anime", id),
            getEntryDetails("shows", id),
            getEntryDetails("movie", id),
        )
    }

    private suspend fun getEntryDetails(type: String, id: Int): TrackSearch? {
        return withIOContext {
            try {
                with(json) {
                    val url = API_URL.toHttpUrl().newBuilder().apply {
                        addPathSegment(type)
                        addPathSegment(id.toString())
                        addQueryParameter("client_id", CLIENT_ID)
                    }.build()
                    client.newCall(GET(url))
                        .awaitSuccess()
                        .parseAs<SimklDetails>()
                        .toTrackSearch(trackerId, type)
                }
            } catch (_: MissingFieldException) {
                null
            } catch (e: HttpException) {
                if (e.code == 404 || e.code == 302) {
                    return@withIOContext null
                }
                throw e
            }
        }
    }

    suspend fun searchAnime(search: String, type: String): List<TrackSearch> {
        return withIOContext {
            val searchUrl = "$API_URL/search/$type".toUri().buildUpon()
                .appendQueryParameter("q", search)
                .appendQueryParameter("extended", "full")
                .appendQueryParameter("client_id", CLIENT_ID)
                .build()
            with(json) {
                client.newCall(GET(searchUrl.toString()))
                    .awaitSuccess()
                    .parseAs<List<SimklSearchResult>>()
                    .map { it.toTrackSearch(trackerId, type) }
            }
        }
    }

    /**
     * Checks if the given [track] exists in the user's list and
     * returns all info about it or null if it isn't found.
     */
    suspend fun findLibAnime(track: Track): Track? {
        return withIOContext {
            val payload = buildJsonArray {
                addJsonObject {
                    put("simkl", track.remote_id)
                }
            }.toString().toRequestBody(jsonMime)
            val foundAnime = with(json) {
                authClient.newCall(
                    POST("$API_URL/sync/watched", body = payload),
                )
                    .awaitSuccess()
                    .parseAs<List<SimklSyncWatched>>()
                    .firstOrNull() ?: return@withIOContext null
            }

            if (foundAnime.result != true) return@withIOContext null
            val lastWatched = foundAnime.lastWatchedAt ?: return@withIOContext null
            val status = foundAnime.list ?: return@withIOContext null
            val type = track.tracking_url
                .substringAfter("/")
                .substringBefore("/")
            val queryType = if (type == "tv") "shows" else type
            val url = "$API_URL/sync/all-items/$queryType/$status".toUri().buildUpon()
                .appendQueryParameter("date_from", lastWatched)
                .build()

            val typeName = if (type == "movies") "movie" else "show"
            val listAnime = with(json) {
                authClient.newCall(GET(url.toString()))
                    .awaitSuccess()
                    .parseAs<SimklSyncResult?>()
                    ?.getFromType(queryType)
                    ?.firstOrNull { item ->
                        item.getFromType(typeName).ids.simkl == track.remote_id
                    } ?: return@withIOContext null
            }

            listAnime.toTrack(trackerId, typeName, type, status)
        }
    }

    fun getCurrentUser(): SimklUser {
        return runBlocking {
            with(json) {
                authClient.newCall(GET("$API_URL/users/settings"))
                    .awaitSuccess()
                    .parseAs<SimklUser>()
            }
        }
    }

    suspend fun accessToken(code: String): SimklOAuth {
        return withIOContext {
            with(json) {
                client.newCall(accessTokenRequest(code))
                    .awaitSuccess()
                    .parseAs()
            }
        }
    }

    private fun accessTokenRequest(code: String) = POST(
        OAUTH_URL,
        body = buildJsonObject {
            put("code", code)
            put("client_id", CLIENT_ID)
            put("client_secret", CLIENT_SECRET)
            put("redirect_uri", REDIRECT_URL)
            put("grant_type", "authorization_code")
        }.toString().toRequestBody(jsonMime),
    )

    companion object {
        const val CLIENT_ID = "350eaf4b09c4fac6a762f7b42427d33eca32e8b09b8d0e66d0de0a86bd8026ce"
        private const val CLIENT_SECRET = "5eb22d7a0926b6a1167eec272b8cef33ee45952dbdc45ba5980eb597f759e9c9"

        private const val BASE_URL = "https://simkl.com"
        private const val API_URL = "https://api.simkl.com"
        private const val OAUTH_URL = "$API_URL/oauth/token"
        private const val LOGIN_URL = "$BASE_URL/oauth/authorize"
        const val POSTERS_URL = "https://simkl.in/posters/"

        private const val REDIRECT_URL = "animiru://simkl-auth"

        fun authUrl(): Uri =
            LOGIN_URL.toUri().buildUpon()
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("client_id", CLIENT_ID)
                .appendQueryParameter("redirect_uri", REDIRECT_URL)
                .build()
    }
}
// <-- AY
