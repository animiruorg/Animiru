// AM (DISCORD_RPC) -->
package eu.kanade.tachiyomi.data.connection.discord

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.compose.ui.util.fastAny
import androidx.core.app.ServiceCompat
import dev.zacsweers.metro.Inject
import eu.kanade.domain.connection.service.ConnectionPreferences
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.connection.ConnectionManager
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.notificationBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import logcat.LogPriority
import mihon.app.di.AppGraph
import mihon.app.di.appGraph
import mihon.core.metro.metroGraph
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.model.Category.Companion.UNCATEGORIZED_ID
import tachiyomi.i18n.MR
import tachiyomi.i18n.animiru.AMMR
import kotlin.math.ceil
import kotlin.math.floor

class DiscordRPCService : Service() {

    private val graph: AppGraph by lazy { metroGraph() }

    @Inject private lateinit var connectionManager: ConnectionManager

    @Inject private lateinit var connectionPreferences: ConnectionPreferences

    override fun onCreate() {
        graph.inject(this)
        super.onCreate()

        val token = connectionPreferences.connectionToken(connectionManager.discord).get()
        if (token.isBlank()) {
            connectionPreferences.enableDiscordRPC.set(false)
            stopSelf()
            return
        }

        val status = when (connectionPreferences.discordRPCStatus.get()) {
            -1 -> "dnd"
            0 -> "idle"
            else -> "online"
        }

        try {
            rpc = if (token.isNotBlank()) DiscordRPC(token, status) else null
            try {
                discordScope.launchIO { setScreen(this@DiscordRPCService) }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to set initial screen" }
                stopSelf()
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to initialize discord rpc" }
            connectionPreferences.enableDiscordRPC.set(false)
            stopSelf()
        }
    }

    override fun onDestroy() {
        NotificationReceiver.dismissNotification(this, Notifications.ID_DISCORD_RPC)
        rpc?.closeRPC()
        rpc = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val token = connectionPreferences.connectionToken(connectionManager.discord).get()
        if (!connectionPreferences.enableDiscordRPC.get() || token.isBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        notification(this)

        when (intent?.action) {
            ACTION_RESTART -> restartRPC()
        }

        return START_STICKY
    }

    private fun restartRPC() {
        try {
            // Close existing RPC connection
            rpc?.closeRPC()
            rpc = null

            // Get fresh token and status
            val token = connectionPreferences.connectionToken(connectionManager.discord).get()
            if (token.isBlank()) {
                stopSelf()
                return
            }

            val status = when (connectionPreferences.discordRPCStatus.get()) {
                -1 -> "dnd"
                0 -> "idle"
                else -> "online"
            }

            // Reinitialize RPC
            rpc = DiscordRPC(token, status)
            discordScope.launchIO {
                setScreen(this@DiscordRPCService)
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to restart discord rpc" }
            stopSelf()
        }
    }

    private fun notification(context: Context) {
        val stopIntent = NotificationReceiver.stopDiscordRPCService(context)
        val builder = context.notificationBuilder(Notifications.CHANNEL_DISCORD_RPC) {
            setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
            setSmallIcon(R.drawable.ic_discord_24dp)
            setContentText(context.stringResource(AMMR.strings.pref_discord_rpc))
            setContentTitle(context.getString(R.string.app_name))
            addAction(R.drawable.ic_close_24dp, context.getString(R.string.action_stop), stopIntent)
            setAutoCancel(false)
            setOngoing(true)
            setUsesChronometer(true)
        }

        ServiceCompat.startForeground(
            this,
            Notifications.ID_DISCORD_RPC,
            builder.build(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    companion object {

        private var rpc: DiscordRPC? = null
        private val handler = Handler(Looper.getMainLooper())
        private val job = SupervisorJob()
        internal val discordScope = CoroutineScope(Dispatchers.IO + job)

        private const val ACTION_RESTART = "${BuildConfig.APPLICATION_ID}.DISCORD_RPC_RESTART"

        fun start(context: Context) {
            val connectionManager = context.appGraph.connectionManager
            val connectionPreferences = context.appGraph.connectionPreferences

            handler.removeCallbacksAndMessages(null)
            val token = connectionPreferences.connectionToken(connectionManager.discord).get()
            if (connectionPreferences.enableDiscordRPC.get()) {
                if (token.isBlank()) {
                    connectionPreferences.enableDiscordRPC.set(false)
                } else if (rpc == null) {
                    since = System.currentTimeMillis()
                    context.startForegroundService(Intent(context, DiscordRPCService::class.java))
                }
            } else {
                stop(context, delay = 0L)
            }
        }

        fun stop(context: Context, delay: Long = 30000L) {
            handler.removeCallbacksAndMessages(null)
            if (delay > 0) {
                handler.postDelayed({
                    try {
                    } catch (e: Exception) {
                        logcat(LogPriority.ERROR, e) { "Failed to stop discord rpc service" }
                    }
                }, delay)
            } else {
                try {
                    context.stopService(Intent(context, DiscordRPCService::class.java))
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Failed to stop discord rpc service" }
                }
            }
        }

        fun restart(context: Context) {
            val connectionManager = context.appGraph.connectionManager
            val connectionPreferences = context.appGraph.connectionPreferences

            val token = connectionPreferences.connectionToken(connectionManager.discord).get()
            if (connectionPreferences.enableDiscordRPC.get() && token.isNotBlank()) {
                val restartIntent = Intent(context, DiscordRPCService::class.java).apply {
                    action = ACTION_RESTART
                }
                try {
                    context.startForegroundService(restartIntent)
                } catch (_: Exception) {
                    // Fallback to stop/start if service isn't running
                    stop(context, 0L)
                    handler.postDelayed({ start(context) }, 1000L)
                }
            } else if (token.isBlank()) {
                connectionPreferences.enableDiscordRPC.set(false)
            }
        }

        private var since = 0L

        internal var lastUsedScreen = DiscordScreen.APP
            set(value) {
                // Only update if the new screen is not a media/webview screen
                if (value !in listOf(DiscordScreen.VIDEO, DiscordScreen.WEBVIEW)) {
                    field = value
                }
            }

        private const val MP_PREFIX = "mp:"
        private const val EXTERNAL_PREFIX = "external/"
        private val json = Json {
            encodeDefaults = true
            allowStructuredMapKeys = true
            ignoreUnknownKeys = true
        }

        private const val TAG = "DiscordRPCService"

        internal suspend fun setAnimeScreen(
            context: Context,
            discordScreen: DiscordScreen,
            playerData: PlayerData = PlayerData(),
        ) {
            if (discordScreen != DiscordScreen.VIDEO) return
            lastUsedScreen = discordScreen

            if (rpc == null) return
            updateDiscordRPC(context, playerData, discordScreen)
        }

        internal suspend fun setScreen(
            context: Context,
            discordScreen: DiscordScreen = lastUsedScreen,
            playerData: PlayerData = PlayerData(),
        ) {
            handler.removeCallbacksAndMessages(null)
            // if (PipState.mode == PipState.ON && discordScreen != DiscordScreen.VIDEO) return
            lastUsedScreen = discordScreen

            if (rpc == null) return

            val name = playerData.animeTitle ?: context.stringResource(MR.strings.app_name)
            val details = playerData.animeTitle ?: context.stringResource(discordScreen.details)
            val state = playerData.episodeNumber ?: context.stringResource(discordScreen.text)
            val imageUrl = playerData.thumbnailUrl ?: discordScreen.imageUrl

            rpc!!.updateRPC(
                activity = DiscordActivity(
                    name = name,
                    details = details,
                    state = state,
                    type = 3,
                    timestamps = DiscordActivity.Timestamps(start = since),
                    assets = DiscordActivity.Assets(
                        largeImage = "mp:$imageUrl",
                        smallImage = "mp:${DiscordScreen.APP.imageUrl}",
                        smallText = context.stringResource(DiscordScreen.APP.text),
                    ),
                ),
                since = since,
            )
        }

        private suspend fun updateDiscordRPC(
            context: Context,
            playerData: PlayerData,
            discordScreen: DiscordScreen,
            sinceTime: Long = since,
        ) {
            val connectionPreferences = context.appGraph.connectionPreferences
            val appName = context.stringResource(MR.strings.app_name)

            val customMessage = connectionPreferences.discordCustomMessage.get()
            val showProgress = connectionPreferences.discordShowProgress.get()
            val showTimestamp = connectionPreferences.discordShowTimestamp.get()
            val showButtons = connectionPreferences.discordShowButtons.get()
            val showDownloadButton = connectionPreferences.discordShowDownloadButton.get()
            val showDiscordButton = connectionPreferences.discordShowDiscordButton.get()

            val name = playerData.animeTitle ?: appName
            val details = when {
                customMessage.isNotBlank() -> customMessage
                playerData.animeTitle != null -> playerData.animeTitle
                else -> context.stringResource(discordScreen.details)
            }

            val state = when {
                playerData.paused == true -> context.stringResource(MR.strings.paused)
                !showProgress -> null
                playerData.episodeNumber != null -> playerData.episodeNumber
                else -> context.stringResource(discordScreen.text)
            }

            val imageUrl = playerData.thumbnailUrl ?: discordScreen.imageUrl

            val timestamps = if (showTimestamp) {
                DiscordActivity.Timestamps(
                    start = playerData.startTimestamp ?: since,
                    end = playerData.endTimestamp,
                )
            } else {
                null
            }

            val buttons = if (showButtons) {
                buildList {
                    if (showDownloadButton) add(DOWNLOAD_BUTTON_LABEL)
                    if (showDiscordButton) add(DISCORD_BUTTON_LABEL)
                }.takeIf { it.isNotEmpty() }
            } else {
                null
            }

            val metadata = buttons?.let {
                DiscordActivity.Metadata(
                    buttonUrls = buildList {
                        if (showDownloadButton) add(DOWNLOAD_BUTTON_URL)
                        if (showDiscordButton) add(DISCORD_BUTTON_URL)
                    },
                )
            }

            rpc!!.updateRPC(
                activity = DiscordActivity(
                    name = name,
                    details = details,
                    state = state,
                    type = 3,
                    timestamps = timestamps,
                    assets = DiscordActivity.Assets(
                        largeImage = "$MP_PREFIX$imageUrl",
                        smallImage = "$MP_PREFIX${DiscordScreen.APP.imageUrl}",
                        smallText = context.stringResource(DiscordScreen.APP.text),
                    ),
                    buttons = buttons,
                    metadata = metadata,
                ),
                since = sinceTime,
            )
        }

        internal suspend fun setPlayerActivity(
            context: Context,
            playerData: PlayerData = PlayerData(),
        ) {
            if (rpc == null || playerData.thumbnailUrl == null || playerData.animeId == null) return

            try {
                val categories = getCategories(context, playerData.animeId)
                val discordIncognito = isIncognito(context, categories, playerData.incognitoMode)

                val animeTitle = playerData.animeTitle.takeUnless { discordIncognito }
                val episodeNumber = getFormattedEpisodeNumber(context, playerData, discordIncognito)
                val (startTime, end) = getTimestamps(playerData)

                withIOContext {
                    val rpcExternalAsset = getRPCExternalAsset(context)
                    val animeThumbnail =
                        getDiscordThumbnail(rpcExternalAsset, playerData.thumbnailUrl, discordIncognito)

                    setAnimeScreen(
                        context = context,
                        discordScreen = DiscordScreen.VIDEO,
                        playerData = PlayerData(
                            paused = playerData.paused,
                            animeTitle = animeTitle,
                            episodeNumber = episodeNumber,
                            thumbnailUrl = animeThumbnail,
                            startTimestamp = startTime,
                            endTimestamp = end,
                        ),
                    )
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Error setting player activity" }
            }
        }

        // Helper functions

        private suspend fun getCategories(context: Context, id: Long?): List<String> {
            return context.appGraph.getCategories
                .await(id!!)
                .map { it.id.toString() }
                .run { ifEmpty { plus(UNCATEGORIZED_ID.toString()) } }
        }

        private fun isIncognito(context: Context, categories: List<String>, incognitoMode: Boolean): Boolean {
            val connectionPreferences = context.appGraph.connectionPreferences
            val discordIncognitoMode = connectionPreferences.discordRPCIncognito.get()
            val incognitoCategories = connectionPreferences.discordRPCIncognitoCategories.get()
            val incognitoCategory = categories.fastAny { it in incognitoCategories }
            return discordIncognitoMode || incognitoMode || incognitoCategory
        }

        private fun getFormattedEpisodeNumber(
            context: Context,
            playerData: PlayerData,
            discordIncognito: Boolean,
        ): String? {
            val connectionPreferences = context.appGraph.connectionPreferences
            return playerData.episodeNumber?.let {
                when {
                    discordIncognito -> null
                    connectionPreferences.discordShowEpisodeTitle.get() -> it
                    ceil(it.toDouble()) == floor(it.toDouble()) -> "Episode ${it.toInt()}"
                    else -> "Episode $it"
                }
            }
        }

        private fun getTimestamps(playerData: PlayerData): Pair<Long?, Long?> {
            val startTime = playerData.startTimestamp ?: System.currentTimeMillis()
            val end = playerData.endTimestamp
            return Pair(startTime, end)
        }

        private suspend fun getRPCExternalAsset(context: Context): DiscordRPCExternalAsset {
            val connectionManager = context.appGraph.connectionManager
            val networkService = context.appGraph.networkHelper
            val connectionPreferences = context.appGraph.connectionPreferences
            val client = networkService.client
            return DiscordRPCExternalAsset(
                applicationId = RICH_PRESENCE_APPLICATION_ID,
                token = connectionPreferences.connectionToken(connectionManager.discord).get(),
                client = client,
                json = json,
            )
        }
        private suspend fun getDiscordThumbnail(
            discordRpcExternalAsset: DiscordRPCExternalAsset,
            thumbnailUrl: String?,
            incognito: Boolean,
        ): String? {
            if (incognito || thumbnailUrl == null) return null

            return try {
                discordRpcExternalAsset.getDiscordUri(thumbnailUrl)
                    ?.takeIf { !it.contains("external/Not Found") }
                    ?.substringAfter("\"id\": \"")
                    ?.substringBefore("\"}")
                    ?.split(EXTERNAL_PREFIX)
                    ?.getOrNull(1)
                    ?.let { "$EXTERNAL_PREFIX$it" }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Error getting Discord URI" }
                null
            }
        }
    }
}
// <-- AM (DISCORD_RPC)
