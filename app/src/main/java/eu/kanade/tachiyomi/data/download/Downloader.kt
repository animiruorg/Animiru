package eu.kanade.tachiyomi.data.download

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.net.toUri
import aniyomi.core.common.torrent.TorrentPreferences
import aniyomi.core.common.torrent.TorrentServerApi
import aniyomi.core.common.torrent.TorrentServerUtils
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.Level
import com.arthenica.ffmpegkit.LogCallback
import com.arthenica.ffmpegkit.StatisticsCallback
import com.hippo.unifile.UniFile
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.animesource.UnmeteredSource
import eu.kanade.tachiyomi.animesource.model.HttpServer
import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.data.download.downloader.VideoDownloader
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.library.LibraryUpdateNotifier
import eu.kanade.tachiyomi.data.notification.NotificationHandler
import eu.kanade.tachiyomi.data.torrent.service.TorrentServerService
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.player.loader.EpisodeLoader
import eu.kanade.tachiyomi.ui.player.loader.HosterLoader
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.storage.toFFmpegString
import eu.kanade.tachiyomi.util.system.copyToClipboard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.suspendCancellableCoroutine
import logcat.LogPriority
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.storage.extension
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNow
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.createFileInCacheDir
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.animiru.AMMR
import tachiyomi.i18n.aniyomi.AYMR
import java.io.BufferedReader
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * This class is the one in charge of downloading episodes.
 *
 * Its queue contains the list of episodes to download.
 */
@Inject
@SingleIn(AppScope::class)
class Downloader(
    private val context: Context,
    private val provider: DownloadProvider,
    private val cache: DownloadCache,
    private val sourceManager: SourceManager,
    private val downloadPreferences: DownloadPreferences,
    private val store: DownloadStore,
    private val notifier: DownloadNotifier,
    // AY -->
    private val episodeLoader: Lazy<EpisodeLoader>,
    private val hosterLoader: Lazy<HosterLoader>,
    private val networkService: NetworkHelper,
    private val torrentServerApi: TorrentServerApi,
    private val torrentServerUtils: TorrentServerUtils,
    private val torrentPreferences: TorrentPreferences,
    // <-- AY
    // AM -->
    private val videoDownloader: VideoDownloader,
    // <-- AM
) {

    /**
     * Queue where active downloads are kept.
     */
    private val _queueState = MutableStateFlow<List<Download>>(emptyList())
    val queueState = _queueState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloaderJob: Job? = null

    /**
     * Whether the downloader is running.
     */
    val isRunning: Boolean
        get() = downloaderJob?.isActive ?: false

    // AM -->
    val client: OkHttpClient
        get() = networkService.client
    // <-- AM

    /**
     * Whether the downloader is paused
     */
    @Volatile
    var isPaused: Boolean = false

    init {
        launchNow {
            val episodes = async { store.restore() }
            addAllToQueue(episodes.await())
        }
    }

    /**
     * Starts the downloader. It doesn't do anything if it's already running or there isn't anything
     * to download.
     *
     * @return true if the downloader is started, false otherwise.
     */
    fun start(): Boolean {
        if (isRunning || queueState.value.isEmpty()) {
            return false
        }

        val pending = queueState.value.filter { it.status != Download.State.DOWNLOADED }
        pending.forEach { if (it.status != Download.State.QUEUE) it.status = Download.State.QUEUE }

        isPaused = false

        launchDownloaderJob()

        return pending.isNotEmpty()
    }

    /**
     * Stops the downloader.
     */
    fun stop(reason: String? = null) {
        cancelDownloaderJob()
        queueState.value
            .filter { it.status == Download.State.DOWNLOADING }
            .forEach { it.status = Download.State.ERROR }

        if (reason != null) {
            notifier.onWarning(reason)
            return
        }

        if (isPaused && queueState.value.isNotEmpty()) {
            notifier.onPaused()
        } else {
            notifier.onComplete()
        }

        isPaused = false

        DownloadJob.stop(context)
    }

    /**
     * Pauses the downloader
     */
    fun pause() {
        cancelDownloaderJob()
        queueState.value
            .filter { it.status == Download.State.DOWNLOADING }
            .forEach { it.status = Download.State.QUEUE }
        isPaused = true
    }

    /**
     * Removes everything from the queue.
     */
    fun clearQueue() {
        cancelDownloaderJob()

        internalClearQueue()
        notifier.dismissProgress()
    }

    /**
     * Prepares the subscriptions to start downloading.
     */
    private fun launchDownloaderJob() {
        if (isRunning) return

        downloaderJob = scope.launch {
            val activeDownloadsFlow = combine(
                queueState,
                downloadPreferences.parallelSourceLimit.changes(),
            ) { a, b -> a to b }.transformLatest { (queue, parallelCount) ->
                while (true) {
                    val activeDownloads = queue.asSequence()
                        // Ignore completed downloads, leave them in the queue
                        .filter { it.status.value <= Download.State.DOWNLOADING.value }
                        .groupBy { it.source }
                        .toList()
                        .take(parallelCount)
                        .map { (_, downloads) -> downloads.first() }
                    emit(activeDownloads)

                    if (activeDownloads.isEmpty()) break
                    // Suspend until a download enters the ERROR state
                    val activeDownloadsErroredFlow =
                        combine(activeDownloads.map(Download::statusFlow)) { states ->
                            states.contains(Download.State.ERROR)
                        }.filter { it }
                    activeDownloadsErroredFlow.first()
                }
            }
                .distinctUntilChanged()

            // Use supervisorScope to cancel child jobs when the downloader job is cancelled
            supervisorScope {
                val downloadJobs = mutableMapOf<Download, Job>()

                activeDownloadsFlow.collectLatest { activeDownloads ->
                    val downloadJobsToStop = downloadJobs.filter { it.key !in activeDownloads }
                    downloadJobsToStop.forEach { (download, job) ->
                        job.cancel()
                        downloadJobs.remove(download)
                    }

                    val downloadsToStart = activeDownloads.filter { it !in downloadJobs }
                    downloadsToStart.forEach { download ->
                        downloadJobs[download] = launchDownloadJob(download)
                    }
                }
            }
        }
    }

    /**
     * Launch the job responsible for downloading a single video
     */
    private fun CoroutineScope.launchDownloadJob(download: Download) = launchIO {
        try {
            downloadEpisode(download)

            // Remove successful download from queue
            if (download.status == Download.State.DOWNLOADED) {
                removeFromQueue(download)
            }
            if (areAllDownloadsFinished()) {
                stop()
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            logcat(LogPriority.ERROR, e)
            notifier.onError(e.message)
            stop()
        }
    }

    /**
     * Destroys the downloader subscriptions.
     */
    private fun cancelDownloaderJob() {
        downloaderJob?.cancel()
        downloaderJob = null
    }

    /**
     * Creates a download object for every episode and adds them to the downloads queue.
     *
     * @param anime the anime of the episodes to download.
     * @param episodes the list of episodes to download.
     * @param autoStart whether to start the downloader after enqueing the episodes.
     */
    fun queueEpisodes(
        anime: Anime,
        episodes: List<Episode>,
        autoStart: Boolean,
        // AY -->
        changeDownloader: Boolean = false,
        video: Video? = null,
        // <-- AY
    ) {
        if (episodes.isEmpty()) return

        val source = sourceManager.get(anime.source) as? AnimeHttpSource ?: return
        val wasEmpty = queueState.value.isEmpty()
        val episodesToQueue = episodes.asSequence()
            // Filter out those already downloaded.
            // AM (CUSTOM_INFORMATION) -->
            .filter { provider.findEpisodeDir(it.name, it.scanlator, it.url, anime.ogTitle, source) == null }
            // <-- AM (CUSTOM_INFORMATION)
            // Add episodes to queue from the start.
            .sortedByDescending { it.sourceOrder }
            // Filter out those already enqueued.
            .filter { episode -> queueState.value.none { it.episode.id == episode.id } }
            // Create a download for each one.
            .map { Download(source, anime, it, changeDownloader, video) }
            .toList()

        if (episodesToQueue.isNotEmpty()) {
            addAllToQueue(episodesToQueue)

            // Start downloader if needed
            if (autoStart && wasEmpty) {
                val queuedDownloads = queueState.value.count { it.source !is UnmeteredSource }
                val maxDownloadsFromSource = queueState.value
                    .groupBy { it.source }
                    .filterKeys { it !is UnmeteredSource }
                    .maxOfOrNull { it.value.size }
                    ?: 0
                if (
                    queuedDownloads > DOWNLOADS_QUEUED_WARNING_THRESHOLD ||
                    maxDownloadsFromSource > EPISODES_PER_SOURCE_QUEUE_WARNING_THRESHOLD
                ) {
                    notifier.onWarning(
                        context.stringResource(
                            MR.strings.download_queue_size_warning,
                            context.stringResource(MR.strings.app_name),
                        ),
                        WARNING_NOTIF_TIMEOUT_MS,
                        NotificationHandler.openUrl(context, LibraryUpdateNotifier.HELP_WARNING_URL),
                    )
                }
                DownloadJob.start(context)
            }
        }
    }

    /**
     * Downloads an episode.
     *
     * @param download the episode to be downloaded.
     */
    private suspend fun downloadEpisode(download: Download) {
        // AM (CUSTOM_INFORMATION) -->
        val animeDir = provider.getAnimeDir(download.anime.ogTitle, download.source).getOrElse { e ->
            download.status = Download.State.ERROR
            notifier.onError(e.message, download.episode.name, download.anime.title, download.anime.id)
            return
        }
        // <-- AM (CUSTOM_INFORMATION)

        val availSpace = DiskUtil.getAvailableStorageSpace(animeDir)
        if (availSpace != -1L && availSpace < MIN_DISK_SPACE) {
            download.status = Download.State.ERROR
            notifier.onError(
                context.stringResource(AMMR.strings.am_download_insufficient_space),
                download.episode.name,
                download.anime.title,
                download.anime.id,
            )
            return
        }

        val episodeDirname = provider.getEpisodeDirName(
            download.episode.name,
            download.episode.scanlator,
            download.episode.url,
        )
        val tmpDir = animeDir.createDirectory(episodeDirname + TMP_DIR_SUFFIX)!!

        try {
            // AY -->
            if (download.video == null) {
                // Pull video from network and add them to download object
                val hosters = episodeLoader.value.getHosters(download.episode, download.anime, download.source)
                if (hosters.isEmpty()) {
                    throw Exception(context.stringResource(AYMR.strings.video_list_empty_error))
                }
                val bestVideo = hosterLoader.value.getBestVideo(download.source, hosters)
                    ?: throw Exception(context.stringResource(AYMR.strings.video_list_empty_error))
                download.video = bestVideo
            }

            withIOContext {
                getOrDownloadVideoFile(download, tmpDir)
            }
            // <-- AY

            // Do after download completes

            if (!isDownloadSuccessful(download, tmpDir)) {
                download.status = Download.State.ERROR
                return
            }

            // AM (CUSTOM_INFORMATION) -->
            val filename = DiskUtil.buildValidFilename("${download.anime.ogTitle} - ${download.episode.name}")
            // <-- AM (CUSTOM_INFORMATION)
            // AY -->
            tmpDir.findFile("${filename}_tmp.mkv")?.delete()
            tmpDir.renameTo(episodeDirname)
            // <-- AY

            cache.addEpisode(episodeDirname, animeDir, download.anime)

            DiskUtil.createNoMediaFile(tmpDir, context)

            download.status = Download.State.DOWNLOADED
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            // If the video threw, it will resume here
            logcat(LogPriority.ERROR, error)
            download.status = Download.State.ERROR
            notifier.onError(error.message, download.episode.name, download.anime.title, download.anime.id)
        }
    }

    // AY -->

    /**
     * Gets the video file if already downloaded, otherwise downloads it
     *
     * @param download the download of the video.
     * @param tmpDir the temporary directory of the download.
     */
    private suspend fun getOrDownloadVideoFile(
        download: Download,
        tmpDir: UniFile,
    ) {
        val video = download.video!!

        video.status = Video.State.LOAD_VIDEO

        var progressJob: Job? = null
        var httpServer: HttpServer? = null

        // Get filename from download info
        val filename = DiskUtil.buildValidFilename(download.episode.name)

        // Delete temp file if it exists
        tmpDir.findFile("$filename.tmp")?.delete()

        // Try to find the video file
        val videoFile = tmpDir.listFiles()?.firstOrNull { it.name!!.startsWith("$filename.mkv") }

        try {
            // If the video is already downloaded, do nothing. Otherwise download from network
            val file = when {
                videoFile != null -> videoFile
                else -> {
                    notifier.onProgressChange(download)

                    download.status = Download.State.DOWNLOADING
                    download.progress = 0

                    // If videoFile is not existing then download it
                    if (downloadPreferences.useExternalDownloader.get() == download.changeDownloader) {
                        progressJob = scope.launch {
                            while (download.status == Download.State.DOWNLOADING) {
                                delay(50.milliseconds)
                                notifier.onProgressChange(download)
                            }
                        }

                        // Start and set http server if needed
                        if (video.usesHttpServer()) {
                            httpServer = download.source.createHttpServer()
                            httpServer?.start()
                            download.video = download.video?.copyHttpServer(httpServer?.listeningPort ?: 0)
                        }

                        if (downloadPreferences.useInternalDownloader.get()) {
                            videoDownloader.download(download, tmpDir, filename)
                        } else {
                            downloadVideo(download, tmpDir, filename)
                        }
                    } else {
                        if (download.video!!.usesHttpServer()) {
                            val (success, port) = MainActivity.startHttpServerService(context, download.source.id)
                            if (!success) throw Exception("Failed to start server")
                            download.video = download.video!!.copyHttpServer(port)
                        }

                        val betterFileName = DiskUtil.buildValidFilename(
                            // AM (CUSTOM_INFORMATION) -->
                            "${download.anime.ogTitle} - ${download.episode.name}",
                            // <-- AM (CUSTOM_INFORMATION)
                        )
                        downloadVideoExternal(
                            video = download.video!!,
                            source = download.source,
                            tmpDir = tmpDir,
                            filename = betterFileName,
                        )
                    }
                }
            }

            video.videoUrl = file.uri.path ?: ""
            download.progress = 100
            video.status = Video.State.READY
            httpServer?.stop()
            progressJob?.cancel()
        } catch (e: Exception) {
            httpServer?.stop()
            if (e is CancellationException) throw e
            video.status = Video.State.ERROR
            notifier.onError(e.message, download.episode.name, download.anime.title, download.anime.id)
            progressJob?.cancel()
        }
    }

    /**
     * Define a retry routine in order to accommodate some errors that can be raised
     *
     * @param download the download reference
     * @param tmpDir the directory where placing the file
     * @param filename the name to give to download file
     */
    private suspend fun downloadVideo(
        download: Download,
        tmpDir: UniFile,
        filename: String,
    ): UniFile {
        return flow {
            tmpDir.findFile("$filename.tmp")?.delete()
            val videoFile = tmpDir.createFile("$filename.tmp")!!
            try {
                if (torrentPreferences.torrServerEnable.get() && isTorrent(download.video)) {
                    torrentDownload(download, tmpDir, videoFile, filename)
                } else {
                    ffmpegDownload(download, tmpDir, videoFile, filename)
                }
            } catch (e: Exception) {
                videoFile.delete()
                throw e
            }

            emit(videoFile)
        }
            // Retry 3 times, waiting 2, 4 and 8 seconds between attempts.
            .retryWhen { _, attempt ->
                if (attempt < 3) {
                    delay((2L shl attempt.toInt()).seconds)
                    true
                } else {
                    false
                }
            }
            .flowOn(Dispatchers.IO)
            .first()
    }

    private fun isTorrent(video: Video?): Boolean {
        val url = video?.videoUrl ?: return false
        return url.startsWith("magnet") || url.endsWith(".torrent") || url.startsWith(torrentServerApi.hostUrl)
    }

    private suspend fun torrentDownload(
        download: Download,
        tmpDir: UniFile,
        videoFile: UniFile,
        filename: String,
    ) {
        val video = download.video!!
        TorrentServerService.start(context)
        if (video.videoUrl.startsWith(torrentServerApi.hostUrl)) {
            val hash = video.videoUrl.substringAfter("link=").substringBefore("&")
            val index = video.videoUrl.substringAfter("index=").substringBefore("&").toInt()
            val magnet = "magnet:?xt=urn:btih:$hash&index=$index"
            video.videoUrl = magnet
        }
        val currentTorrent = torrentServerApi.addTorrent(video.videoUrl, video.videoTitle, "", "", false)
        var index = 0
        if (video.videoUrl.contains("index=")) {
            index = try {
                video.videoUrl.substringAfter("index=")
                    .substringBefore("&").toInt()
            } catch (_: Exception) {
                0
            }
        }
        val torrentUrl = torrentServerUtils.getTorrentPlayLink(currentTorrent, index)
        video.videoUrl = torrentUrl
        ffmpegDownload(download, tmpDir, videoFile, filename)
    }

    // ffmpeg is always on safe mode
    private suspend fun ffmpegDownload(
        download: Download,
        tmpDir: UniFile,
        videoFile: UniFile,
        filename: String,
    ) {
        val video = download.video!!

        val ffmpegFilename = { videoFile.uri.toFFmpegString(context) }

        val headers = video.headers ?: download.source.headers
        val headerOptions = headers.joinToString("", "-headers '", "'") {
            "${it.first}: ${it.second}\r\n"
        }

        val ffmpegOptions = getFFmpegOptions(video, headers, headerOptions, ffmpegFilename())
        val duration = getDuration(video.videoUrl, headerOptions)?.toLong() ?: 0L

        val logCallback = LogCallback { log ->
            if (log.level <= Level.AV_LOG_WARNING) {
                log.message?.let {
                    logcat(LogPriority.ERROR) { it }
                }
            }
        }

        val statCallback = StatisticsCallback { s ->
            val outTime = (s.time / 1000.0).toLong()

            if (duration != 0L && outTime > 0) {
                download.progress = (100 * outTime / duration).toInt()
            }
        }

        suspendCancellableCoroutine { continuation ->
            val session = FFmpegKit.executeWithArgumentsAsync(
                ffmpegOptions,
                {
                    if (it.returnCode.isValueSuccess) {
                        tmpDir.findFile("$filename.tmp")?.apply {
                            renameTo("$filename.mkv")
                        }
                        continuation.resume(it)
                    } else {
                        continuation.resumeWithException(Exception("Error in ffmpeg!"))
                    }
                },
                logCallback,
                statCallback,
            )
            continuation.invokeOnCancellation {
                session.cancel()
            }
        }
    }

    private suspend fun getFFmpegOptions(
        video: Video,
        // AM -->
        headers: Headers,
        // <-- AM
        headerOptions: String,
        ffmpegFilename: String,
    ): Array<String> {
        fun formatInputs(tracks: List<Track>) = tracks.joinToString(" ", postfix = " ") {
            buildList {
                if (it.url.startsWith("http")) {
                    add(headerOptions)
                }
                add("-i")
                add("\"${it.url}\"")
            }.joinToString(" ")
        }

        fun formatMaps(tracks: List<Track>, type: String, offset: Int = 0) = tracks.indices.joinToString(" ") {
            "-map ${it + 1 + offset}:$type"
        }

        fun formatMetadata(tracks: List<Track>, type: String) = tracks.mapIndexed { i, track ->
            "-metadata:s:$type:$i \"title=${track.lang}\""
        }.joinToString(" ")

        // AM -->
        val subtitleTracks = filterTracks(video.subtitleTracks, headers)
        // <-- AM
        val subtitleInputs = formatInputs(subtitleTracks)
        val subtitleMaps = formatMaps(subtitleTracks, "s")
        val subtitleMetadata = formatMetadata(subtitleTracks, "s")

        // AM -->
        val audioTracks = filterTracks(video.audioTracks, headers)
        // <-- AM
        val audioInputs = formatInputs(audioTracks)
        val audioMaps = formatMaps(audioTracks, "a", subtitleTracks.size)
        val audioMetadata = formatMetadata(audioTracks, "a")

        val sourceStreamOptions = video.ffmpegStreamArgs.joinToString(" ") { (key, value) ->
            "-$key \"$value\""
        }
        val sourceVideoOptions = video.ffmpegVideoArgs.joinToString(" ") { (key, value) ->
            "-$key \"$value\""
        }

        val videoInput = buildList {
            if (video.videoUrl.startsWith("http")) {
                add(headerOptions)
            }
            add(sourceStreamOptions)
            add("-i")
            add("\"${video.videoUrl}\"")
        }.joinToString(" ")

        val command = listOf(
            videoInput, subtitleInputs, audioInputs,
            "-map 0:v", audioMaps, "-map 0:a?", subtitleMaps, "-map 0:s? -map 0:t?",
            "-f matroska -c:a copy -c:v copy -c:s copy",
            subtitleMetadata, audioMetadata, sourceVideoOptions,
            "\"$ffmpegFilename\" -y",
        )
            .filter(String::isNotBlank)
            .joinToString(" ")

        return FFmpegKitConfig.parseArguments(command)
    }

    // AM -->
    private suspend fun filterTracks(tracks: List<Track>, headers: Headers): List<Track> {
        if (!downloadPreferences.ignoreBrokenTracks.get()) return tracks

        return tracks.mapNotNull { track ->
            try {
                val request = Request.Builder()
                    .url(track.url)
                    .headers(headers)
                    .head()
                    .build()
                client.newCall(request).awaitSuccess()
                track
            } catch (_: Exception) {
                null
            }
        }
    }
    // <-- AM

    private suspend fun getDuration(videoUrl: String, headerOptions: String): Float? {
        val durationFile = context.createFileInCacheDir("ffprobe_duration.txt")
        val durationFilePath = durationFile.toUri().toFFmpegString(context)

        val ffprobeCommand = FFmpegKitConfig.parseArguments(
            listOf(
                headerOptions,
                "-v quiet -show_entries format=duration -of default=noprint_wrappers=1:nokey=1",
                "-o \"$durationFilePath\"",
                "\"$videoUrl\"",
            ).joinToString(" "),
        )

        suspendCancellableCoroutine { continuation ->
            val session = FFprobeKit.executeWithArgumentsAsync(ffprobeCommand) {
                if (it.returnCode.isValueSuccess) {
                    continuation.resume(it)
                } else {
                    continuation.resumeWithException(Exception(it.output))
                }
            }
            continuation.invokeOnCancellation { session.cancel() }
        }

        return durationFile.bufferedReader().use(BufferedReader::readText).trim().toFloatOrNull()
    }

    /**
     * Returns the observable which downloads the video with an external downloader.
     *
     * @param video the video to download.
     * @param source the source of the video.
     * @param tmpDir the temporary directory of the download.
     * @param filename the filename of the video.
     */
    private suspend fun downloadVideoExternal(
        video: Video,
        source: AnimeHttpSource,
        tmpDir: UniFile,
        filename: String,
    ): UniFile {
        try {
            val file = tmpDir.createFile("${filename}_tmp.mkv")!!
            withUIContext {
                context.copyToClipboard("Episode download location", tmpDir.filePath!!.substringBeforeLast("_tmp"))
            }

            // TODO: support other file formats!!
            // start download with intent
            val pm = context.packageManager
            val pkgName = downloadPreferences.externalDownloaderSelection.get()
            val intent: Intent
            if (pkgName.isNotEmpty()) {
                intent = pm.getLaunchIntentForPackage(pkgName) ?: throw Exception(
                    "Launch intent not found",
                )
                when {
                    // 1DM
                    pkgName.startsWith("idm.internet.download.manager") -> {
                        val headers = (video.headers ?: source.headers).toMap()
                        val bundle = Bundle()
                        for ((key, value) in headers) {
                            bundle.putString(key, value)
                        }

                        intent.apply {
                            component = ComponentName(
                                pkgName,
                                "idm.internet.download.manager.Downloader",
                            )
                            action = Intent.ACTION_VIEW
                            data = video.videoUrl.toUri()

                            putExtra("extra_filename", "$filename.mkv")
                            putExtra("extra_headers", bundle)
                        }
                    }
                    // ADM
                    pkgName.startsWith("com.dv.adm") -> {
                        val headers = (video.headers ?: source.headers).toList()
                        val bundle = Bundle()
                        headers.forEach { a ->
                            bundle.putString(
                                a.first,
                                a.second.replace("http", "h_ttp"),
                            )
                        }

                        intent.apply {
                            component = ComponentName(pkgName, "$pkgName.AEditor")
                            action = Intent.ACTION_VIEW
                            putExtra(
                                "com.dv.get.ACTION_LIST_ADD",
                                "${video.videoUrl.toUri()}<info>$filename.mkv",
                            )
                            putExtra(
                                "com.dv.get.ACTION_LIST_PATH",
                                tmpDir.filePath!!.substringBeforeLast("_"),
                            )
                            putExtra("android.media.intent.extra.HTTP_HEADERS", bundle)
                        }
                        file.delete()
                        tmpDir.delete()
                        queueState.value.find { anime -> anime.video == video }?.let { download ->
                            download.status = Download.State.DOWNLOADED
                            // Delete successful downloads from queue
                            if (download.status == Download.State.DOWNLOADED) {
                                // Remove downloaded episode from queue
                                removeFromQueue(download)
                            }
                            if (areAllDownloadsFinished()) {
                                stop()
                            }
                        }
                    }
                }
            } else {
                intent = Intent(Intent.ACTION_VIEW).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    setDataAndType(video.videoUrl.toUri(), "video/*")
                    putExtra("extra_filename", filename)
                }
            }
            context.startActivity(intent)
            return file
        } catch (e: Exception) {
            tmpDir.findFile("${filename}_tmp.mkv")?.delete()
            throw e
        }
    }

    /**
     * Checks if the download was successful.
     *
     * @param download the download to check.
     * @param tmpDir the directory where the download is currently stored.
     */
    private fun isDownloadSuccessful(
        download: Download,
        tmpDir: UniFile,
    ): Boolean {
        val downloadedVideo = tmpDir.listFiles().orEmpty().filterNot { it.extension == ".tmp" }
        return downloadedVideo.size == 1
    }
    // <-- AY

    /**
     * Returns true if all the queued downloads are in DOWNLOADED or ERROR state.
     */
    private fun areAllDownloadsFinished(): Boolean {
        return queueState.value.none { it.status.value <= Download.State.DOWNLOADING.value }
    }

    private fun addAllToQueue(downloads: List<Download>) {
        _queueState.update {
            downloads.forEach { download ->
                download.status = Download.State.QUEUE
            }
            store.addAll(downloads)
            it + downloads
        }
    }

    private fun removeFromQueue(download: Download) {
        _queueState.update {
            store.remove(download)
            if (download.status == Download.State.DOWNLOADING || download.status == Download.State.QUEUE) {
                download.status = Download.State.NOT_DOWNLOADED
            }
            it - download
        }
    }

    private inline fun removeFromQueueIf(predicate: (Download) -> Boolean) {
        _queueState.update { queue ->
            val downloads = queue.filter { predicate(it) }
            store.removeAll(downloads)
            downloads.forEach { download ->
                if (download.status == Download.State.DOWNLOADING || download.status == Download.State.QUEUE) {
                    download.status = Download.State.NOT_DOWNLOADED
                }
            }
            queue - downloads
        }
    }

    fun removeFromQueue(episodes: List<Episode>) {
        val episodeIds = episodes.map { it.id }
        removeFromQueueIf { it.episode.id in episodeIds }
    }

    fun removeFromQueue(anime: Anime) {
        removeFromQueueIf { it.anime.id == anime.id }
    }

    private fun internalClearQueue() {
        _queueState.update {
            it.forEach { download ->
                if (download.status == Download.State.DOWNLOADING || download.status == Download.State.QUEUE) {
                    download.status = Download.State.NOT_DOWNLOADED
                }
            }
            store.clear()
            emptyList()
        }
    }

    fun updateQueue(downloads: List<Download>) {
        val wasRunning = isRunning

        if (downloads.isEmpty()) {
            clearQueue()
            stop()
            return
        }

        pause()
        internalClearQueue()
        addAllToQueue(downloads)

        if (wasRunning) {
            start()
        }
    }

    companion object {
        const val TMP_DIR_SUFFIX = "_tmp"
        const val WARNING_NOTIF_TIMEOUT_MS = 30_000L
        const val EPISODES_PER_SOURCE_QUEUE_WARNING_THRESHOLD = 15
        private const val DOWNLOADS_QUEUED_WARNING_THRESHOLD = 30
    }
}

// Arbitrary minimum required space to start a download: 200 MB
private const val MIN_DISK_SPACE = 200L * 1024 * 1024
