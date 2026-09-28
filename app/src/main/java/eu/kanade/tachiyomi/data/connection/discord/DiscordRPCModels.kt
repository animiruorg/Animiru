// AM (DISCORD_RPC) -->
package eu.kanade.tachiyomi.data.connection.discord

import dev.icerock.moko.resources.StringResource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import tachiyomi.i18n.MR
import tachiyomi.i18n.animiru.AMMR
import tachiyomi.i18n.aniyomi.AYMR

// Constant for logging tag
const val RICH_PRESENCE_TAG = "discord_rpc"

// Constant for application id
const val RICH_PRESENCE_APPLICATION_ID = "952899285983326208"

// Constant for buttons list
const val DOWNLOAD_BUTTON_LABEL = "Download"
const val DOWNLOAD_BUTTON_URL = "https://github.com/animiruorg/Animiru"
const val DISCORD_BUTTON_LABEL = "Discord"
const val DISCORD_BUTTON_URL = "https://discord.gg/yDuHDMwxhv"

@Serializable
data class DiscordActivity(
    @SerialName("application_id")
    val applicationId: String? = RICH_PRESENCE_APPLICATION_ID,
    val name: String? = null,
    val details: String? = null,
    val state: String? = null,
    val type: Int? = null,
    val timestamps: Timestamps? = null,
    val assets: Assets? = null,
    val buttons: List<String>? = null,
    val metadata: Metadata? = null,
) {
    @Serializable
    data class Assets(
        @SerialName("large_image")
        val largeImage: String? = null,
        @SerialName("large_text")
        val largeText: String? = null,
        @SerialName("small_image")
        val smallImage: String? = null,
        @SerialName("small_text")
        val smallText: String? = null,
    )

    @Serializable
    data class Metadata(
        @SerialName("button_urls")
        val buttonUrls: List<String>,
    )

    @Serializable
    data class Timestamps(
        val start: Long? = null,
        val end: Long? = null,
        val stop: Long? = null,
    )
}

@Serializable
data class Presence(
    val activities: List<DiscordActivity> = listOf(),
    val afk: Boolean = true,
    val since: Long? = null,
    val status: String? = null,
) {
    @Serializable
    data class Response(
        val op: Long,
        val d: Presence,
    )
}

@Serializable
data class Identity(
    val token: String,
    val properties: Properties,
    val compress: Boolean,
    val intents: Long,
) {

    @Serializable
    data class Response(
        val op: Long,
        val d: Identity,
    )

    @Serializable
    data class Properties(
        @SerialName("\$os")
        val os: String,

        @SerialName("\$browser")
        val browser: String,

        @SerialName("\$device")
        val device: String,
    )
}

@Serializable
data class Res(
    val t: String?,
    val s: Int?,
    val op: Int,
    val d: JsonElement,
)

enum class OpCode(val value: Int) {
    /** An event was dispatched. */
    DISPATCH(0),

    /** Fired periodically by the client to keep the connection alive. */
    HEARTBEAT(1),

    /** Starts a new session during the initial handshake. */
    IDENTIFY(2),

    /** Update the client's presence. */
    PRESENCE_UPDATE(3),

    /** Joins/leaves or moves between voice channels. */
    VOICE_STATE(4),

    /** Resume a previous session that was disconnected. */
    RESUME(6),

    /** You should attempt to reconnect and resume immediately. */
    RECONNECT(7),

    /** Request information about offline guild members in a large guild. */
    REQUEST_GUILD_MEMBERS(8),

    /** The session has been invalidated. You should reconnect and identify/resume accordingly */
    INVALID_SESSION(9),

    /** Sent immediately after connecting, contains the heartbeat_interval to use. */
    HELLO(10),

    /** Sent in response to receiving a heartbeat to acknowledge that it has been received. */
    HEARTBEAT_ACK(11),

    /** For future use or unknown opcodes. */
    UNKNOWN(-1),
}

data class PlayerData(
    val incognitoMode: Boolean = false,
    val paused: Boolean? = null,
    val animeId: Long? = null,
    val animeTitle: String? = null,
    val episodeNumber: String? = null,
    val thumbnailUrl: String? = null,
    val startTimestamp: Long? = null,
    val endTimestamp: Long? = null,
)

// Enum class for standard Rich Presence in-app screens
enum class DiscordScreen(val text: StringResource, val details: StringResource, val imageUrl: String) {
    APP(MR.strings.app_name, AMMR.strings.browsing, ANIMIRU_IMAGE_URL),
    LIBRARY(MR.strings.label_library, AMMR.strings.browsing, LIBRARY_IMAGE_URL),
    UPDATES(MR.strings.label_recent_updates, AMMR.strings.scrolling, UPDATES_IMAGE_URL),
    HISTORY(MR.strings.label_recent_manga, AMMR.strings.scrolling, HISTORY_IMAGE_URL),
    RECENTS(AMMR.strings.label_recent_recents, AMMR.strings.scrolling, RECENTS_IMAGE_URL),
    BROWSE(MR.strings.label_sources, AMMR.strings.browsing, BROWSE_IMAGE_URL),
    MORE(MR.strings.label_settings, AMMR.strings.messing, MORE_IMAGE_URL),
    WEBVIEW(MR.strings.action_web_view, AMMR.strings.browsing, WEBVIEW_IMAGE_URL),
    VIDEO(AMMR.strings.video, AYMR.strings.watching, VIDEO_IMAGE_URL),
}

// Constants for standard Rich Presence image urls
private const val ANIMIRU_IMAGE_URL = "emojis/1247521756898398269.webp?quality=lossless"
private const val LIBRARY_IMAGE_URL = "emojis/1247521758966317158.webp?quality=lossless"
private const val UPDATES_IMAGE_URL = "emojis/1247521763571794030.webp?quality=lossless"
private const val HISTORY_IMAGE_URL = "emojis/1247521754264506378.webp?quality=lossless"
private const val RECENTS_IMAGE_URL = "emojis/1247787737705353291.webp?quality=lossless"
private const val BROWSE_IMAGE_URL = "emojis/1247521751575826493.webp?quality=lossless"
private const val MORE_IMAGE_URL = "emojis/1247521761025851472.webp?quality=lossless"
private const val WEBVIEW_IMAGE_URL = "emojis/1247521768533524573.webp?quality=lossless"
private const val VIDEO_IMAGE_URL = "emojis/1247521765857693698.webp?quality=lossless"
// <-- AM (DISCORD_RPC)
