package eu.kanade.tachiyomi.data.download.downloader

import android.content.Context
import androidx.core.net.toUri
import com.hippo.unifile.UniFile
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.network.get
import io.lindstrom.mpd.MPDParser
import io.lindstrom.mpd.ParsingMode
import io.lindstrom.mpd.data.AdaptationSet
import io.lindstrom.mpd.data.BaseURL
import io.lindstrom.mpd.data.PresentationType
import io.lindstrom.mpd.data.Representation
import io.lindstrom.mpd.data.SegmentTemplate
import okhttp3.Headers
import okhttp3.OkHttpClient
import okio.Throttler
import kotlin.math.ceil

@Inject
@SingleIn(AppScope::class)
class DashDownloader(
    private val context: Context,
    private val directDownloader: DirectDownloader,
    private val downloader: HttpDownloader,
) {
    private enum class TrackType { VIDEO, AUDIO, SUBTITLE }

    suspend fun parsePlaylist(
        url: String,
        headers: Headers,
        client: OkHttpClient,
        name: String,
    ): PlaylistResult {
        val xml = when {
            url.startsWith("http") -> client.get(url, headers).body.string()
            url.startsWith("file") -> {
                context.contentResolver.openInputStream(url.toUri())?.bufferedReader()?.use {
                    it.readText()
                } ?: throw Exception("Failed to read from file $url")
            }
            else -> throw IllegalArgumentException("Unexpected input: $url")
        }

        val mpd = MPDParser(ParsingMode.LENIENT).parse(xml)
        if (mpd.type != PresentationType.STATIC) {
            throw IllegalStateException("Unsupported type: ${mpd.type}")
        }
        val mpdBaseUrl = if (url.startsWith("http")) {
            resolveBaseUrl(url, mpd.baseURLs)
        } else {
            mpd.baseURLs.firstOrNull()?.value
                ?: throw IllegalStateException("No baseurls for playlist")
        }

        val period = mpd.periods.singleOrNull()
            ?: throw IllegalStateException("Multi-period playlists not supported")
        val periodMs = (period.duration ?: mpd.mediaPresentationDuration)?.toMillis() ?: 0L
        val periodBaseUrl = resolveBaseUrl(mpdBaseUrl, period.baseURLs)

        val tracks = period.adaptationSets
            .flatMap { s -> s.representations.map { s to it } }
            .mapNotNull { (s, r) ->
                getTrackType(s, r)?.let { Triple(it, s, r) }
            }
            .groupBy { (type, s, _) ->
                when (type) {
                    TrackType.VIDEO -> "video"
                    TrackType.AUDIO -> "aud-${s.lang}"
                    TrackType.SUBTITLE -> "sub-${s.lang}"
                }
            }
            .map { it.value.maxBy { (_, _, r) -> r.bandwidth } }

        val audioTracks = tracks
            .filter { (type, _, _) -> type == TrackType.AUDIO }
            .mapIndexed { i, (_, set, rep) ->
                getSegmentList(rep, set, periodMs, mpdBaseUrl, periodBaseUrl, "aud$i")
            }

        val subtitleTracks = tracks
            .filter { (type, _, _) -> type == TrackType.SUBTITLE }
            .mapIndexed { i, (_, set, rep) ->
                getSegmentList(rep, set, periodMs, mpdBaseUrl, periodBaseUrl, "aud$i")
            }

        val video = tracks.first { (type, _, _) -> type == TrackType.VIDEO }.let { (_, set, rep) ->
            getSegmentList(rep, set, periodMs, mpdBaseUrl, periodBaseUrl, "vid")
        }

        return when (video) {
            // Can't use concat demuxer and concat prototol results in long strings
            is TrackResult.Playlist -> PlaylistResult.Content(
                type = DownloadType.Hls,
                content = video.content,
                fragments = video.fragments,
                audioTracks = audioTracks,
                subtitleTracks = subtitleTracks,
            )
            is TrackResult.Url -> PlaylistResult.Url(
                url = video.url,
                audioTracks = audioTracks,
                subtitleTracks = subtitleTracks,
            )
        }
    }

    suspend fun downloadTrack(
        headers: Headers,
        client: OkHttpClient,
        track: TrackResult,
        progress: ItemProgress,
        throttler: Throttler?,
        threadCount: Int,
        destDir: UniFile,
        name: String,
    ): String {
        return when (track) {
            is TrackResult.Url -> {
                directDownloader.download(
                    url = track.url,
                    headers = headers,
                    client = client,
                    progress = progress,
                    throttler = throttler,
                    threadCount = threadCount,
                    destDir = destDir,
                    name = name,
                    forceSingle = false,
                )
            }
            is TrackResult.Playlist -> {
                downloader.downloadPlaylist(
                    headers = headers,
                    client = client,
                    playlist = track.content,
                    fragments = track.fragments,
                    progress = progress,
                    throttler = throttler,
                    threadCount = threadCount,
                    destDir = destDir,
                    name = name,
                    ffmpegName = "playlist",
                    ffmpegType = "dash",
                )
            }
        }
    }

    private fun getTrackType(set: AdaptationSet, representation: Representation): TrackType? {
        val type = listOfNotNull(set.contentType, representation.mimeType, set.mimeType)

        return when {
            type.any { it.contains("video", true) } -> TrackType.VIDEO
            type.any { it.contains("audio", true) } -> TrackType.AUDIO
            listOf("text", "ttml", "webvtt", "3gpp").any { t ->
                type.any { it.contains(t, true) }
            } -> TrackType.SUBTITLE
            else -> null
        }
    }

    private fun getSegmentList(
        representation: Representation,
        set: AdaptationSet,
        periodMs: Long,
        mpdUrl: String,
        periodBase: String,
        prefix: String,
    ): TrackResult {
        val inputList = StringBuilder()
        inputList.appendLine("#EXTM3U")
        inputList.appendLine("#EXT-X-PLAYLIST-TYPE:VOD")
        inputList.appendLine("#EXT-X-VERSION:4")
        val segmentUrls = mutableListOf<DownloadFragment>()

        val baseUrl = resolveBaseUrl(resolveBaseUrl(periodBase, set.baseURLs), representation.baseURLs)
        val template = representation.segmentTemplate ?: set.segmentTemplate
        val media = template?.media
        val segmentList = representation.segmentList ?: set.segmentList
        var i = 0

        when {
            template != null && media != null -> {
                template.initialization?.let {
                    val name = "${prefix}_${i++}.m4s"
                    val url = resolveUrl(baseUrl, substitute(it, representation, null, null))
                    inputList.appendLine("#EXTINF:,\n$name")
                    segmentUrls.add(DownloadFragment(name, url))
                }
                getSegmentTimeline(template, periodMs).forEach { (n, time) ->
                    val name = "${prefix}_${i++}.m4s"
                    val url = resolveUrl(baseUrl, substitute(media, representation, n, time))
                    inputList.appendLine("#EXTINF:,\n$name")
                    segmentUrls.add(DownloadFragment(name, url))
                }
            }
            segmentList != null -> {
                segmentList.initialization?.sourceURL?.let {
                    val name = "${prefix}_${i++}.m4s"
                    inputList.appendLine("#EXTINF:,\n$name")
                    segmentUrls.add(DownloadFragment(name, resolveUrl(baseUrl, it)))
                }
                segmentList.segmentURLs.forEach {
                    val mediaUrl = it.media ?: return@forEach
                    val name = "${prefix}_${i++}.m4s"
                    inputList.appendLine("#EXTINF:,\n$name")
                    segmentUrls.add(
                        DownloadFragment(
                            name = name,
                            url = resolveUrl(baseUrl, mediaUrl),
                        ),
                    )
                }
            }
            else -> {
                if (baseUrl == mpdUrl) {
                    throw IllegalStateException("No segments found")
                } else {
                    return TrackResult.Url(
                        url = baseUrl,
                        name = set.lang ?: set.id.toString(),
                    )
                }
            }
        }

        inputList.append("#EXT-X-ENDLIST")

        return TrackResult.Playlist(
            type = DownloadType.Hls,
            content = inputList.toString(),
            fragments = segmentUrls,
            name = set.lang ?: set.id.toString(),
        )
    }

    private fun substitute(
        template: String,
        representation: Representation,
        number: Long?,
        time: Long?,
    ): String {
        return templateRegex.replace(template) { m ->
            val width = m.groupValues[3].toIntOrNull() ?: 1
            val r = when (m.groupValues[1]) {
                "" -> return@replace "$"
                "RepresentationID" -> return@replace representation.id
                "Bandwidth" -> representation.bandwidth
                "Number" -> number
                else -> time
            }
            r.toString().padStart(width, '0')
        }
    }

    private fun getSegmentTimeline(
        template: SegmentTemplate,
        periodMs: Long,
    ): List<Pair<Long, Long>> {
        val timescale = template.timescale ?: 1L
        val start = template.startNumber ?: 1L
        val offset = template.presentationTimeOffset ?: 0L
        val end = offset + periodMs / 1000.0 * timescale

        val timeline = template.segmentTimeline
        if (timeline == null || timeline.isEmpty()) {
            val duration = template.duration ?: throw IllegalStateException("Duration not found")
            val count = ceil((end - offset) / duration).toLong()
            return (0 until count).map {
                (start + it) to (offset + it * duration)
            }
        }

        val out = mutableListOf<Pair<Long, Long>>()
        var time = 0L
        var number = start
        timeline.forEach { s ->
            s.t?.let { time = it }
            val repeat = s.r ?: 0L
            val count = if (repeat >= 0) {
                repeat + 1
            } else {
                ceil((end - time) / s.d).toLong()
            }
            (0 until count).forEach { _ ->
                out.add(number++ to time)
                time += s.d
            }
        }
        return out
    }

    private fun resolveBaseUrl(baseUrl: String, urls: List<BaseURL>): String {
        val relative = urls.firstOrNull()?.value?.trim() ?: return baseUrl
        return resolveUrl(baseUrl, relative)
    }

    companion object {
        private val templateRegex = Regex("""\$(RepresentationID|Number|Bandwidth|Time)?(%0(\d+)d)?\$""")
    }
}
