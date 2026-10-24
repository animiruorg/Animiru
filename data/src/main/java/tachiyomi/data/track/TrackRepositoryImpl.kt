package tachiyomi.data.track

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import tachiyomi.data.Database
import tachiyomi.data.subscribeToList
import tachiyomi.domain.track.model.Track
import tachiyomi.domain.track.repository.TrackRepository

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class TrackRepositoryImpl(
    private val database: Database,
) : TrackRepository {

    override suspend fun getTrackById(id: Long): Track? {
        return database.anime_trackQueries.getTrackById(id, TrackMapper::mapTrack).awaitAsOneOrNull()
    }

    override suspend fun getTracksByAnimeId(animeId: Long): List<Track> {
        return database.anime_trackQueries.getTracksByAnimeId(animeId, TrackMapper::mapTrack).awaitAsList()
    }

    override suspend fun getTracksByAnimeIds(animeIds: List<Long>): Map<Long, List<Track>> {
        return database.anime_trackQueries
            .getTracksByAnimeIds(animeIds, TrackMapper::mapTrack)
            .awaitAsList()
            .groupBy { it.animeId }
    }

    override suspend fun getTracks(): List<Track> {
        return database.anime_trackQueries
            .getTracks(TrackMapper::mapTrack)
            .awaitAsList()
    }

    override fun getTracksAsFlow(): Flow<List<Track>> {
        return database.anime_trackQueries.getTracks(TrackMapper::mapTrack)
            .subscribeToList()
    }

    override fun getTracksByAnimeIdAsFlow(animeId: Long): Flow<List<Track>> {
        return database.anime_trackQueries.getTracksByAnimeId(animeId, TrackMapper::mapTrack)
            .subscribeToList()
    }

    override suspend fun delete(animeId: Long, trackerId: Long) {
        database.anime_trackQueries.delete(
            animeId = animeId,
            trackerId = trackerId,
        )
    }

    override suspend fun upsert(track: Track) {
        upsertValues(track)
    }

    override suspend fun upsertAll(tracks: List<Track>) {
        upsertValues(*tracks.toTypedArray())
    }

    private suspend fun upsertValues(vararg tracks: Track) {
        database.transaction {
            tracks.forEach { animeTrack ->
                database.anime_trackQueries.upsert(
                    animeId = animeTrack.animeId,
                    trackerId = animeTrack.trackerId,
                    remoteId = animeTrack.remoteId,
                    libraryId = animeTrack.libraryId,
                    title = animeTrack.title,
                    lastEpisodeSeen = animeTrack.lastEpisodeSeen,
                    totalEpisodes = animeTrack.totalEpisodes,
                    status = animeTrack.status,
                    score = animeTrack.score,
                    remoteUrl = animeTrack.remoteUrl,
                    startDate = animeTrack.startDate,
                    finishDate = animeTrack.finishDate,
                    private = animeTrack.private,
                )
            }
        }
    }
}
