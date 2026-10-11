package eu.kanade.tachiyomi.data.download

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.data.download.model.Download
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.runBlocking
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.anime.interactor.GetAnime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.episode.interactor.GetEpisode
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.source.local.io.Format
import tachiyomi.source.local.io.LocalSourceFileSystem
import tachiyomi.source.local.isLocal

/**
 * This class is used to manage episode downloads in the application. It must be instantiated once
 * and retrieved through dependency injection. You can use this class to queue new episodes or query
 * downloaded episodes.
 */
@Inject
@SingleIn(AppScope::class)
class DownloadManager(
    private val context: Context,
    // AM (STORAGE_SCREEN) -->
    private val storageManager: StorageManager,
    // <-- AM (STORAGE_SCREEN)
    private val provider: DownloadProvider,
    private val cache: DownloadCache,
    private val getCategories: GetCategories,
    private val getAnime: GetAnime,
    private val getEpisode: GetEpisode,
    private val sourceManager: SourceManager,
    private val downloadPreferences: DownloadPreferences,
    private val downloader: Downloader,
    private val pendingDeleter: DownloadPendingDeleter,
) {

    val isRunning: Boolean
        get() = downloader.isRunning

    val queueState
        get() = downloader.queueState

    // For use by DownloadService only
    fun downloaderStart() = downloader.start()
    fun downloaderStop(reason: String? = null) = downloader.stop(reason)

    val isDownloaderRunning
        get() = DownloadJob.isRunningFlow(context)

    /**
     * Tells the downloader to begin downloads.
     */
    fun startDownloads() {
        if (downloader.isRunning) return

        if (DownloadJob.isRunning(context)) {
            downloader.start()
        } else {
            DownloadJob.start(context)
        }
    }

    /**
     * Tells the downloader to pause downloads.
     */
    fun pauseDownloads() {
        downloader.stop()
    }

    /**
     * Empties the download queue.
     */
    fun clearQueue() {
        downloader.clearQueue()
        downloader.stop()
    }

    /**
     * Returns the download from queue if the episode is queued for download
     * else it will return null which means that the episode is not queued for download
     *
     * @param episodeId the episode to check.
     */
    fun getQueuedDownloadOrNull(episodeId: Long): Download? {
        return queueState.value.find { it.episode.id == episodeId }
    }

    fun startDownloadNow(episodeId: Long) {
        val existingDownload = getQueuedDownloadOrNull(episodeId)
        // If not in queue try to start a new download
        val toAdd = existingDownload ?: runBlocking { downloadFromEpisodeId(episodeId) } ?: return
        queueState.value.toMutableList().apply {
            existingDownload?.let { remove(it) }
            add(0, toAdd)
            reorderQueue(this)
        }
        startDownloads()
    }

    private suspend fun downloadFromEpisodeId(episodeId: Long): Download? {
        val episode = getEpisode.await(episodeId) ?: return null
        val anime = getAnime.await(episode.animeId) ?: return null
        val source = sourceManager.get(anime.source) as? AnimeHttpSource ?: return null

        return Download(source, anime, episode)
    }

    /**
     * Reorders the download queue.
     *
     * @param downloads value to set the download queue to
     */
    fun reorderQueue(downloads: List<Download>) {
        downloader.updateQueue(downloads)
    }

    /**
     * Tells the downloader to enqueue the given list of episodes.
     *
     * @param anime the anime of the episodes.
     * @param episodes the list of episodes to enqueue.
     * @param autoStart whether to start the downloader after enqueing the episodes.
     * @param alt whether to use the alternative downloader.
     * @param video the video to download, if available.
     */
    suspend fun downloadEpisodes(
        anime: Anime,
        episodes: List<Episode>,
        autoStart: Boolean = true,
        // AY -->
        alt: Boolean = false,
        video: Video? = null,
        // <-- AY
    ) {
        // AY -->
        val filteredEpisodes = getEpisodesToDownload(episodes)
        downloader.queueEpisodes(anime, filteredEpisodes, autoStart, alt, video)
        // <-- AY
    }

    /**
     * Tells the downloader to enqueue the given list of downloads at the start of the queue.
     *
     * @param downloads the list of downloads to enqueue.
     */
    fun addDownloadsToStartOfQueue(downloads: List<Download>) {
        if (downloads.isEmpty()) return
        queueState.value.toMutableList().apply {
            addAll(0, downloads)
            reorderQueue(this)
        }
        if (!DownloadJob.isRunning(context)) startDownloads()
    }

    /**
     * Builds the video of a downloaded episode.
     *
     * @param source the source of the episode.
     * @param anime the anime of the episode.
     * @param episode the downloaded episode.
     * @return the video from the episode.
     */
    fun buildVideo(source: AnimeSource, anime: Anime, episode: Episode): Video {
        // AM (CUSTOM_INFORMATION) -->
        val episodeDir = provider.findEpisodeDir(episode.name, episode.scanlator, episode.url, anime.ogTitle, source)
        // <-- AM (CUSTOM_INFORMATION)
        // AY -->
        val file = episodeDir?.listFiles().orEmpty()
            .firstOrNull { "video" in it.type.orEmpty() }
        // <-- AY

        if (file == null) {
            throw Exception(context.stringResource(AYMR.strings.video_list_empty_error))
        }

        // AY -->
        return Video(
            videoUrl = file.uri.toString(),
            videoTitle = "download: " + file.uri.toString(),
            initialized = true,
        ).apply { status = Video.State.READY }
        // <-- AY
    }

    /**
     * Returns true if the episode is downloaded.
     *
     * @param episodeName the name of the episode to query.
     * @param episodeScanlator scanlator of the episode to query
     * @param animeTitle the title of the anime to query.
     * @param sourceId the id of the source of the episode.
     */
    fun isEpisodeDownloaded(
        episodeName: String,
        episodeScanlator: String?,
        episodeUrl: String,
        animeTitle: String,
        sourceId: Long,
    ): Boolean {
        return cache.isEpisodeDownloaded(episodeName, episodeScanlator, episodeUrl, animeTitle, sourceId)
    }

    fun isEpisodeDownloadedOnDisk(
        episodeName: String,
        episodeScanlator: String?,
        episodeUrl: String,
        animeTitle: String,
        source: AnimeSource,
    ): Boolean {
        return provider.findEpisodeDir(episodeName, episodeScanlator, episodeUrl, animeTitle, source) != null
    }

    /**
     * Returns the amount of downloaded episodes.
     */
    fun getDownloadCount(): Int {
        return cache.getTotalDownloadCount()
    }

    /**
     * Returns the amount of downloaded/local episodes for an anime.
     *
     * @param anime the anime to check.
     */
    fun getDownloadCount(anime: Anime): Int {
        return if (anime.isLocal()) {
            // AM (STORAGE_SCREEN) -->
            LocalSourceFileSystem(storageManager).getFilesInAnimeDirectory(anime.url)
                .count { Format.isSupported(it) }
            // <-- AM (STORAGE_SCREEN)
        } else {
            cache.getDownloadCount(anime)
        }
    }

    // AM (STORAGE_SCREEN) -->

    /**
     * Returns the size of downloaded episodes.
     */
    fun getDownloadSize(): Long {
        return cache.getTotalDownloadSize()
    }

    /**
     * Returns the size of downloaded episodes for an anime.
     *
     * @param anime the anime to check.
     */
    fun getDownloadSize(anime: Anime): Long {
        return cache.getDownloadSize(anime)
    }
    // <-- AM (STORAGE_SCREEN)

    fun cancelQueuedDownloads(downloads: List<Download>) {
        removeFromDownloadQueue(downloads.map { it.episode })
    }

    /**
     * Deletes the directories of a list of downloaded episodes.
     *
     * @param episodes the list of episodes to delete.
     * @param anime the anime of the episodes.
     * @param source the source of the episodes.
     */
    fun deleteEpisodes(episodes: List<Episode>, anime: Anime, source: AnimeSource) {
        launchIO {
            val filteredEpisodes = getEpisodesToDelete(episodes, anime)
            if (filteredEpisodes.isEmpty()) {
                return@launchIO
            }

            removeFromDownloadQueue(filteredEpisodes)

            val (animeDir, episodeDirs) = provider.findEpisodeDirs(filteredEpisodes, anime, source)
            episodeDirs.forEach { it.delete() }
            cache.removeEpisodes(filteredEpisodes, anime)

            // Delete anime directory if empty
            if (animeDir?.listFiles()?.isEmpty() == true) {
                deleteAnime(anime, source, removeQueued = false)
            }
        }
    }

    /**
     * Deletes the directory of a downloaded anime.
     *
     * @param anime the anime to delete.
     * @param source the source of the anime.
     * @param removeQueued whether to also remove queued downloads.
     */
    fun deleteAnime(anime: Anime, source: AnimeSource, removeQueued: Boolean = true) {
        launchIO {
            if (removeQueued) {
                downloader.removeFromQueue(anime)
            }
            // AM (CUSTOM_INFORMATION) -->
            provider.findAnimeDir(anime.ogTitle, source)?.delete()
            // <-- AM (CUSTOM_INFORMATION)
            cache.removeAnime(anime)

            // Delete source directory if empty
            val sourceDir = provider.findSourceDir(source)
            if (sourceDir?.listFiles()?.isEmpty() == true) {
                sourceDir.delete()
                cache.removeSource(source)
            }
        }
    }

    private fun removeFromDownloadQueue(episodes: List<Episode>) {
        val wasRunning = downloader.isRunning
        if (wasRunning) {
            downloader.pause()
        }

        downloader.removeFromQueue(episodes)

        if (wasRunning) {
            if (queueState.value.isEmpty()) {
                downloader.stop()
            } else if (queueState.value.isNotEmpty()) {
                downloader.start()
            }
        }
    }

    /**
     * Adds a list of episodes to be deleted later.
     *
     * @param episodes the list of episodes to delete.
     * @param anime the anime of the episodes.
     */
    suspend fun enqueueEpisodesToDelete(episodes: List<Episode>, anime: Anime) {
        pendingDeleter.addEpisodes(getEpisodesToDelete(episodes, anime), anime)
    }

    /**
     * Triggers the execution of the deletion of pending episodes.
     */
    suspend fun deletePendingEpisodes() {
        val pendingEpisodes = pendingDeleter.getPendingEpisodes()
        for ((anime, episodes) in pendingEpisodes) {
            val source = sourceManager.get(anime.source) ?: continue
            deleteEpisodes(episodes, anime, source)
        }
    }

    /**
     * Renames source download folder
     *
     * @param oldSource the old source.
     * @param newSource the new source.
     */
    fun renameSource(oldSource: AnimeSource, newSource: AnimeSource) {
        val oldFolder = provider.findSourceDir(oldSource) ?: return
        val newName = provider.getSourceDirName(newSource)

        if (oldFolder.name == newName) return

        val capitalizationChanged = oldFolder.name.equals(newName, ignoreCase = true)
        if (capitalizationChanged) {
            val tempName = newName + Downloader.TMP_DIR_SUFFIX
            if (!oldFolder.renameTo(tempName)) {
                logcat(LogPriority.ERROR) { "Failed to rename source download folder: ${oldFolder.name}" }
                return
            }
        }

        if (!oldFolder.renameTo(newName)) {
            logcat(LogPriority.ERROR) { "Failed to rename source download folder: ${oldFolder.name}" }
        }
    }

    /**
     * Renames anime download folder
     *
     * @param anime the anime
     * @param newTitle the new anime title.
     */
    suspend fun renameAnime(anime: Anime, newTitle: String) {
        val source = sourceManager.getOrStub(anime.source)
        // AM (CUSTOM_INFORMATION) -->
        val oldFolder = provider.findAnimeDir(anime.ogTitle, source) ?: return
        // <-- AM (CUSTOM_INFORMATION)
        val newName = provider.getAnimeDirName(newTitle)

        if (oldFolder.name == newName) return

        // just to be safe, don't allow downloads for this anime while renaming it
        downloader.removeFromQueue(anime)

        val capitalizationChanged = oldFolder.name.equals(newName, ignoreCase = true)
        if (capitalizationChanged) {
            val tempName = newName + Downloader.TMP_DIR_SUFFIX
            if (!oldFolder.renameTo(tempName)) {
                logcat(LogPriority.ERROR) { "Failed to rename anime download folder: ${oldFolder.name}" }
                return
            }
        }

        if (oldFolder.renameTo(newName)) {
            cache.renameAnime(anime, oldFolder, newTitle)
        } else {
            logcat(LogPriority.ERROR) { "Failed to rename anime download folder: ${oldFolder.name}" }
        }
    }

    /**
     * Renames an already downloaded episode
     *
     * @param source the source of the anime.
     * @param anime the anime of the episode.
     * @param oldEpisode the existing episode with the old name.
     * @param newEpisode the target episode with the new name.
     */
    suspend fun renameEpisode(source: AnimeSource, anime: Anime, oldEpisode: Episode, newEpisode: Episode) {
        val oldNames = provider.getValidEpisodeDirNames(oldEpisode.name, oldEpisode.scanlator, oldEpisode.url)
        // AM (CUSTOM_INFORMATION) -->
        val animeDir = provider.getAnimeDir(anime.ogTitle, source).getOrElse { e ->
            logcat(LogPriority.ERROR, e) { "Anime download folder doesn't exist. Skipping renaming after source sync" }
            return
        }
        // <-- AM (CUSTOM_INFORMATION)

        // Assume there's only 1 version of the episode name formats present
        val oldDownload = oldNames.asSequence()
            .mapNotNull { animeDir.findFile(it) }
            .firstOrNull() ?: return

        val newName = provider.getEpisodeDirName(newEpisode.name, newEpisode.scanlator, newEpisode.url)
        if (oldDownload.name == newName) return

        if (oldDownload.renameTo(newName)) {
            cache.removeEpisode(oldEpisode, anime)
            cache.addEpisode(newName, animeDir, anime)
        } else {
            logcat(LogPriority.ERROR) { "Could not rename downloaded episode: ${oldNames.joinToString()}" }
        }
    }

    private suspend fun getEpisodesToDelete(episodes: List<Episode>, anime: Anime): List<Episode> {
        // Retrieve the categories that are set to exclude from being deleted on read
        val categoriesToExclude = downloadPreferences.removeExcludeCategories.get().map(String::toLong)

        val categoriesForAnime = getCategories.await(anime.id)
            .map { it.id }
            .ifEmpty { listOf(0) }
        val filteredCategoryAnime = if (categoriesForAnime.intersect(categoriesToExclude).isNotEmpty()) {
            episodes.filterNot { it.seen }
        } else {
            episodes
        }

        return if (!downloadPreferences.removeBookmarkedEpisodes.get()) {
            filteredCategoryAnime.filterNot { it.bookmark }
        } else {
            filteredCategoryAnime
        }
    }

    // AY -->
    private fun getEpisodesToDownload(episodes: List<Episode>): List<Episode> {
        return if (!downloadPreferences.downloadFillermarkedEpisodes.get()) {
            episodes.filterNot { it.fillermark }
        } else {
            episodes
        }
    }
    // <-- AY

    fun statusFlow(): Flow<Download> = queueState
        .flatMapLatest { downloads ->
            downloads
                .map { download ->
                    download.statusFlow.drop(1).map { download }
                }
                .merge()
        }
        .onStart {
            emitAll(
                queueState.value.filter { download -> download.status == Download.State.DOWNLOADING }.asFlow(),
            )
        }

    fun progressFlow(): Flow<Download> = queueState
        .flatMapLatest { downloads ->
            downloads
                .map { download ->
                    download.progressFlow.drop(1).map { download }
                }
                .merge()
        }
        .onStart {
            emitAll(
                queueState.value.filter { download -> download.status == Download.State.DOWNLOADING }
                    .asFlow(),
            )
        }
}
