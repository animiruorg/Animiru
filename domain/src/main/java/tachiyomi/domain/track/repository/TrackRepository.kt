package tachiyomi.domain.track.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.track.model.Track

interface TrackRepository {

    suspend fun getTrackById(id: Long): Track?

    suspend fun getTracksByAnimeId(animeId: Long): List<Track>

    fun getTracksAsFlow(): Flow<List<Track>>

    fun getTracksByAnimeIdAsFlow(animeId: Long): Flow<List<Track>>

    suspend fun delete(animeId: Long, trackerId: Long)

    suspend fun upsert(track: Track)

    suspend fun upsertAll(tracks: List<Track>)

    // AM (GROUPING) -->
    suspend fun getTracks(): List<Track>
    // <-- AM (GROUPING)
}
