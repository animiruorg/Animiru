package eu.kanade.tachiyomi.data.track.kitsu

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.apollographql.apollo.network.okHttpClient
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuOAuth
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuUser
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.dataOrElse
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import mihon.graphql.kitsu.KitsuAddLibAnimeMutation
import mihon.graphql.kitsu.KitsuDeleteLibEntryMutation
import mihon.graphql.kitsu.KitsuFindLibAnimeQuery
import mihon.graphql.kitsu.KitsuGetAnimeDetailsByIdQuery
import mihon.graphql.kitsu.KitsuGetAnimeDetailsBySlugQuery
import mihon.graphql.kitsu.KitsuGetCurrentAccountQuery
import mihon.graphql.kitsu.KitsuSearchAnimeByTitleQuery
import mihon.graphql.kitsu.KitsuUpdateLibAnimeMutation
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
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

    private val graphQlClient by lazy {
        ApolloClient.Builder()
            .serverUrl("https://kitsu.app/api/graphql")
            .okHttpClient(authClient)
            .dispatcher(Dispatchers.IO)
            // required to log the error body in dataOrElse, which also properly closes it
            .httpExposeErrorBody(true)
            .build()
    }

    suspend fun addLibAnime(track: Track): Track {
        return graphQlClient
            .mutation(
                KitsuAddLibAnimeMutation(
                    media_id = track.remote_id.toString(),
                    status = track.toKitsuStatus(),
                    progress = track.last_episode_seen.toInt(),
                    private = track.private,
                    rating = Optional.present(track.score.toInt().takeIf { it > 0 }),
                ),
            )
            .execute()
            .dataOrElse(
                errorLog = "Kitsu: Failed to add anime",
                default = { null },
            ) {
                it.libraryEntry.create?.libraryEntry?.id?.let { libraryId ->
                    track.library_id = libraryId.toLong()
                    track
                }
            }
            ?: throw Exception("Failed to add anime")
    }

    suspend fun updateLibAnime(track: Track): Track {
        val libraryId = track.library_id
        requireNotNull(libraryId) { "Kitsu cannot update track with null library_id" }

        return graphQlClient
            .mutation(
                KitsuUpdateLibAnimeMutation(
                    library_id = libraryId.toString(),
                    status = track.toKitsuStatus(),
                    progress = track.last_episode_seen.toInt(),
                    private = track.private,
                    rating = Optional.present(track.score.toInt().takeIf { it > 0 }),
                    startedAt = Optional.present(
                        track.started_watching_date
                            .takeIf { it > 0 }
                            ?.let { Instant.fromEpochMilliseconds(it) },
                    ),
                    finishedAt = Optional.present(
                        track.finished_watching_date
                            .takeIf { it > 0 }
                            ?.let { Instant.fromEpochMilliseconds(it) },
                    ),
                ),
            )
            .execute()
            .dataOrElse(
                errorLog = "Kitsu: Failed to update anime",
                default = { null },
            ) {
                it.libraryEntry.update?.libraryEntry?.id?.let { libraryId ->
                    logcat { "Kitsu: Updated library entry $libraryId" }
                    track.library_id = libraryId.toLong()
                    track
                }
            }
            ?: throw Exception("Failed to update anime")
    }

    suspend fun removeLibAnime(track: DomainTrack) {
        val libraryId = track.libraryId
        requireNotNull(libraryId) { "Kitsu cannot delete track with null library_id" }

        graphQlClient
            .mutation(
                KitsuDeleteLibEntryMutation(
                    library_id = libraryId.toString(),
                ),
            )
            .execute()
            .dataOrElse(
                errorLog = "Kitsu: Failed to delete anime",
                default = {},
                onException = { e ->
                    val body = e.body?.use { it.readUtf8() }
                    if (
                        e.statusCode == 500 &&
                        body?.contains("Couldn't find LibraryEntry with 'id'=") == true
                    ) {
                        // Deleting something not in the library (currently as of 2026-09-20) returns a 500 with a
                        // "Couldn't find LibraryEntry" error message at the error.message key --
                        // but the user gets their wish of "title not in library" so ignore it
                        return@dataOrElse
                    } else {
                        throw HttpException(e.statusCode).apply { stackTrace = e.stackTrace }
                    }
                },
            ) {
                logcat { "Kitsu: Deleted library entry ${it.libraryEntry.delete?.libraryEntry?.id}" }
            }
    }

    suspend fun search(search: String): List<TrackSearch> {
        return graphQlClient
            .query(
                KitsuSearchAnimeByTitleQuery(
                    query = search,
                ),
            )
            .execute()
            .dataOrElse(
                errorLog = "Kitsu: Search failed",
                default = { emptyList() },
            ) {
                it.searchAnimeByTitle.nodes
                    ?.mapNotNull { node -> node?.toTrackSearch(trackerId) }
            }
            ?: emptyList()
    }

    suspend fun findLibAnime(track: Track): Track? {
        return graphQlClient
            .query(
                KitsuFindLibAnimeQuery(
                    remote_id = track.remote_id.toString(),
                ),
            )
            .execute()
            .dataOrElse(
                errorLog = "Kitsu: Failed to find anime in library",
                default = { null },
            ) {
                it.findAnimeById?.toTrackSearch(trackerId)
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

    suspend fun getCurrentUser(): KitsuUser {
        return graphQlClient
            .query(
                KitsuGetCurrentAccountQuery(),
            )
            .execute()
            .dataOrElse(
                errorLog = "Kitsu: Failed to get current user",
                default = { null },
            ) {
                it.currentAccount?.toKitsuUser()
            }
            ?: throw Exception("Failed to get Kitsu user data")
    }

    suspend fun getAnimeDetails(search: String): TrackSearch? {
        val isSearchById = search.matches(Regex("\\d+"))

        return if (isSearchById) {
            getAnimeDetailsById(search)
        } else {
            getAnimeDetailsBySlug(search)
        }
    }

    private suspend fun getAnimeDetailsById(id: String): TrackSearch? {
        return graphQlClient
            .query(
                KitsuGetAnimeDetailsByIdQuery(
                    id = id,
                ),
            )
            .execute()
            .dataOrElse(
                errorLog = "Kitsu: Search by ID failed",
                default = { null },
            ) {
                it.findAnimeById?.toTrackSearch(trackerId)
            }
    }

    private suspend fun getAnimeDetailsBySlug(slug: String): TrackSearch? {
        return graphQlClient
            .query(
                KitsuGetAnimeDetailsBySlugQuery(
                    slug = slug,
                ),
            )
            .execute()
            .dataOrElse(
                errorLog = "Kitsu: Search by Slug failed",
                default = { null },
            ) {
                it.findAnimeBySlug?.toTrackSearch(trackerId)
            }
    }

    companion object {
        private const val CLIENT_ID = "dd031b32d2f56c990b1425efe6c42ad847e7fe3ab46bf1299f05ecd856bdb7dd"
        private const val CLIENT_SECRET = "54d7307928f63414defd96399fc31ba847961ceaecef3a5fd93144e960c0e151"

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
    }
}
