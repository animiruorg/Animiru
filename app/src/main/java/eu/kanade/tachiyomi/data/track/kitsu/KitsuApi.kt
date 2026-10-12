package eu.kanade.tachiyomi.data.track.kitsu

import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuAccount
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuAddAnimeResult
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuCurrentAccountResult
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuDeleteAnimeResult
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuOAuth
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuSearchByIdResult
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuSearchByIdWithLibraryResult
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuSearchBySlugResult
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuSearchByTitleResult
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuUpdateAnimeResult
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import logcat.LogPriority
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import kotlin.time.Instant
import tachiyomi.domain.track.model.Track as DomainTrack

class KitsuApi(
    private val trackerId: Long,
    private val client: OkHttpClient,
    private val json: Json,
    interceptor: KitsuInterceptor,
) {

    private val authClient = client.newBuilder().addInterceptor(interceptor).build()

    suspend fun addLibAnime(track: Track): Track {
        return withIOContext {
            val query = $$"""
                |mutation AddAnime(
                  |$media_id: ID!
                  |$status: LibraryEntryStatusEnum!
                  |$progress: Int!
                  |$private: Boolean!
                  |$rating: Int
                |) {
                  |libraryEntry {
                    |create(
                      |input: {
                        |mediaId: $media_id
                        |mediaType: ANIME
                        |status: $status
                        |progress: $progress
                        |private: $private
                        |rating: $rating
                      |}
                    |) {
                      |errors {
                        |message
                      |}
                      |libraryEntry {
                        |id
                      |}
                    |}
                  |}
                |}
            """.trimMargin()

            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("media_id", track.remote_id)
                    put("status", track.toKitsuApiStatus())
                    put("progress", track.last_episode_seen.toInt())
                    put("private", track.private)
                    put("rating", track.score.toInt().takeIf { it > 0 })
                }
            }

            with(json) {
                val parsed = authClient.newCall(
                    POST(
                        GRAPHQL_API_URL,
                        body = payload.toString().toRequestBody(jsonMime),
                    ),
                )
                    .awaitSuccess()
                    .parseAs<KitsuAddAnimeResult>()

                if (parsed.error != null) {
                    logcat(LogPriority.ERROR) { "Failed to add: ${parsed.error.message ?: "(none)"}" }
                    throw Exception("Failed to add anime")
                } else if (parsed.errors != null) {
                    parsed.errors.forEach {
                        logcat(LogPriority.ERROR) { "Failed to add: ${it.message ?: "(none)"}" }
                    }
                    throw Exception("Failed to add anime")
                } else if (parsed.data == null) {
                    logcat(LogPriority.ERROR) { "Kitsu error, errors, and data null?" }
                    throw Exception("Encountered unexpected error while adding anime")
                }

                parsed.data.libraryEntry.create.libraryEntry.id.let {
                    track.library_id = it.toLong()
                    track
                }
            }
        }
    }

    suspend fun updateLibAnime(track: Track): Track {
        return withIOContext {
            val query = $$"""
                |mutation UpdateAnime(
                  |$library_id: ID!
                  |$status: LibraryEntryStatusEnum!
                  |$progress: Int!
                  |$private: Boolean!
                  |$rating: Int
                  |$startedAt: ISO8601DateTime
                  |$finishedAt: ISO8601DateTime
                |) {
                  |libraryEntry {
                    |update(
                      |input: {
                        |id: $library_id
                        |status: $status
                        |progress: $progress
                        |private: $private
                        |rating: $rating
                        |startedAt: $startedAt
                        |finishedAt: $finishedAt
                      |}
                    |) {
                      |errors {
                        |message
                      |}
                      |libraryEntry {
                        |id
                      |}
                    |}
                  |}
                |}
            """.trimMargin()

            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("library_id", track.library_id)
                    put("status", track.toKitsuApiStatus())
                    put("progress", track.last_episode_seen.toInt())
                    put("private", track.private)
                    put("rating", track.score.toInt().takeIf { it > 0 })
                    put(
                        "startedAt",
                        track.started_watching_date
                            .takeIf { it > 0 }
                            ?.let { Instant.fromEpochMilliseconds(it).toString() },
                    )
                    put(
                        "finishedAt",
                        track.finished_watching_date
                            .takeIf { it > 0 }
                            ?.let { Instant.fromEpochMilliseconds(it).toString() },
                    )
                }
            }

            with(json) {
                val parsed = authClient.newCall(
                    POST(
                        GRAPHQL_API_URL,
                        body = payload.toString().toRequestBody(jsonMime),
                    ),
                )
                    .awaitSuccess()
                    .parseAs<KitsuUpdateAnimeResult>()

                if (parsed.error != null) {
                    logcat(LogPriority.ERROR) { "Failed to update: ${parsed.error.message ?: "(none)"}" }
                    throw Exception("Failed to update anime")
                } else if (parsed.errors != null) {
                    parsed.errors.forEach {
                        logcat(LogPriority.ERROR) { "Failed to update: ${it.message ?: "(none)"}" }
                    }
                    throw Exception("Failed to update anime")
                } else if (parsed.data == null) {
                    logcat(LogPriority.ERROR) { "Kitsu error, errors, and data null?" }
                    throw Exception("Encountered unexpected error while updating anime")
                }

                track
            }
        }
    }

    suspend fun removeLibAnime(track: DomainTrack) {
        withIOContext {
            val query = $$"""|
                |mutation DeleteLibEntry(
                  |$library_id: ID!
                |) {
                  |libraryEntry {
                    |delete(
                      |input: {
                        |id: $library_id
                      |}
                    |) {
                      |errors {
                        |message
                      |}
                      |libraryEntry {
                        |id
                      |}
                    |}
                  |}
                |}
            """.trimMargin()

            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("library_id", track.libraryId)
                }
            }

            with(json) {
                val parsed = authClient.newCall(
                    POST(
                        GRAPHQL_API_URL,
                        body = payload.toString().toRequestBody(jsonMime),
                    ),
                )
                    // Deleting something not in the library returns a 500 with "Couldn't find LibraryEntry" msg
                    // awaitSuccess would throw with that but user gets their wish of "title not in library" so ignore it
                    .await()
                    .parseAs<KitsuDeleteAnimeResult>()

                if (parsed.error != null) {
                    logcat(LogPriority.ERROR) { "Failed to delete: ${parsed.error.message ?: "(none)"}" }
                    if (parsed.error.message != null && parsed.error.message.startsWith("Couldn't find")) {
                        return@with
                    }
                    throw Exception("Failed to delete anime")
                } else if (parsed.errors != null) {
                    parsed.errors.forEach {
                        logcat(LogPriority.ERROR) { "Failed to delete: ${it.message ?: "(none)"}" }
                    }
                    throw Exception("Failed to delete anime")
                } else if (parsed.data == null) {
                    logcat(LogPriority.ERROR) { "Kitsu error, errors, and data null?" }
                    throw Exception("Encountered unexpected error while deleting anime")
                }
            }
        }
    }

    suspend fun search(search: String): List<TrackSearch> {
        return withIOContext {
            val query = $$"""
                |query Query($query: String!) {
                  |searchAnimeByTitle(title: $query, first: 20) {
                    |nodes {
                      $$COMMON_ANIME_DATA
                    |}
                  |}
                |}
            """.trimMargin()

            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("query", search)
                }
            }

            with(json) {
                authClient.newCall(
                    POST(
                        GRAPHQL_API_URL,
                        body = payload.toString().toRequestBody(jsonMime),
                    ),
                )
                    .awaitSuccess()
                    .parseAs<KitsuSearchByTitleResult>()
                    .data.searchAnimeByTitle.nodes
                    .map { it.toTrackSearch(trackerId) }
            }
        }
    }

    suspend fun findLibAnime(track: Track): Track? {
        return withIOContext {
            val query = $$"""
                |query Query($remote_id: ID!) {
                  |findAnimeById(id: $remote_id) {
                    |$$COMMON_ANIME_DATA
                    |myLibraryEntry {
                      |id
                      |private
                      |progress
                      |rating
                      |reconsuming
                      |status
                      |startedAt
                      |finishedAt
                    |}
                  |}
                |}
            """.trimMargin()

            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("remote_id", track.remote_id)
                }
            }

            with(json) {
                authClient.newCall(
                    POST(
                        GRAPHQL_API_URL,
                        body = payload.toString().toRequestBody(jsonMime),
                    ),
                )
                    .awaitSuccess()
                    .parseAs<KitsuSearchByIdWithLibraryResult>()
                    .data.findAnimeById
                    ?.toTrackSearch(trackerId)
            }
        }
    }

    suspend fun login(username: String, password: String): KitsuOAuth {
        return withIOContext {
            val formBody: RequestBody = FormBody.Builder()
                .add("username", username)
                .add("password", password)
                .add("grant_type", "password")
                .add("client_id", CLIENT_ID)
                .add("client_secret", CLIENT_SECRET)
                .build()
            with(json) {
                client.newCall(POST(LOGIN_URL, body = formBody))
                    .awaitSuccess()
                    .parseAs()
            }
        }
    }

    suspend fun getCurrentUser(): KitsuAccount {
        return withIOContext {
            val query = """
                |query Query {
                  |currentAccount {
                    |id
                    |ratingSystem
                    |profile {
                      |name
                    |}
                  |}
                |}
            """.trimMargin()

            val payload = buildJsonObject {
                put("query", query)
            }

            with(json) {
                authClient.newCall(
                    POST(
                        GRAPHQL_API_URL,
                        body = payload.toString().toRequestBody(jsonMime),
                    ),
                )
                    .awaitSuccess()
                    .parseAs<KitsuCurrentAccountResult>()
                    .data.currentAccount
            }
        }
    }

    suspend fun getAnimeDetails(search: String): TrackSearch? {
        val isSearchById = search.matches(Regex("\\d+"))

        val query = if (isSearchById) {
            $$"""
                |query Query($query: ID!) {
                  |findAnimeById(id: $query) {
                    |$$COMMON_ANIME_DATA
                  |}
                |}
            """
        } else {
            $$"""
                |query Query($query: String!) {
                  |findAnimeBySlug(slug: $query) {
                    |$$COMMON_ANIME_DATA
                  |}
                |}
            """
        }

        val payload = buildJsonObject {
            put("query", query.trimMargin())
            putJsonObject("variables") {
                put("query", search)
            }
        }

        return withIOContext {
            with(json) {
                val response = authClient.newCall(
                    POST(
                        GRAPHQL_API_URL,
                        body = payload.toString().toRequestBody(jsonMime),
                    ),
                )
                    .awaitSuccess()

                val kitsuAnime = if (isSearchById) {
                    response
                        .parseAs<KitsuSearchByIdResult>()
                        .data.findAnimeById
                } else {
                    response
                        .parseAs<KitsuSearchBySlugResult>()
                        .data.findAnimeBySlug
                }

                kitsuAnime?.toTrackSearch(trackerId)
            }
        }
    }

    companion object {
        private const val CLIENT_ID = "dd031b32d2f56c990b1425efe6c42ad847e7fe3ab46bf1299f05ecd856bdb7dd"
        private const val CLIENT_SECRET = "54d7307928f63414defd96399fc31ba847961ceaecef3a5fd93144e960c0e151"

        private const val GRAPHQL_API_URL = "https://kitsu.app/api/graphql"
        private const val LOGIN_URL = "https://kitsu.app/api/oauth/token"

        fun refreshTokenRequest(token: String) = POST(
            LOGIN_URL,
            body = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", token)
                .add("client_id", CLIENT_ID)
                .add("client_secret", CLIENT_SECRET)
                .build(),
        )

        private val COMMON_ANIME_DATA = """
            |id
            |titles {
              |preferred
            |}
            |episodeCount
            |productions(first: 10) {
              |nodes {
                |role
                |company {
                  |name
                |}
              |}
            |}
            |posterImage {
              |views(names: "small") {
                |name
                |url
              |}
              |original {
                |name
                |url
              |}
            |}
            |description(locales: "en")
            |status
            |subtype
            |startDate
            |endDate
            |slug
            |averageRating
        """.trimMargin()
    }
}
