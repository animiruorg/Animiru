package eu.kanade.tachiyomi.data.track.kitsu.dto

import kotlinx.serialization.Serializable

@Serializable
data class KitsuUser(
    val id: String,
    val name: String,
    val ratingSystem: String,
)
