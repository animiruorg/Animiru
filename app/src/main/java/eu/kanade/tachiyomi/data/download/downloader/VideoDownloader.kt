package eu.kanade.tachiyomi.data.download.downloader

import android.content.Context
import androidx.core.net.toUri
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
import eu.kanade.tachiyomi.animesource.model.TimeStamp
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.get
import eu.kanade.tachiyomi.network.head
import eu.kanade.tachiyomi.ui.player.controls.components.IndexedSegment
import eu.kanade.tachiyomi.ui.player.utils.ChapterUtils
import eu.kanade.tachiyomi.util.lang.Hash.md5
import eu.kanade.tachiyomi.util.storage.toFFmpegString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import logcat.LogPriority
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okio.Throttler
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.download.service.DownloadPreferences
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToLong

enum class DownloadType {
    Hls,
    Dash,
    Direct,
}

data class DownloadTrack(
    val name: String,
    val ffmpegInput: String,
)

sealed interface TrackResult {
    val name: String

    data class Url(
        val url: String,
        override val name: String,
    ) : TrackResult

    data class Playlist(
        val type: DownloadType,
        val content: String,
        val fragments: List<DownloadFragment>,
        override val name: String,
    ) : TrackResult
}

sealed interface PlaylistResult {
    val subtitleTracks: List<TrackResult>
    val audioTracks: List<TrackResult>

    data class Url(
        val url: String,
        override val subtitleTracks: List<TrackResult>,
        override val audioTracks: List<TrackResult>,
    ) : PlaylistResult

    data class Content(
        val type: DownloadType,
        val content: String,
        val fragments: List<DownloadFragment>,
        override val subtitleTracks: List<TrackResult>,
        override val audioTracks: List<TrackResult>,
    ) : PlaylistResult
}

@Inject
@SingleIn(AppScope::class)
class VideoDownloader(
    private val context: Context,
    private val network: NetworkHelper,
    private val hlsDownloader: HlsDownloader,
    private val dashDownloader: DashDownloader,
    private val directDownloader: DirectDownloader,
    private val downloadPreferences: DownloadPreferences,
    private val journalFactory: DownloadJournal.Factory,
    private val json: Json,
) {
    private val client = network.client

    suspend fun download(
        download: Download,
        destDir: UniFile,
        filename: String,
        isTorrent: Boolean,
    ): UniFile {
        val video = download.video!!
        val videoHeaders = video.headers ?: Headers.EMPTY
        val client = download.source.client

        var threadCount = downloadPreferences.downloadThreads.get()
        val skipTracks = downloadPreferences.ignoreBrokenTracks.get()
        val speedLimit = downloadPreferences.downloadSpeedLimit.get()
        val throttler = Throttler().takeIf { speedLimit > 0 }
        throttler?.apply {
            bytesPerSecond(speedLimit * 1024L)
        }

        var downloadDir = getDownloadCacheDir(download)
        val existing = DownloadJournal.read(downloadDir, json)
        val initial = when {
            existing == null -> Journal(threadCount, video.videoTitle)
            existing.videoTitle != video.videoTitle -> {
                downloadDir.delete()
                downloadDir = getDownloadCacheDir(download)
                Journal(threadCount, video.videoTitle)
            }
            else -> {
                threadCount = existing.threadCount
                existing
            }
        }
        val journal = journalFactory.create(downloadDir, initial)
        journal.write()

        val progress = ProgressAggregator { percent ->
            download.progress = percent
        }

        val playlistResult = parsePlaylist(
            url = video.videoUrl,
            headers = videoHeaders,
            client = client,
            destDir = downloadDir,
            name = "vid",
        )

        val subtitleTracks =
            playlistResult.subtitleTracks + video.subtitleTracks.map { TrackResult.Url(it.url, it.lang) }
        val audioTracks = playlistResult.audioTracks + video.audioTracks.map { TrackResult.Url(it.url, it.lang) }

        val videoProgress = progress.register(VIDEO_WEIGHT)
        val subtitleProgress = subtitleTracks.map { progress.register(SUBTITLE_WEIGHT) }
        val audioProgress = audioTracks.map { progress.register(AUDIO_WEIGHT) }

        val subtitleDownloadTracks = subtitleTracks.mapIndexedNotNull { i, track ->
            try {
                val downloaded = journal.getSubtitleInput(i)
                val input = if (downloaded == null) {
                    downloadItem(
                        trackResult = track,
                        headers = videoHeaders,
                        client = client,
                        progress = subtitleProgress[i],
                        throttler = throttler,
                        threadCount = threadCount,
                        destDir = downloadDir,
                        name = "sub$i",
                        forceSingle = true,
                    )
                        .also { journal.updateSubtitle(i, it) }
                } else {
                    downloaded
                        .also { subtitleProgress[i].report(1f) }
                }

                DownloadTrack(
                    name = track.name,
                    ffmpegInput = input,
                )
            } catch (e: Exception) {
                if (e is CancellationException || !skipTracks) throw e
                null
            }
        }

        val audioDownloadTracks = audioTracks.mapIndexedNotNull { i, track ->
            try {
                val downloaded = journal.getAudioInput(i)
                val input = if (downloaded == null) {
                    downloadItem(
                        trackResult = track,
                        headers = videoHeaders,
                        client = client,
                        progress = audioProgress[i],
                        throttler = throttler,
                        threadCount = threadCount,
                        destDir = downloadDir,
                        name = "aud$i",
                    )
                        .also { journal.updateAudio(i, it) }
                } else {
                    downloaded
                        .also { audioProgress[i].report(1f) }
                }
                DownloadTrack(
                    name = track.name,
                    ffmpegInput = input,
                )
            } catch (e: Exception) {
                if (e is CancellationException || !skipTracks) throw e
                null
            }
        }

        val downloadedVideo = journal.getVideoInput()
        val videoInput = if (downloadedVideo == null) {
            downloadPlaylist(
                playlistResult = playlistResult,
                headers = videoHeaders,
                client = client,
                progress = videoProgress,
                throttler = throttler,
                threadCount = threadCount,
                destDir = downloadDir,
                name = "vid",
                forceSingle = isTorrent,
            )
                .also { journal.updateVideo(it) }
        } else {
            downloadedVideo
                .also { videoProgress.report(1f) }
        }

        return merge(
            download = download,
            video = video,
            videoInput = videoInput,
            subtitleTracks = subtitleDownloadTracks,
            audioTracks = audioDownloadTracks,
            downloadDir = downloadDir,
            destDir = destDir,
            filename = filename,
        )
    }

    private fun getDownloadCacheDir(download: Download): UniFile {
        val downloadKey = listOf(
            download.source.name,
            download.anime.ogTitle,
            download.episode.scanlator,
            download.episode.name,
        ).joinToString("_")

        val file = context.getExternalFilesDir(DOWNLOADS_DIR)
            ?: File(context.filesDir, DOWNLOADS_DIR).also { it.mkdirs() }

        return UniFile.fromFile(file)!!
            .createDirectory(md5(downloadKey).take(16))!!
    }

    private suspend fun parsePlaylist(
        url: String,
        headers: Headers,
        client: OkHttpClient,
        destDir: UniFile,
        name: String,
    ): PlaylistResult {
        val type = getType(url, headers)

        return when (type) {
            DownloadType.Hls -> hlsDownloader.parsePlaylist(url, headers, client, name)
            DownloadType.Dash -> dashDownloader.parsePlaylist(url, headers, client, name)
            DownloadType.Direct -> directDownloader.parsePlaylist(url, headers, name)
        }
    }

    private suspend fun downloadPlaylist(
        playlistResult: PlaylistResult,
        headers: Headers,
        client: OkHttpClient,
        progress: ItemProgress,
        throttler: Throttler?,
        threadCount: Int,
        destDir: UniFile,
        name: String,
        forceSingle: Boolean,
    ): String {
        return when (playlistResult) {
            is PlaylistResult.Url -> {
                val type = getType(playlistResult.url, headers)

                when (type) {
                    DownloadType.Hls -> hlsDownloader.download(
                        url = playlistResult.url,
                        headers = headers,
                        client = client,
                        progress = progress,
                        throttler = throttler,
                        threadCount = threadCount,
                        destDir = destDir,
                        name = name,
                    )
                    DownloadType.Dash -> {
                        val track = TrackResult.Url(
                            url = playlistResult.url,
                            name = name,
                        )
                        dashDownloader.downloadTrack(
                            headers = headers,
                            client = client,
                            track = track,
                            progress = progress,
                            throttler = throttler,
                            threadCount = threadCount,
                            destDir = destDir,
                            name = name,
                        )
                    }
                    DownloadType.Direct -> directDownloader.download(
                        url = playlistResult.url,
                        headers = headers,
                        client = client,
                        progress = progress,
                        throttler = throttler,
                        threadCount = threadCount,
                        destDir = destDir,
                        name = name,
                        forceSingle = forceSingle,
                    )
                }
            }
            is PlaylistResult.Content -> {
                when (playlistResult.type) {
                    DownloadType.Hls -> hlsDownloader.download(
                        headers = headers,
                        client = client,
                        playlist = playlistResult.content,
                        fragments = playlistResult.fragments,
                        progress = progress,
                        throttler = throttler,
                        threadCount = threadCount,
                        destDir = destDir,
                        name = name,
                    )
                    DownloadType.Dash -> {
                        val track = TrackResult.Playlist(
                            type = DownloadType.Dash,
                            content = playlistResult.content,
                            fragments = playlistResult.fragments,
                            name = name,
                        )
                        dashDownloader.downloadTrack(
                            headers = headers,
                            client = client,
                            track = track,
                            progress = progress,
                            throttler = throttler,
                            threadCount = threadCount,
                            destDir = destDir,
                            name = name,
                        )
                    }
                    DownloadType.Direct -> throw IllegalStateException("Content not supported for direct download")
                }
            }
        }
    }

    private suspend fun downloadItem(
        trackResult: TrackResult,
        headers: Headers,
        client: OkHttpClient,
        progress: ItemProgress,
        throttler: Throttler?,
        threadCount: Int,
        destDir: UniFile,
        name: String,
        forceSingle: Boolean = false,
    ): String {
        return when (trackResult) {
            is TrackResult.Url -> {
                val url = trackResult.url
                if (url.startsWith("file")) {
                    progress.report(1f)
                    return "-allowed_extensions ALL -i \"$url\""
                }

                val type = getType(url, headers)

                when (type) {
                    DownloadType.Hls -> hlsDownloader.download(
                        url = url,
                        headers = headers,
                        client = client,
                        progress = progress,
                        throttler = throttler,
                        threadCount = threadCount,
                        destDir = destDir,
                        name = name,
                    )
                    DownloadType.Dash -> dashDownloader.downloadTrack(
                        headers = headers,
                        client = client,
                        track = trackResult,
                        progress = progress,
                        throttler = throttler,
                        threadCount = threadCount,
                        destDir = destDir,
                        name = name,
                    )
                    DownloadType.Direct -> directDownloader.download(
                        url = url,
                        headers = headers,
                        client = client,
                        progress = progress,
                        throttler = throttler,
                        threadCount = threadCount,
                        destDir = destDir,
                        name = name,
                        forceSingle = forceSingle,
                    )
                }
            }
            is TrackResult.Playlist -> {
                when (trackResult.type) {
                    DownloadType.Hls -> hlsDownloader.download(
                        headers = headers,
                        client = client,
                        playlist = trackResult.content,
                        fragments = trackResult.fragments,
                        progress = progress,
                        throttler = throttler,
                        threadCount = threadCount,
                        destDir = destDir,
                        name = name,
                    )
                    DownloadType.Dash -> {
                        dashDownloader.downloadTrack(
                            headers = headers,
                            client = client,
                            track = trackResult,
                            progress = progress,
                            throttler = throttler,
                            threadCount = threadCount,
                            destDir = destDir,
                            name = name,
                        )
                    }
                    DownloadType.Direct -> throw IllegalStateException("Content not supported for direct download")
                }
            }
        }
    }

    private suspend fun getType(url: String, headers: Headers): DownloadType {
        return when {
            url.startsWith("http") -> getHttpType(url, headers)
            else -> getFileType(url)
        }
    }

    private fun getFileType(url: String): DownloadType {
        if (url.endsWith(".m3u8")) return DownloadType.Hls
        if (url.endsWith(".mpd")) return DownloadType.Dash
        if (url.endsWith(".vtt") || url.endsWith(".ass")) return DownloadType.Direct

        val file = UniFile.fromUri(context, url.toUri())?.openInputStream()?.use { input ->
            val bytes = ByteArray(1024)
            val n = input.read(bytes, 0, bytes.size)
            val text = String(bytes, 0, n, Charsets.UTF_8).trim()

            when {
                text.startsWith("#EXTM3U") -> return DownloadType.Hls
                text.startsWith("<") && text.contains("<MPD") -> return DownloadType.Dash
            }
        }

        return DownloadType.Direct
    }

    private suspend fun getHttpType(url: String, headers: Headers): DownloadType {
        val path = url.toHttpUrl().encodedPath
        if (path.endsWith(".m3u8")) return DownloadType.Hls
        if (path.endsWith(".mpd")) return DownloadType.Dash
        if (url.endsWith(".vtt") || url.endsWith(".ass") || url.endsWith(".mkv")) return DownloadType.Direct

        val headHeaders = headers.newBuilder().set("Connection", "close").build()
        val contentType = client.head(url, headHeaders).use {
            it.header("Content-Type")?.lowercase()
        }

        if (contentType?.contains("application/x-mpegurl") == true ||
            contentType?.contains("application/vnd.apple.mpegurl") == true ||
            contentType?.contains("audio/mpegurl") == true
        ) {
            return DownloadType.Hls
        }

        if (
            contentType?.contains("video/x-matroska") == true ||
            contentType?.contains("video/mp4") == true ||
            contentType?.contains("video/webm") == true
        ) {
            return DownloadType.Direct
        }

        val probeHeaders = headers.newBuilder()
            .set("Range", "bytes=0-1023")
            .build()

        client.get(url, probeHeaders).use {
            if (!it.isSuccessful) return DownloadType.Direct

            val source = it.body.source()
            source.request(1024)
            val len = minOf(source.buffer.size, 1024L)
            val text = source.buffer.peek().readUtf8(len).trim()

            when {
                text.startsWith("#EXTM3U") -> return DownloadType.Hls
                text.startsWith("<") && text.contains("<MPD") -> return DownloadType.Dash
            }
        }

        return DownloadType.Direct
    }

    private suspend fun merge(
        download: Download,
        video: Video,
        videoInput: String,
        subtitleTracks: List<DownloadTrack>,
        audioTracks: List<DownloadTrack>,
        downloadDir: UniFile,
        destDir: UniFile,
        filename: String,
    ): UniFile {
        destDir.findFile("$filename.tmp")?.delete()
        val videoFile = destDir.createFile("$filename.tmp")!!

        download.progress = 0

        val information = getInformation(downloadDir, videoInput)
        val duration = information.format.duration?.toDoubleOrNull()

        val hasTimeStamps = download.video?.timestamps?.isNotEmpty() ?: false
        val chapterInput = if (hasTimeStamps) {
            val chapterFile = getChapterMetadata(
                destDir = downloadDir,
                chapters = information.chapters,
                timestamps = download.video?.timestamps.orEmpty(),
                duration = duration?.toFloat(),
            )
            "-f ffmetadata -i \"${chapterFile}\""
        } else {
            ""
        }
        val chapterMetadata = if (hasTimeStamps) {
            listOf(
                "-map_metadata 0",
                "-map_chapters ${audioTracks.size + subtitleTracks.size + 1}",
            ).joinToString(" ")
        } else {
            ""
        }

        val ffmpegFilename = videoFile.uri.toFFmpegString(context)
        val ffmpegOptions = getFfmpegOptions(
            videoInput = videoInput,
            subtitleTracks = subtitleTracks,
            audioTracks = audioTracks,
            chapterInput = chapterInput,
            chapterMetadata = chapterMetadata,
            ffmpegStreamArgs = video.ffmpegStreamArgs,
            ffmpegVideoArgs = video.ffmpegVideoArgs,
            ffmpegFilename = ffmpegFilename,
        )

        val logCallback = LogCallback { log ->
            if (log.level <= Level.AV_LOG_WARNING) {
                logcat(LogPriority.ERROR) { log.message }
            }
        }

        val statCallback = StatisticsCallback { s ->
            val outTime = (s.time / 1000.0).toLong()
            if (duration != null && duration != 0.0 && outTime > 0) {
                download.progress = (100 * outTime / duration).toInt()
            }
        }

        suspendCancellableCoroutine { continuation ->
            val session = FFmpegKit.executeWithArgumentsAsync(
                ffmpegOptions,
                {
                    if (it.getReturnCode()?.isValueSuccess() == true) {
                        downloadDir.delete()
                        videoFile.renameTo("$filename.mkv")
                        continuation.resume(it)
                    } else {
                        continuation.resumeWithException(
                            Exception(
                                "ffprobe failed code=${it.getReturnCode()} output=${it.getOutput()} logs=${it.getAllLogsAsString()} fail=${it.getFailStackTrace()}",
                            ),
                        )
                    }
                },
                logCallback,
                statCallback,
            )
            continuation.invokeOnCancellation {
                session.cancel()
            }
        }

        return videoFile
    }

    private fun getFfmpegOptions(
        videoInput: String,
        subtitleTracks: List<DownloadTrack>,
        audioTracks: List<DownloadTrack>,
        chapterInput: String,
        chapterMetadata: String,
        ffmpegStreamArgs: List<Pair<String, String>>,
        ffmpegVideoArgs: List<Pair<String, String>>,
        ffmpegFilename: String,
    ): Array<String> {
        val subtitleInputs = subtitleTracks.joinToString(" ") { it.ffmpegInput }
        val subtitleMaps = formatMaps(subtitleTracks, "s")
        val subtitleMetadata = formatMetadata(subtitleTracks, "s")

        val audioInputs = audioTracks.joinToString(" ") { it.ffmpegInput }
        val audioMaps = formatMaps(audioTracks, "a", subtitleTracks.size)
        val audioMetadata = formatMetadata(audioTracks, "a")

        val sourceStreamOptions = ffmpegStreamArgs.joinToString(" ") { (key, value) ->
            "-$key \"$value\""
        }
        val sourceVideoOptions = ffmpegVideoArgs.joinToString(" ") { (key, value) ->
            "-$key \"$value\""
        }

        val videoInput = "$sourceStreamOptions $videoInput"

        val command = listOf(
            videoInput, subtitleInputs, audioInputs, chapterInput,
            "-map 0:v", audioMaps, "-map 0:a?", subtitleMaps, "-map 0:s? -map 0:t?",
            "-f matroska -c:a copy -c:v copy -c:s copy",
            subtitleMetadata, audioMetadata, chapterMetadata, sourceVideoOptions,
            "\"$ffmpegFilename\" -y",
        )
            .filter(String::isNotBlank)
            .joinToString(" ")

        return FFmpegKitConfig.parseArguments(command)
    }

    private fun formatMaps(tracks: List<DownloadTrack>, type: String, offset: Int = 0) = tracks.indices.joinToString(
        " ",
    ) {
        "-map ${it + 1 + offset}:$type"
    }

    private fun formatMetadata(tracks: List<DownloadTrack>, type: String) = tracks.mapIndexed { i, track ->
        "-metadata:s:$type:$i \"title=${track.name}\""
    }.joinToString(" ")

    private fun getChapterMetadata(
        destDir: UniFile,
        chapters: List<VideoInformation.Chapter>,
        timestamps: List<TimeStamp>,
        duration: Float?,
    ): String {
        val segments = chapters.mapNotNull {
            IndexedSegment(it.tags?.title.orEmpty(), it.startTime?.toFloatOrNull() ?: return@mapNotNull null)
        }
        val merged = ChapterUtils.mergeChapters(segments, timestamps, duration?.toInt())
        val end = if (duration == null) {
            if (merged.isEmpty()) {
                IndexedSegment("", 0f) // Get's thrown away in zipWithNext()
            } else {
                IndexedSegment("", merged.last().start)
            }
        } else {
            IndexedSegment("", duration)
        }
        val zipped = (merged + end).zipWithNext()
        val content = buildString {
            appendLine(";FFMETADATA1")
            zipped.forEach { (start, end) ->
                if (duration != null) {
                    if (start.start > duration) return@forEach
                }

                val end = if (duration == null) {
                    (end.start * 1000).roundToLong()
                } else {
                    (end.start * 1000).roundToLong()
                        .coerceAtMost((duration * 1000).roundToLong())
                }

                appendLine()
                appendLine("[CHAPTER]")
                appendLine("TIMEBASE=1/1000")
                appendLine("START=${(start.start * 1000).roundToLong()}")
                appendLine("END=$end")
                appendLine("title=${escapeMetadata(start.name.orEmpty())}")
            }
        }

        destDir.findFile("chapters.ffmeta")?.delete()
        val file = destDir.createFile("chapters.ffmeta")!!
        file.openOutputStream().bufferedWriter().use {
            it.write(content)
        }
        return file.filePath!!
    }

    private fun escapeMetadata(value: String): String =
        buildString {
            for (char in value) {
                when (char) {
                    '\\', '=', ';', '#' -> {
                        append('\\')
                        append(char)
                    }
                    '\n' -> append("\\\n")
                    '\r' -> append("\\\r")
                    else -> append(char)
                }
            }
        }

    private suspend fun getInformation(destDir: UniFile, ffmpegInput: String): VideoInformation {
        val durationFile = destDir.createFile("ffprobe_duration.txt")!!
        val durationFilePath = durationFile.toFFmpegString(context)

        val ffprobeCommand = FFmpegKitConfig.parseArguments(
            listOf(
                "-v error",
                "-show_entries format=duration",
                "-show_entries chapter=start_time,end_time:chapter_tags=title",
                "-of json -o \"$durationFilePath\"",
                ffmpegInput,
            ).joinToString(" "),
        )

        val logCallback = LogCallback { log ->
            logcat(LogPriority.ERROR) { log.message }
        }

        suspendCancellableCoroutine { continuation ->
            val session = FFprobeKit.executeWithArgumentsAsync(
                ffprobeCommand,
                {
                    if (it.getReturnCode()?.isValueSuccess() == true) {
                        continuation.resume(it)
                    } else {
                        continuation.resumeWithException(
                            Exception(
                                "ffprobe failed code=${it.getReturnCode()} output=${it.getOutput()} logs=${it.getAllLogsAsString()} fail=${it.getFailStackTrace()}",
                            ),
                        )
                    }
                },
                logCallback,
            )
            continuation.invokeOnCancellation { session.cancel() }
        }

        return durationFile.openInputStream().use {
            json.decodeFromStream<VideoInformation>(it)
        }
    }

    @Serializable
    private data class VideoInformation(
        val format: VideoFormat,
        val chapters: List<Chapter>,
    ) {
        @Serializable
        data class VideoFormat(
            val duration: String? = null,
        )

        @Serializable
        data class Chapter(
            @SerialName("start_time")
            val startTime: String? = null,
            @SerialName("end_time")
            val endTime: String? = null,
            val tags: ChapterTags? = null,
        ) {
            @Serializable
            data class ChapterTags(
                val title: String? = null,
            )
        }
    }

    companion object {
        const val DOWNLOADS_DIR = "downloads"

        private const val VIDEO_WEIGHT = 100.0
        private const val AUDIO_WEIGHT = 8.0
        private const val SUBTITLE_WEIGHT = 0.5
    }
}
