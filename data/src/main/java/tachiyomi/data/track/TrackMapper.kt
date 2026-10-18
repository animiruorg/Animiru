package tachiyomi.data.track

import tachiyomi.domain.track.model.Track

object TrackMapper {
    fun mapTrack(
        id: Long,
        animeId: Long,
        trackerId: Long,
        remoteId: Long,
        remoteUrl: String,
        libraryId: Long?,
        title: String,
        lastEpisodeSeen: Double,
        totalEpisodes: Long,
        status: Long,
        score: Double,
        startDate: Long,
        finishDate: Long,
        private: Boolean,
    ): Track = Track(
        id = id,
        animeId = animeId,
        trackerId = trackerId,
        remoteId = remoteId,
        libraryId = libraryId,
        title = title,
        lastEpisodeSeen = lastEpisodeSeen,
        totalEpisodes = totalEpisodes,
        status = status,
        score = score,
        remoteUrl = remoteUrl,
        startDate = startDate,
        finishDate = finishDate,
        private = private,
    )
}
