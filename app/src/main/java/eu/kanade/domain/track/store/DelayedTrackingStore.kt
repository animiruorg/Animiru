package eu.kanade.domain.track.store

import android.content.Context
import androidx.core.content.edit
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

@Inject
@SingleIn(AppScope::class)
class DelayedTrackingStore(context: Context) {

    /**
     * Preference file where queued tracking updates are stored.
     */
    private val preferences by lazy { context.getSharedPreferences("tracking_queue", Context.MODE_PRIVATE) }

    fun add(trackId: Long, lastEpisodeSeen: Double) {
        val previousLastEpisodeSeen = preferences.getFloat(trackId.toString(), 0f)
        if (lastEpisodeSeen > previousLastEpisodeSeen) {
            logcat(LogPriority.DEBUG) { "Queuing track item: $trackId, last episode seen: $lastEpisodeSeen" }
            preferences.edit {
                putFloat(trackId.toString(), lastEpisodeSeen.toFloat())
            }
        }
    }

    fun remove(trackId: Long) {
        preferences.edit {
            remove(trackId.toString())
        }
    }

    fun getItems(): List<DelayedTrackingItem> {
        return preferences.all.mapNotNull {
            DelayedTrackingItem(
                trackId = it.key.toLong(),
                lastEpisodeSeen = it.value.toString().toFloat(),
            )
        }
    }

    data class DelayedTrackingItem(
        val trackId: Long,
        val lastEpisodeSeen: Float,
    )
}
