@file:Suppress("PropertyName")

package eu.kanade.tachiyomi.data.database.models

import kotlinx.serialization.json.JsonObject
import mihon.core.common.extensions.EMPTY

class EpisodeImpl : Episode {

    override var id: Long? = null

    override var anime_id: Long? = null

    override lateinit var url: String

    override lateinit var name: String

    override var scanlator: String? = null

    // AY -->
    override var summary: String? = null

    override var preview_url: String? = null
    // <-- AY

    override var seen: Boolean = false

    override var bookmark: Boolean = false

    // AY -->
    override var fillermark: Boolean = false
    // <-- AY

    override var last_second_seen: Long = 0

    // AY -->
    override var total_seconds: Long = 0
    // <-- AY

    override var date_fetch: Long = 0

    override var date_upload: Long = 0

    override var episode_number: Float = 0f

    override var source_order: Int = 0

    override var memo: JsonObject = JsonObject.EMPTY

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        val episode = other as Episode
        if (url != episode.url) return false
        return id == episode.id
    }

    override fun hashCode(): Int {
        return url.hashCode() + id.hashCode()
    }
}
