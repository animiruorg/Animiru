package eu.kanade.tachiyomi.data.track.anilist

import android.net.Uri
import androidx.core.net.toUri
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.apollographql.apollo.network.okHttpClient
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.anilist.dto.ALUser
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.dataOrElse
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import mihon.graphql.anilist.AniListAddAnimeMutation
import mihon.graphql.anilist.AniListDeleteAnimeMutation
import mihon.graphql.anilist.AniListGetAnimeDetailsQuery
import mihon.graphql.anilist.AniListGetCurrentUserQuery
import mihon.graphql.anilist.AniListGetLibAnimeQuery
import mihon.graphql.anilist.AniListSearchAnimeQuery
import mihon.graphql.anilist.AniListUpdateAnimeMutation
import mihon.graphql.anilist.type.FuzzyDateInput
import okhttp3.OkHttpClient
import tachiyomi.core.common.util.system.logcat
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import tachiyomi.domain.track.model.Track as DomainTrack

class AnilistApi(
    val trackerId: Long,
    val client: OkHttpClient,
    interceptor: AnilistInterceptor,
) {

    private val authClient = client.newBuilder()
        .addInterceptor(interceptor)
        .rateLimit(permits = 25, period = 1.minutes)
        .build()

    private val graphQlClient by lazy {
        ApolloClient.Builder()
            .serverUrl("https://graphql.anilist.co")
            .okHttpClient(authClient)
            .dispatcher(Dispatchers.IO)
            // required to log the error body in dataOrElse, which also properly closes it
            .httpExposeErrorBody(true)
            .build()
    }

    suspend fun addLibAnime(track: Track): Track {
        return graphQlClient
            .mutation(
                AniListAddAnimeMutation(
                    anime_id = track.remote_id.toInt(),
                    progress = track.last_episode_seen.toInt(),
                    status = track.toApiStatus(),
                    private = track.private,
                ),
            )
            .execute()
            .dataOrElse(
                errorLog = "AniList: Failed to add anime",
                default = { null },
            ) {
                it.SaveMediaListEntry?.id?.let { libraryId ->
                    track.library_id = libraryId.toLong()
                    track
                }
            }
            ?: throw Exception("Failed to add anime")
    }

    suspend fun updateLibAnime(track: Track): Track {
        val libraryId = track.library_id
        requireNotNull(libraryId) { "AniList cannot update track with null library_id" }

        return graphQlClient
            .mutation(
                AniListUpdateAnimeMutation(
                    library_id = libraryId.toInt(),
                    progress = track.last_episode_seen.toInt(),
                    status = track.toApiStatus(),
                    private = track.private,
                    score = track.score.toInt(),
                    startedAt = createFuzzyDate(track.started_watching_date),
                    completedAt = createFuzzyDate(track.finished_watching_date),
                ),
            )
            .execute()
            .dataOrElse(
                errorLog = "AniList: Failed to update anime",
                default = { null },
            ) {
                it.SaveMediaListEntry?.id?.let { remoteLibraryid ->
                    track.library_id = remoteLibraryid.toLong()
                    track
                }
            }
            ?: throw Exception("Failed to update anime")
    }

    suspend fun deleteLibAnime(track: DomainTrack) {
        val libraryId = track.libraryId
        requireNotNull(libraryId) { "AniList cannot delete track with null library_id" }

        graphQlClient
            .mutation(
                AniListDeleteAnimeMutation(library_id = libraryId.toInt()),
            )
            .execute()
            .dataOrElse(
                errorLog = "AniList: Failed to delete anime",
                default = { null },
            ) {
                it.DeleteMediaListEntry?.deleted?.let { deleted ->
                    if (deleted) {
                        logcat { "AniList: Deleted anime ${track.libraryId} successfully" }
                    }
                }
            }
            ?: throw Exception("Failed to delete anime")
    }

    suspend fun search(search: String): List<TrackSearch> {
        return graphQlClient
            .query(
                AniListSearchAnimeQuery(search = search),
            )
            .execute()
            .dataOrElse(
                errorLog = "AniList: Search failed",
                default = { emptyList() },
            ) {
                it.Page?.media
                    ?.mapNotNull { alAnime -> alAnime?.toTrackSearch(trackerId) }
                    ?: emptyList()
            }
    }

    suspend fun findLibAnime(track: Track, userid: Int): Track? {
        return graphQlClient
            .query(
                AniListGetLibAnimeQuery(
                    user_id = userid,
                    anime_id = track.remote_id.toInt(),
                ),
            )
            .execute()
            .dataOrElse(
                errorLog = "AniList: Failed to find anime in library",
                default = { null },
            ) {
                it.Page?.mediaList
                    ?.firstOrNull()
                    ?.toTrack(trackerId)
            }
    }

    suspend fun getCurrentUser(): ALUser {
        return graphQlClient
            .query(AniListGetCurrentUserQuery())
            .execute()
            .dataOrElse(
                errorLog = "AniList: Failed to get current user",
                default = { null },
            ) {
                it.Viewer?.toALUser()
            }
            ?: throw Exception("Failed to get AniList user data")
    }

    suspend fun getAnimeDetails(id: Int): TrackSearch? {
        return graphQlClient
            .query(
                AniListGetAnimeDetailsQuery(anime_id = id),
            )
            .execute()
            .dataOrElse(
                errorLog = "AniList: Failed to get anime details",
                default = { null },
            ) {
                it.Page?.media
                    ?.firstOrNull()
                    ?.toTrackSearch(trackerId)
            }
    }

    private fun createFuzzyDate(dateValue: Long): FuzzyDateInput {
        // all absent/null
        if (dateValue == 0L) return FuzzyDateInput()

        val dateTime = Instant.fromEpochMilliseconds(dateValue).toLocalDateTime(TimeZone.currentSystemDefault())
        return FuzzyDateInput(
            year = Optional.present(dateTime.year),
            month = Optional.present(dateTime.month.number),
            day = Optional.present(dateTime.day),
        )
    }

    companion object {
        private const val CLIENT_ID = "7719"

        fun authUrl(): Uri = "https://anilist.co/api/v2/oauth/authorize".toUri().buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("response_type", "token")
            .build()
    }
}
