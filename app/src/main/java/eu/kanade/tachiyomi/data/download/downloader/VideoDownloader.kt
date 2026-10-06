package eu.kanade.tachiyomi.data.download.downloader

import android.content.Context
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.LogCallback
import com.arthenica.ffmpegkit.StatisticsCallback
import com.hippo.unifile.UniFile
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.animesource.model.Track
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
import tachiyomi.core.common.util.system.logcat
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Clock

enum class DownloadType {
    Hls,
    Dash,
    Direct,
}

data class DownloadTrack(
    val name: String,
    val ffmpegInput: String,
)

data class PlaylistResult(
    val ffmpegInput: String,
    val subtitleTracks: List<Track>,
    val audioTracks: List<Track>,
)

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
    private val directDownloader: DirectDownloader,
) {
    private val client = network.client

    suspend fun download(download: Download, destDir: UniFile, filename: String): UniFile {
        val video = download.video!!
        val videoHeaders = video.headers ?: Headers.EMPTY

        val downloadKey = listOf(
            download.source.name,
            download.anime.ogTitle,
            download.episode.scanlator,
            download.episode.name,
        ).joinToString("_")
        val downloadDir = UniFile.fromFile(getDownloadCacheDir(download))!!
            .createDirectory(md5(downloadKey).take(16))!!

        val playlistResult = downloadPlaylist(
            url = video.videoUrl,
            headers = videoHeaders,
            destDir = downloadDir,
            name = "vid",
        )

        val subtitleTracks = (playlistResult.subtitleTracks + video.subtitleTracks).mapIndexedNotNull { i, track ->
            try {
                val input = downloadItem(
                    url = track.url,
                    headers = videoHeaders,
                    destDir = downloadDir,
                    name = "sub$i",
                    forceSingle = true,
                )
                DownloadTrack(
                    name = track.lang,
                    ffmpegInput = input,
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // TODO(dl): throw
                null
            }
        }

        val audioTracks = (playlistResult.audioTracks + video.audioTracks).mapIndexedNotNull { i, track ->
            try {
                val input = downloadItem(
                    url = track.url,
                    headers = videoHeaders,
                    destDir = downloadDir,
                    name = "aud$i",
                )
                DownloadTrack(
                    name = track.lang,
                    ffmpegInput = input,
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // TODO(dl): throw
                null
            }
        }

        return merge(
            video = video,
            videoInput = playlistResult.ffmpegInput,
            subtitleTracks = subtitleTracks,
            audioTracks = audioTracks,
            downloadDir = downloadDir,
            destDir = destDir,
            filename = filename,
        )
    }

    private fun getDownloadCacheDir(download: Download): File {
        return context.getExternalFilesDir(DOWNLOADS_DIR)
            ?: File(context.filesDir, DOWNLOADS_DIR).also { it.mkdirs() }
    }

    private suspend fun downloadPlaylist(
        url: String,
        headers: Headers,
        destDir: UniFile,
        name: String,
    ): PlaylistResult {
        val type = getType(url, headers)

        return when (type) {
            DownloadType.Dash -> TODO()
            DownloadType.Hls -> hlsDownloader.downloadPlaylist(url, headers, destDir, name)
            DownloadType.Direct -> directDownloader.downloadPlaylist(url, headers, destDir, name)
        }
    }

    private suspend fun downloadItem(
        url: String,
        headers: Headers,
        destDir: UniFile,
        name: String,
        forceSingle: Boolean = false,
    ): String {
        val type = getType(url, headers)

        return when (type) {
            DownloadType.Dash -> TODO()
            DownloadType.Hls -> hlsDownloader.download(url, headers, destDir, name)
            DownloadType.Direct -> directDownloader.download(url, headers, destDir, name, forceSingle)
        }
    }

    suspend fun getType(url: String, headers: Headers): DownloadType {
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
            if (true) {
                log.message?.let {
                    logcat(LogPriority.ERROR) { it }
                }
            }
        }

        val statCallback = StatisticsCallback { s ->
        }

        val start = Clock.System.now()
        suspendCancellableCoroutine { continuation ->
            val session = FFmpegKit.executeWithArgumentsAsync(
                ffmpegOptions,
                {
                    val end = Clock.System.now()
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

    companion object {
        private const val DOWNLOADS_DIR = "downloads"
    }
}
