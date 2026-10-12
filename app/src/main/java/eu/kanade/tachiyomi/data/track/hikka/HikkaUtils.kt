package eu.kanade.tachiyomi.data.track.hikka

import eu.kanade.tachiyomi.data.database.models.Track
import java.util.UUID

internal fun Track.toApiStatus() = when (status) {
    Hikka.WATCHING -> "watching"
    Hikka.COMPLETED -> "completed"
    Hikka.ON_HOLD -> "on_hold"
    Hikka.DROPPED -> "dropped"
    Hikka.PLAN_TO_WATCH -> "planned"
    Hikka.REWATCHING -> "watching"
    else -> throw NotImplementedError("Hikka: Unknown status: $status")
}

internal fun toTrackStatus(status: String) = when (status) {
    "watching" -> Hikka.WATCHING
    "completed" -> Hikka.COMPLETED
    "on_hold" -> Hikka.ON_HOLD
    "dropped" -> Hikka.DROPPED
    "planned" -> Hikka.PLAN_TO_WATCH
    else -> throw NotImplementedError("Hikka: Unknown status: $status")
}

internal fun stringToNumber(input: String): Long {
    val uuid = UUID.nameUUIDFromBytes(input.toByteArray())
    return uuid.mostSignificantBits and Long.MAX_VALUE
}
