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
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.get
import eu.kanade.tachiyomi.network.head
import eu.kanade.tachiyomi.util.lang.Hash.md5
import eu.kanade.tachiyomi.util.storage.toFFmpegString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import logcat.LogPriority
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okio.Throttler
import tachiyomi.core.common.util.system.createFileInCacheDir
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.download.service.DownloadPreferences
import java.io.BufferedReader
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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

@Serializable
data class Journal(
    val threadCount: Int,
    val videoTitle: String,
)

@Inject
@SingleIn(AppScope::class)
class VideoDownloader(
    private val context: Context,
    private val network: NetworkHelper,
    private val hlsDownloader: HlsDownloader,
    private val dashDownloader: DashDownloader,
    private val directDownloader: DirectDownloader,
    private val downloadPreferences: DownloadPreferences,
) {
    private val client = network.client

    suspend fun download(download: Download, destDir: UniFile, filename: String): UniFile {
        val video = download.video!!
        val videoHeaders = video.headers ?: Headers.EMPTY

        val threadCount = downloadPreferences.downloadThreads.get()
        val skipTracks = downloadPreferences.ignoreBrokenTracks.get()
        val speedLimit = downloadPreferences.downloadSpeedLimit.get()
        val throttler = Throttler().takeIf { speedLimit > 0 }
        throttler?.apply {
            bytesPerSecond(speedLimit * 1024L)
        }

        val downloadKey = listOf(
            download.source.name,
            download.anime.ogTitle,
            download.episode.scanlator,
            download.episode.name,
        ).joinToString("_")
        val downloadDir = UniFile.fromFile(getDownloadCacheDir())!!
            .createDirectory(md5(downloadKey).take(16))!!

        val progress = ProgressAggregator { percent ->
            download.progress = percent
        }

        val playlistResult = parsePlaylist(
            url = video.videoUrl,
            headers = videoHeaders,
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
                val input = downloadItem(
                    trackResult = track,
                    headers = videoHeaders,
                    progress = subtitleProgress[i],
                    throttler = throttler,
                    threadCount = threadCount,
                    destDir = downloadDir,
                    name = "sub$i",
                    forceSingle = true,
                )
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
                val input = downloadItem(
                    trackResult = track,
                    headers = videoHeaders,
                    progress = audioProgress[i],
                    throttler = throttler,
                    threadCount = threadCount,
                    destDir = downloadDir,
                    name = "aud$i",
                )
                DownloadTrack(
                    name = track.name,
                    ffmpegInput = input,
                )
            } catch (e: Exception) {
                if (e is CancellationException || !skipTracks) throw e
                null
            }
        }

        val videoInput = downloadPlaylist(
            playlistResult = playlistResult,
            headers = videoHeaders,
            progress = videoProgress,
            throttler = throttler,
            threadCount = threadCount,
            destDir = downloadDir,
            name = "vid",
        )

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

    private fun getDownloadCacheDir(): File {
        return context.getExternalFilesDir(DOWNLOADS_DIR)
            ?: File(context.filesDir, DOWNLOADS_DIR).also { it.mkdirs() }
    }

    private suspend fun parsePlaylist(
        url: String,
        headers: Headers,
        destDir: UniFile,
        name: String,
    ): PlaylistResult {
        val type = getType(url, headers)

        return when (type) {
            DownloadType.Hls -> hlsDownloader.parsePlaylist(url, headers, name)
            DownloadType.Dash -> dashDownloader.parsePlaylist(url, headers, name)
            DownloadType.Direct -> directDownloader.parsePlaylist(url, headers, name)
        }
    }

    private suspend fun downloadPlaylist(
        playlistResult: PlaylistResult,
        headers: Headers,
        progress: ItemProgress,
        throttler: Throttler?,
        threadCount: Int,
        destDir: UniFile,
        name: String,
    ): String {
        return when (playlistResult) {
            is PlaylistResult.Url -> {
                val type = getType(playlistResult.url, headers)

                when (type) {
                    DownloadType.Hls -> hlsDownloader.download(
                        url = playlistResult.url,
                        headers = headers,
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
                        progress = progress,
                        throttler = throttler,
                        threadCount = threadCount,
                        destDir = destDir,
                        name = name,
                        forceSingle = false,
                    )
                }
            }
            is PlaylistResult.Content -> {
                when (playlistResult.type) {
                    DownloadType.Hls -> hlsDownloader.download(
                        headers = headers,
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
                val type = getType(url, headers)

                when (type) {
                    DownloadType.Hls -> hlsDownloader.download(
                        url = url,
                        headers = headers,
                        progress = progress,
                        throttler = throttler,
                        threadCount = threadCount,
                        destDir = destDir,
                        name = name,
                    )
                    DownloadType.Dash -> dashDownloader.downloadTrack(
                        headers = headers,
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
        val path = url.toHttpUrl().encodedPath
        if (path.endsWith(".m3u8")) return DownloadType.Hls
        if (path.endsWith(".mpd")) return DownloadType.Dash

        val contentType = client.head(url, headers).use {
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

        val duration = getDuration(videoInput)
        download.progress = 0

        val ffmpegFilename = videoFile.uri.toFFmpegString(context)
        val ffmpegOptions = getFfmpegOptions(
            videoInput = videoInput,
            subtitleTracks = subtitleTracks,
            audioTracks = audioTracks,
            ffmpegStreamArgs = video.ffmpegStreamArgs,
            ffmpegVideoArgs = video.ffmpegVideoArgs,
            ffmpegFilename = ffmpegFilename,
        )

        val logCallback = LogCallback { log ->
            if (log.level <= Level.AV_LOG_WARNING) {
                log.message?.let {
                    logcat(LogPriority.ERROR) { it }
                }
            }
        }

        val statCallback = StatisticsCallback { s ->
            val outTime = (s.time / 1000.0).toLong()
            if (duration != null && duration != 0f && outTime > 0) {
                download.progress = (100 * outTime / duration).toInt()
            }
        }

        suspendCancellableCoroutine { continuation ->
            val session = FFmpegKit.executeWithArgumentsAsync(
                ffmpegOptions,
                {
                    if (it.returnCode.isValueSuccess) {
                        downloadDir.delete()
                        videoFile.renameTo("$filename.mkv")
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

        return videoFile
    }

    private fun getFfmpegOptions(
        videoInput: String,
        subtitleTracks: List<DownloadTrack>,
        audioTracks: List<DownloadTrack>,
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

    private fun formatMaps(tracks: List<DownloadTrack>, type: String, offset: Int = 0) = tracks.indices.joinToString(
        " ",
    ) {
        "-map ${it + 1 + offset}:$type"
    }

    private fun formatMetadata(tracks: List<DownloadTrack>, type: String) = tracks.mapIndexed { i, track ->
        "-metadata:s:$type:$i \"title=${track.name}\""
    }.joinToString(" ")


    private suspend fun getDuration(ffmpegInput: String): Float? {
        val durationFile = context.createFileInCacheDir("dl_ffprobe_duration.txt")
        val durationFilePath = durationFile.toUri().toFFmpegString(context)

        val ffprobeCommand = FFmpegKitConfig.parseArguments(
            listOf(
                "-v quiet -show_entries format=duration -of default=noprint_wrappers=1:nokey=1",
                "-o \"$durationFilePath\"",
                ffmpegInput,
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

    companion object {
        const val DOWNLOADS_DIR = "downloads"

        private const val VIDEO_WEIGHT = 100.0
        private const val AUDIO_WEIGHT = 8.0
        private const val SUBTITLE_WEIGHT = 0.5
    }
}
