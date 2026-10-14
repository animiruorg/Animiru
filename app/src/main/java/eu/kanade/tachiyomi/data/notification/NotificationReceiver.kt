package eu.kanade.tachiyomi.data.notification

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.backup.restore.BackupRestoreJob
import eu.kanade.tachiyomi.data.connection.discord.DiscordRPCService
import eu.kanade.tachiyomi.data.connection.syncmiru.SyncDataJob
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.player.PlayerActivity
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.getParcelableExtraCompat
import eu.kanade.tachiyomi.util.system.notificationManager
import eu.kanade.tachiyomi.util.system.toShareIntent
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.runBlocking
import mihon.app.di.appGraph
import tachiyomi.core.common.Constants
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.anime.interactor.GetAnime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.episode.interactor.GetEpisode
import tachiyomi.domain.episode.interactor.UpdateEpisode
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.episode.model.EpisodeUpdate
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.aniyomi.AYMR
import eu.kanade.tachiyomi.BuildConfig.APPLICATION_ID as ID

/**
 * Global [BroadcastReceiver] that runs on UI thread
 * Pending Broadcasts should be made from here.
 * NOTE: Use local broadcasts if possible.
 */
class NotificationReceiver : BroadcastReceiver() {

    @Inject private lateinit var getAnime: GetAnime

    @Inject private lateinit var getEpisode: GetEpisode

    @Inject private lateinit var updateEpisode: UpdateEpisode

    @Inject private lateinit var downloadManager: DownloadManager

    @Inject private lateinit var downloadPreferences: DownloadPreferences

    @Inject private lateinit var sourceManager: SourceManager

    override fun onReceive(context: Context, intent: Intent) {
        context.appGraph.inject(this)

        when (intent.action) {
            // Dismiss notification
            ACTION_DISMISS_NOTIFICATION -> dismissNotification(context, intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1))
            // Resume the download service
            ACTION_RESUME_DOWNLOADS -> downloadManager.startDownloads()
            // Pause the download service
            ACTION_PAUSE_DOWNLOADS -> downloadManager.pauseDownloads()
            // Clear the download queue
            ACTION_CLEAR_DOWNLOADS -> downloadManager.clearQueue()
            // Launch share activity and dismiss notification
            ACTION_SHARE_IMAGE ->
                shareImage(
                    context,
                    intent.getStringExtra(EXTRA_URI)!!.toUri(),
                )
            // Share backup file
            ACTION_SHARE_BACKUP ->
                shareFile(
                    context,
                    intent.getParcelableExtraCompat(EXTRA_URI)!!,
                    "application/x-protobuf+gzip",
                )
            ACTION_CANCEL_RESTORE -> cancelRestore(context)
            // AM (SYNC) -->
            ACTION_CANCEL_SYNC -> cancelSync(context)
            // <-- AM (SYNC)
            // AM (DISCORD_RPC) -->
            // Stop Discord RPC service
            ACTION_STOP_DISCORD_RPC -> stopDiscordRPC(context)
            // <-- AM (DISCORD_RPC)
            // Cancel library update and dismiss notification
            ACTION_CANCEL_LIBRARY_UPDATE -> cancelLibraryUpdate(context)
            // Open player activity
            ACTION_OPEN_EPISODE -> {
                openEpisode(
                    context,
                    intent.getLongExtra(EXTRA_ANIME_ID, -1),
                    intent.getLongExtra(EXTRA_EPISODE_ID, -1),
                )
            }
            // Mark updated anime episodes as seen
            ACTION_MARK_AS_SEEN -> {
                val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
                if (notificationId > -1) {
                    dismissNotification(context, notificationId, intent.getIntExtra(EXTRA_GROUP_ID, 0))
                }
                val urls = intent.getStringArrayExtra(EXTRA_EPISODE_URL) ?: return
                val animeId = intent.getLongExtra(EXTRA_ANIME_ID, -1)
                if (animeId > -1) {
                    markAsSeen(urls, animeId)
                }
            }
            // Download anime episodes
            ACTION_DOWNLOAD_EPISODE -> {
                val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
                if (notificationId > -1) {
                    dismissNotification(context, notificationId, intent.getIntExtra(EXTRA_GROUP_ID, 0))
                }
                val urls = intent.getStringArrayExtra(EXTRA_EPISODE_URL) ?: return
                val animeId = intent.getLongExtra(EXTRA_ANIME_ID, -1)
                if (animeId > -1) {
                    downloadEpisodes(urls, animeId)
                }
            }
        }
    }

    /**
     * Dismiss the notification
     *
     * @param notificationId the id of the notification
     */
    private fun dismissNotification(context: Context, notificationId: Int) {
        context.cancelNotification(notificationId)
    }

    /**
     * Called to start share intent to share image
     *
     * @param context context of application
     * @param uri path of file
     */
    private fun shareImage(context: Context, uri: Uri) {
        context.startActivity(uri.toShareIntent(context))
    }

    /**
     * Called to start share intent to share backup file
     *
     * @param context context of application
     * @param path path of file
     */
    private fun shareFile(context: Context, uri: Uri, fileMimeType: String) {
        context.startActivity(uri.toShareIntent(context, fileMimeType))
    }

    /**
     * Starts player activity
     *
     * @param context context of application
     * @param animeId id of anime
     * @param episodeId id of episode
     */
    private fun openEpisode(context: Context, animeId: Long, episodeId: Long) {
        val anime = runBlocking { getAnime.await(animeId) }
        val episode = runBlocking { getEpisode.await(episodeId) }
        if (anime != null && episode != null) {
            val intent = PlayerActivity.newIntent(context, anime.id, episode.id).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(intent)
        } else {
            context.toast(context.stringResource(AYMR.strings.download_error))
        }
    }

    /**
     * Method called when user wants to stop a backup restore job.
     *
     * @param context context of application
     */
    private fun cancelRestore(context: Context) {
        BackupRestoreJob.stop(context.workManager)
    }

    // AM (SYNC) -->
    private fun cancelSync(context: Context) {
        SyncDataJob.stop(context)
    }
    // <-- AM (SYNC)

    /**
     * Method called when user wants to stop a library update
     *
     * @param context context of application
     */
    private fun cancelLibraryUpdate(context: Context) {
        LibraryUpdateJob.stop(context)
    }

    /**
     * Method called when user wants to mark anime episodes as seen
     *
     * @param episodeUrls URLs of episode to mark as seen
     * @param animeId id of anime
     */
    private fun markAsSeen(episodeUrls: Array<String>, animeId: Long) {
        launchIO {
            val toUpdate = episodeUrls.mapNotNull { getEpisode.await(it, animeId) }
                .map {
                    if (downloadPreferences.removeAfterMarkedAsSeen.get()) {
                        val anime = getAnime.await(animeId)
                        if (anime != null) {
                            val source = sourceManager.get(anime.source)
                            if (source != null) {
                                downloadManager.deleteEpisodes(listOf(it), anime, source)
                            }
                        }
                    }
                    EpisodeUpdate(it.id) { seen = true }
                }
            updateEpisode.awaitAll(toUpdate)
        }
    }

    /**
     * Method called when user wants to download episodes
     *
     * @param episodeUrls URLs of episode to download
     * @param animeId id of anime
     */
    private fun downloadEpisodes(episodeUrls: Array<String>, animeId: Long) {
        launchIO {
            val anime = getAnime.await(animeId) ?: return@launchIO
            val episodes = episodeUrls.mapNotNull { getEpisode.await(it, animeId) }
            downloadManager.downloadEpisodes(anime, episodes)
        }
    }

    // AM (DISCORD_RPC) -->

    /**
     * Stop the Discord RPC service
     *
     * @param context context of application
     */
    private fun stopDiscordRPC(context: Context) {
        val serviceIntent = Intent(context, DiscordRPCService::class.java)
        context.stopService(serviceIntent)
        context.cancelNotification(Notifications.ID_DISCORD_RPC)
    }
    // <-- AM (DISCORD_RPC)

    companion object {
        private const val NAME = "NotificationReceiver"

        private const val ACTION_SHARE_IMAGE = "$ID.$NAME.SHARE_IMAGE"

        private const val ACTION_SHARE_BACKUP = "$ID.$NAME.SEND_BACKUP"

        private const val ACTION_CANCEL_RESTORE = "$ID.$NAME.CANCEL_RESTORE"

        // AM (SYNC) -->
        private const val ACTION_CANCEL_SYNC = "$ID.$NAME.CANCEL_SYNC"
        // <-- AM (SYNC)

        // AM (DISCORD_RPC) -->
        // Stop Discord RPC service
        private const val ACTION_STOP_DISCORD_RPC = "$ID.$NAME.STOP_DISCORD_RPC"
        // <-- AM (DISCORD_RPC)

        private const val ACTION_CANCEL_LIBRARY_UPDATE = "$ID.$NAME.CANCEL_LIBRARY_UPDATE"

        private const val ACTION_MARK_AS_SEEN = "$ID.$NAME.MARK_AS_SEEN"
        private const val ACTION_OPEN_EPISODE = "$ID.$NAME.ACTION_OPEN_EPISODE"
        private const val ACTION_DOWNLOAD_EPISODE = "$ID.$NAME.ACTION_DOWNLOAD_EPISODE"

        private const val ACTION_OPEN_ENTRY = "$ID.$NAME.ACTION_OPEN_ENTRY"

        private const val ACTION_RESUME_DOWNLOADS = "$ID.$NAME.ACTION_RESUME_DOWNLOADS"
        private const val ACTION_PAUSE_DOWNLOADS = "$ID.$NAME.ACTION_PAUSE_DOWNLOADS"
        private const val ACTION_CLEAR_DOWNLOADS = "$ID.$NAME.ACTION_CLEAR_DOWNLOADS"

        private const val ACTION_DISMISS_NOTIFICATION = "$ID.$NAME.ACTION_DISMISS_NOTIFICATION"

        private const val EXTRA_URI = "$ID.$NAME.URI"
        private const val EXTRA_NOTIFICATION_ID = "$ID.$NAME.NOTIFICATION_ID"
        private const val EXTRA_GROUP_ID = "$ID.$NAME.EXTRA_GROUP_ID"
        private const val EXTRA_ANIME_ID = "$ID.$NAME.EXTRA_ANIME_ID"
        private const val EXTRA_EPISODE_ID = "$ID.$NAME.EXTRA_EPISODE_ID"
        private const val EXTRA_EPISODE_URL = "$ID.$NAME.EXTRA_EPISODE_URL"

        /**
         * Returns a [PendingIntent] that resumes the download of an episode
         *
         * @param context context of application
         * @return [PendingIntent]
         */
        internal fun resumeDownloadsPendingBroadcast(context: Context): PendingIntent {
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                action = ACTION_RESUME_DOWNLOADS
            }
            return PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        /**
         * Returns [PendingIntent] that pauses the download queue
         *
         * @param context context of application
         * @return [PendingIntent]
         */
        internal fun pauseDownloadsPendingBroadcast(context: Context): PendingIntent {
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                action = ACTION_PAUSE_DOWNLOADS
            }
            return PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        /**
         * Returns a [PendingIntent] that clears the download queue
         *
         * @param context context of application
         * @return [PendingIntent]
         */
        internal fun clearDownloadsPendingBroadcast(context: Context): PendingIntent {
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                action = ACTION_CLEAR_DOWNLOADS
            }
            return PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        /**
         * Returns [PendingIntent] that starts a service which dismissed the notification
         *
         * @param context context of application
         * @param notificationId id of notification
         * @return [PendingIntent]
         */
        internal fun dismissNotificationPendingBroadcast(context: Context, notificationId: Int): PendingIntent {
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                action = ACTION_DISMISS_NOTIFICATION
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            }
            return PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        /**
         * Returns [PendingIntent] that starts a service which dismissed the notification
         *
         * @param context context of application
         * @param notificationId id of notification
         * @return [PendingIntent]
         */
        internal fun dismissNotification(context: Context, notificationId: Int, groupId: Int? = null) {
            /*
            Group notifications always have at least 2 notifications:
            - Group summary notification
            - Single anime notification

            If the single notification is dismissed by the system, ie by a user swipe or tapping on the notification,
            it will auto dismiss the group notification if there's no other single updates.

            When programmatically dismissing this notification, the group notification is not automatically dismissed.
             */
            val groupKey = context.notificationManager.activeNotifications.find {
                it.id == notificationId
            }?.groupKey

            if (groupId != null && groupId != 0 && !groupKey.isNullOrEmpty()) {
                val notifications = context.notificationManager.activeNotifications.filter {
                    it.groupKey == groupKey
                }

                if (notifications.size == 2) {
                    context.cancelNotification(groupId)
                    return
                }
            }

            context.cancelNotification(notificationId)
        }

        /**
         * Returns [PendingIntent] that starts a share activity
         *
         * @param context context of application
         * @param uri location path of file
         * @param notificationId id of notification
         * @return [PendingIntent]
         */
        internal fun shareImagePendingBroadcast(context: Context, uri: Uri): PendingIntent {
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                action = ACTION_SHARE_IMAGE
                putExtra(EXTRA_URI, uri.toString())
            }
            return PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        /**
         * Returns [PendingIntent] that starts a player activity containing episode.
         *
         * @param context context of application
         * @param anime anime of episode
         * @param episode episode that needs to be opened
         */
        internal fun openEpisodePendingActivity(context: Context, anime: Anime, episode: Episode): PendingIntent {
            val newIntent = PlayerActivity.newIntent(context, anime.id, episode.id)
            return PendingIntent.getActivity(
                context,
                anime.id.hashCode(),
                newIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        /**
         * Returns [PendingIntent] that opens the anime info controller.
         *
         * @param context context of application
         * @param anime anime of episode
         */
        internal fun openEpisodePendingActivity(context: Context, anime: Anime, groupId: Int): PendingIntent {
            val newIntent =
                Intent(context, MainActivity::class.java).setAction(Constants.SHORTCUT_ANIME)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(Constants.ANIME_EXTRA, anime.id)
                    .putExtra("notificationId", anime.id.hashCode())
                    .putExtra("groupId", groupId)
            return PendingIntent.getActivity(
                context,
                anime.id.hashCode(),
                newIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        /**
         * Returns [PendingIntent] that marks an episode as seen and deletes it if preferred
         *
         * @param context context of application
         * @param anime anime of episode
         */
        internal fun markAsSeenPendingBroadcast(
            context: Context,
            anime: Anime,
            episodes: Array<Episode>,
            groupId: Int,
        ): PendingIntent {
            val newIntent = Intent(context, NotificationReceiver::class.java).apply {
                action = ACTION_MARK_AS_SEEN
                putExtra(EXTRA_EPISODE_URL, episodes.map { it.url }.toTypedArray())
                putExtra(EXTRA_ANIME_ID, anime.id)
                putExtra(EXTRA_NOTIFICATION_ID, anime.id.hashCode())
                putExtra(EXTRA_GROUP_ID, groupId)
            }
            return PendingIntent.getBroadcast(
                context,
                anime.id.hashCode(),
                newIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        /**
         * Returns [PendingIntent] that downloads episodes
         *
         * @param context context of application
         * @param anime anime of episode
         */
        internal fun downloadEpisodesPendingBroadcast(
            context: Context,
            anime: Anime,
            episodes: Array<Episode>,
            groupId: Int,
        ): PendingIntent {
            val newIntent = Intent(context, NotificationReceiver::class.java).apply {
                action = ACTION_DOWNLOAD_EPISODE
                putExtra(EXTRA_EPISODE_URL, episodes.map { it.url }.toTypedArray())
                putExtra(EXTRA_ANIME_ID, anime.id)
                putExtra(EXTRA_NOTIFICATION_ID, anime.id.hashCode())
                putExtra(EXTRA_GROUP_ID, groupId)
            }
            return PendingIntent.getBroadcast(
                context,
                anime.id.hashCode(),
                newIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        /**
         * Returns [PendingIntent] that opens the anime info controller
         *
         * @param context context of application
         * @param animeId id of the entry to open
         */
        internal fun openEntryPendingActivity(context: Context, animeId: Long): PendingIntent {
            val newIntent = Intent(context, MainActivity::class.java).setAction(Constants.SHORTCUT_ANIME)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(Constants.ANIME_EXTRA, animeId)
                .putExtra("notificationId", animeId.hashCode())

            return PendingIntent.getActivity(
                context,
                animeId.hashCode(),
                newIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        /**
         * Returns [PendingIntent] that starts a service which stops the library update
         *
         * @param context context of application
         * @return [PendingIntent]
         */
        internal fun cancelLibraryUpdatePendingBroadcast(context: Context): PendingIntent {
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                action = ACTION_CANCEL_LIBRARY_UPDATE
            }
            return PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        /**
         * Returns [PendingIntent] that opens the extensions controller.
         *
         * @param context context of application
         * @return [PendingIntent]
         */
        internal fun openExtensionsPendingActivity(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                action = Constants.SHORTCUT_EXTENSIONS
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            return PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        /**
         * Returns [PendingIntent] that directly launches a share activity for a backup file.
         *
         * @param context context of application
         * @param uri uri of backup file
         * @return [PendingIntent]
         */
        internal fun shareBackupPendingActivity(context: Context, uri: Uri): PendingIntent {
            val intent = uri.toShareIntent(context, "application/x-protobuf+gzip").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            return PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        /**
         * Returns [PendingIntent] that opens the error log file in an external viewer
         *
         * @param context context of application
         * @param uri uri of error log file
         * @return [PendingIntent]
         */
        internal fun openErrorLogPendingActivity(context: Context, uri: Uri): PendingIntent {
            val intent = Intent().apply {
                action = Intent.ACTION_VIEW
                setDataAndType(uri, "text/plain")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        }

        /**
         * Returns [PendingIntent] that cancels a backup restore job.
         *
         * @param context context of application
         * @param notificationId id of notification
         * @return [PendingIntent]
         */
        internal fun cancelRestorePendingBroadcast(context: Context, notificationId: Int): PendingIntent {
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                action = ACTION_CANCEL_RESTORE
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            }
            return PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        // AM (SYNC) -->
        internal fun cancelSyncPendingBroadcast(context: Context, notificationId: Int): PendingIntent {
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                action = ACTION_CANCEL_SYNC
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            }
            return PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        // <-- AM (SYNC)

        // AM (DISCORD_RPC) -->
        internal fun stopDiscordRPCService(context: Context): PendingIntent {
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                action = ACTION_STOP_DISCORD_RPC
            }
            return PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        // <-- AM (DISCORD_RPC)
    }
}
