package tachiyomi.domain.track.model

import java.io.Serializable
import kotlin.time.Instant

data class Track(
    val id: Long,
    val animeId: Long,
    val trackerId: Long,
    val remoteId: Long,
    val libraryId: Long?,
    val title: String,
    val lastEpisodeSeen: Double,
    val totalEpisodes: Long,
    val status: Long,
    val score: Double,
    val remoteUrl: String,
    val startDate: Instant?,
    val finishDate: Instant?,
    val private: Boolean,
) : Serializable
