package tachiyomi.cast.domain

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.core.net.toUri
import animiru.domain.player.model.VideoTrack
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFprobeKit
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.util.storage.toFFmpegString
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import tachiyomi.core.common.util.system.createFileInCacheDir
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Serializable
private data class FFprobeDto(
    val streams: List<StreamDto>,
    val chapters: List<StreamChapterDto>,
    val format: FormatDto,
)

@Serializable
private data class StreamDto(
    val index: Int,
    @SerialName("codec_name")
    val codecName: String? = null,
    @SerialName("codec_type")
    val codecType: String,
    val tags: StreamTagDto? = null,
)

@Serializable
private data class StreamChapterDto(
    @SerialName("start_time")
    val startTime: Double,
    val tags: StreamTagDto? = null,
)

@Serializable
private data class StreamTagDto(
    val language: String? = null,
    val title: String? = null,
)

@Serializable
private data class FormatDto(
    @SerialName("format_name")
    val formatName: String,
    val duration: String? = null,
)

@Stable
data class TrackInformation(
    override val title: String,
    override val language: String,
    val index: Long,
    val type: String,
    val contentType: String,
    val contentId: String? = null,
    val loading: Boolean = false,
    val error: Boolean = false,
) : VideoTrack

data class ChapterInformation(
    val startTime: Double,
    val name: String,
)

data class CodecInformation(
    val contentType: String,
    val tracks: List<TrackInformation>,
    val chapters: List<ChapterInformation>,
    val duration: Double?,
)

@Inject
class VideoInformation(
    private val context: Context,
    private val json: Json,
) {
    @OptIn(ExperimentalSerializationApi::class)
    suspend fun getVideoInformation(videoUrl: String, headers: Headers): CodecInformation {
        val informationFile = context.createFileInCacheDir("ffprobe_information.json")
        val informationFilePath = informationFile.toUri().toFFmpegString(context)

        val headerOptions = if (headers.toMap().isEmpty()) {
            ""
        } else {
            headers.joinToString("", "-headers '", "'") {
                "${it.first}: ${it.second}\r\n"
            }
        }

        val videoUrl = if (videoUrl.startsWith("content")) {
            videoUrl.toUri().toFFmpegString(context)
        } else {
            videoUrl
        }

        val ffprobeCommand = FFmpegKitConfig.parseArguments(
            listOf(
                headerOptions,
                "-v quiet -print_format json -show_format -show_streams -show_chapters",
                "-o \"$informationFilePath\"",
                "\"$videoUrl\"",
            ).joinToString(" "),
        )

        suspendCancellableCoroutine { continuation ->
            val session = FFprobeKit.executeWithArgumentsAsync(ffprobeCommand) {
                if (it.getReturnCode()?.isValueSuccess() == true) {
                    continuation.resume(it)
                } else {
                    continuation.resumeWithException(Exception(it.getOutput()))
                }
            }
            continuation.invokeOnCancellation { session.cancel() }
        }

        val resultDto = informationFile.inputStream().use {
            json.decodeFromStream<FFprobeDto>(it)
        }

        val containerType = getContainerMimeType(resultDto.format.formatName, resultDto.streams)

        val tracks = mutableListOf<TrackInformation>()
        resultDto.streams.forEach {
            if (it.codecType != "audio" && it.codecType != "subtitle") return@forEach
            if (it.codecName == null) return@forEach

            val contentType = if (it.codecType ==
                "audio"
            ) {
                getAudioMimeType(it.codecName, containerType)
            } else {
                getSubtitleMimeType(it.codecName)
            }
            val fallbackName = if (it.codecType == "audio") "Audio ${it.index}" else "Subtitle ${it.index}"
            tracks.add(
                TrackInformation(
                    index = it.index.toLong(),
                    type = it.codecType,
                    contentType = contentType,
                    title = it.tags?.title ?: fallbackName,
                    language = it.tags?.language ?: "und",
                ),
            )
        }

        val chapters = resultDto.chapters.map {
            ChapterInformation(
                startTime = it.startTime,
                name = it.tags?.title ?: "",
            )
        }

        return CodecInformation(
            contentType = containerType,
            tracks = tracks,
            chapters = chapters,
            duration = resultDto.format.duration?.toDoubleOrNull(),
        )
    }

    private fun getContainerMimeType(formatName: String, tracks: List<StreamDto>): String {
        val name = formatName.lowercase()

        return when {
            "hls" in name -> "application/x-mpegURL"
            "dash" in name -> "application/dash+xml"
            "matroska" in name || "webm" in name -> {
                val webm = setOf("vp8", "vp9", "av1", "opus", "vorbis")
                val isWebm = tracks
                    .filter { it.codecType == "video" || it.codecType == "audio" }
                    .all { it.codecName?.lowercase() in webm }
                if (isWebm) "video/webm" else "video/x-matroska"
            }
            name in setOf("mp4", "mov", "m4a", "3gp", "3g2", "mj2") -> "video/mp4"
            name in setOf("asf", "wmv") -> "video/x-ms-wmv"
            "avi" in name -> "video/x-msvideo"
            "flv" in name -> "video/x-flv"
            "mpegts" in name -> "video/mp2t"
            "ogg" in name -> "video/ogg"
            else -> "application/octet-stream"
        }
    }

    private fun getAudioMimeType(codec: String, container: String): String = when (codec) {
        "aac" -> if (container == "video/mp4") "audio/mp4" else "audio/aac"
        "mp3" -> "audio/mpeg"
        "opus" -> "audio/opus"
        "vorbis" -> "audio/ogg"
        "ac3" -> "audio/ac3"
        "eac3" -> "audio/eac3"
        "flac" -> "audio/flac"
        "pcm_s16le", "pcm_s24le" -> "audio/wav"
        "dts" -> "audio/vnd.dts"
        "truehd" -> "audio/vnd.dolby.mlp"
        else -> "audio/unknown"
    }

    private fun getSubtitleMimeType(codec: String): String = when (codec) {
        "ass", "ssa" -> "text/x-ssa"
        "subrip", "srt" -> "application/x-subrip"
        "webvtt" -> "text/vtt"
        "mov_text" -> "text/mp4"
        "ttml" -> "application/ttml+xml"
        "hdmv_pgs_subtitle" -> "application/x-pgs"
        "dvd_subtitle" -> "application/x-dvd-subtitle"
        else -> "application/octet-stream"
    }

    fun getSubtitleContentType(url: String): String {
        val extension = url.toHttpUrl().pathSegments.last()
            .substringAfterLast(".", "")
            .lowercase()
        return when (extension) {
            "vtt" -> "text/vtt"
            "srt" -> "application/x-subrip"
            "ttml", "dfxp", "xml" -> "application/ttml+xml"
            "smi", "sami" -> "application/smil+xml"
            "ssa", "ass" -> "text/x-ssa"
            else -> "text/vtt"
        }
    }

    fun getAudioContentType(url: String): String {
        val extension = url.toHttpUrl().pathSegments.last()
            .substringAfterLast(".", "")
            .lowercase()
        return when (extension) {
            "aac" -> "audio/aac"
            "mp3" -> "audio/mpeg"
            "m4a", "m4b" -> "audio/mp4"
            "wav" -> "audio/wav"
            "ogg", "oga", "opus" -> "audio/ogg"
            "flac" -> "audio/flac"
            "webm" -> "audio/webm"
            else -> "audio/mp4"
        }
    }
}
