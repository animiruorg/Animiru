package eu.kanade.tachiyomi.data.download.downloader

import com.hippo.unifile.UniFile
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.get
import okhttp3.Headers
import okio.Throttler
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.time.Duration
import kotlin.math.ceil

@Inject
@SingleIn(AppScope::class)
class DashDownloader(
    private val networkHelper: NetworkHelper,
    private val directDownloader: DirectDownloader,
    private val downloader: HttpDownloader,
) {
    private val client = networkHelper.client

    enum class Kind { VIDEO, AUDIO, SUBTITLE, OTHER }

    private data class Pick(
        val kind: Kind,
        val adaptationSet: Int,
        val representation: Int,
        val bandwidth: Long,
    )

    suspend fun parsePlaylist(
        url: String,
        headers: Headers,
        name: String,
    ): PlaylistResult {
        val xml = client.get(url, headers).body.string()
        val mpd = xml.asXml(url).selectFirst("MPD")
            ?: throw Exception("No mpd root element found")

        val period = mpd.kids("Period").singleOrNull()
            ?: throw Exception("Only single-period manifests are supported")

        val tracks = period.kids("AdaptationSet").mapIndexedNotNull { ai, aset ->
            getBestRepresentation(ai, aset)
        }

        fun parse(p: Pick, name: String): TrackResult? {
            val document = xml.asXml(url).also {
                prune(it, p)
            }
            return Parser(url, name).parse(document)
        }

        fun getTracks(kind: Kind, getName: (Int) -> String): List<TrackResult> {
            return tracks.filter { it.kind == kind }
                .mapIndexedNotNull { index, pick ->
                    parse(pick, getName(index))
                }
        }

        val video = tracks.filter { it.kind == Kind.VIDEO }
            .maxByOrNull { it.bandwidth }
            ?.let {
                parse(it, name)
            }
            ?: throw Exception("No videos found")

        val subtitleTracks = getTracks(Kind.SUBTITLE) { "sub$it" }
        val audioTracks = getTracks(Kind.AUDIO) { "aud$it" }

        return when (video) {
            is TrackResult.Url -> PlaylistResult.Url(
                url = video.url,
                subtitleTracks = subtitleTracks,
                audioTracks = audioTracks,
            )
            is TrackResult.Playlist -> PlaylistResult.Content(
                type = DownloadType.Dash,
                content = video.content,
                fragments = video.fragments,
                subtitleTracks = subtitleTracks,
                audioTracks = audioTracks,
            )
        }
    }

    suspend fun downloadTrack(
        headers: Headers,
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
                    playlist = track.content,
                    fragments = track.fragments,
                    progress = progress,
                    throttler = throttler,
                    threadCount = threadCount,
                    destDir = destDir,
                    name = name,
                    ffmpegName = "playlist",
                    ffmpegType = "dash",
                    ffmpegArgs = "-allowed_extensions ALL",
                )
            }
        }
    }

    // Pick based on highest bitrate
    private fun getBestRepresentation(ai: Int, aset: Element): Pick? {
        val reps = aset.kids("Representation")
        val ri = reps.indices.maxByOrNull {
            reps[it].attr("bandwidth").toLongOrNull() ?: 0L
        } ?: return null

        val rep = reps[ri]
        fun inherited(name: String): String {
            return rep.attr(name).ifEmpty { aset.attr(name) }
        }

        val hint = listOf(
            aset.attr("contentType"),
            inherited("mimeType"),
            inherited("codecs"),
        )
            .joinToString(" ")
            .lowercase()

        val kind = when {
            "video" in hint -> Kind.VIDEO
            "audio" in hint -> Kind.AUDIO
            listOf("text", "ttml", "stpp", "wvtt").any { it in hint } -> Kind.SUBTITLE
            else -> Kind.OTHER
        }

        return Pick(
            kind = kind,
            adaptationSet = ai,
            representation = ri,
            bandwidth = inherited("bandwidth").toLongOrNull() ?: 0L,
        )
    }

    private fun String.asXml(manifestUrl: String): Document {
        return Jsoup.parse(this, manifestUrl, org.jsoup.parser.Parser.xmlParser())
    }

    private fun prune(doc: Document, p: Pick) {
        val period = doc.selectFirst("MPD")!!.kids("Period").single()
        period.kids("AdaptationSet").filterIndexed { i, _ ->
            i != p.adaptationSet
        }.forEach { it.remove() }

        val aset = period.kids("AdaptationSet").single()
        aset.kids("Representation").filterIndexed { i, _ ->
            i != p.representation
        }.forEach { it.remove() }
    }
}

// Thanks, gpt
private class Parser(
    private val manifestUrl: String,
    private val prefix: String,
) {
    private data class Ref(
        val url: String,
        val start: Long? = null,
        val end: Long? = null,
    )

    private class SegmentInfo(
        val levels: List<Element>,
        val tag: String,
    ) {
        fun attr(name: String): String? {
            return levels.firstNotNullOfOrNull {
                it.kids(tag).firstOrNull()?.attr(name)?.ifEmpty { null }
            }
        }

        val timeline = levels.firstNotNullOfOrNull {
            it.kids(tag).firstOrNull()?.kids("SegmentTimeline")?.firstOrNull()
        }

        val element
            get() = levels.firstNotNullOf { it.kids(tag).firstOrNull() }
    }

    private val fragments = LinkedHashMap<Ref, DownloadFragment>()
    private val usedNames = HashSet<String>()

    fun parse(document: Document): TrackResult? {
        val mpd = document.selectFirst("MPD")!!
        val period = mpd.kids("Period").single()
        val aset = period.kids("AdaptationSet").single()
        val rep = aset.kids("Representation").single()

        val base = listOf(mpd, period, aset, rep).fold(manifestUrl) { b, el -> resolveBaseUrl(b, el) }
        val levels = listOf(rep, aset, period)
        val tag = listOf("SegmentTemplate", "SegmentList").firstOrNull { t -> levels.any { it.kids(t).isNotEmpty() } }
        if (tag == null) {
            return if (base == manifestUrl) null else TrackResult.Url(base, prefix)
        }

        val segInfo = SegmentInfo(levels, tag)
        val (init, media) = if (tag == "SegmentTemplate") {
            templateRefs(segInfo, base, rep.attr("id"), rep.attr("bandwidth"), periodSeconds(mpd, period))
        } else {
            listRefs(segInfo, base)
        }

        val stale = levels.flatMap { it.kids("SegmentTemplate") + it.kids("SegmentList") }
        val list = rep.appendElement("SegmentList")
        for (name in listOf("timescale", "duration", "startNumber", "presentationTimeOffset")) {
            segInfo.attr(name)?.let { list.attr(name, it) }
        }
        init?.let { list.appendElement("Initialization").attr("sourceURL", register(it).name) }
        segInfo.timeline?.let { list.appendChild(it.clone()) }
        media.forEach { list.appendElement("SegmentURL").attr("media", register(it).name) }

        document.select("BaseURL").remove()
        stale.forEach { it.remove() }
        return TrackResult.Playlist(
            type = DownloadType.Dash,
            content = document.outerHtml(),
            fragments = fragments.values.toList(),
            name = prefix,
        )
    }

    private fun templateRefs(
        s: SegmentInfo,
        base: String,
        id: String,
        bw: String,
        periodSec: Double?,
    ): Pair<Ref?, List<Ref>> {
        val scale = s.attr("timescale")?.toDouble() ?: 1.0
        val first = s.attr("startNumber")?.toLong() ?: 1L

        val times = mutableListOf<Long>()
        if (s.timeline != null) {
            var t = 0L
            for (e in s.timeline.kids("S")) {
                e.attr("t").toLongOrNull()?.let { t = it }
                val d = e.attr("d").toLong()
                val r = e.attr("r").toLongOrNull() ?: 0L
                val repeats = if (r >= 0) {
                    r
                } else {
                    ceil(
                        ((periodSec ?: throw Exception("Negative S@r needs a known duration")) * scale - t) / d,
                    ).toLong() -
                        1
                }
                repeat((repeats + 1).toInt()) {
                    times += t
                    t += d
                }
            }
        } else {
            val d = s.attr("duration")?.toLong() ?: throw Exception("'$id' has no duration or SegmentTimeline")
            val count = ceil((periodSec ?: throw Exception("Unknown duration for '$id'")) * scale / d).toInt()
            times += (0 until count).map { it * d }
        }

        val media = s.attr("media") ?: throw Exception("'$id' has no media template")
        val init = s.attr("initialization")?.let { Ref(resolveUrl(base, expand(it, id, bw, null, null))) }
        return init to times.mapIndexed { i, t -> Ref(resolveUrl(base, expand(media, id, bw, first + i, t))) }
    }

    private fun expand(template: String, id: String, bandwidth: String, number: Long?, time: Long?): String =
        tokenRegex.replace(template) { m ->
            if (m.value == "$$") return@replace "$"
            val width = m.groupValues[2].toIntOrNull()
            fun num(v: Long?) = (v ?: throw Exception("${m.value} not allowed here")).let {
                if (width != null) "%0${width}d".format(it) else it.toString()
            }
            when (m.groupValues[1]) {
                "RepresentationID" -> id
                "Bandwidth" -> bandwidth
                "Number" -> num(number)
                else -> num(time)
            }
        }

    private fun listRefs(s: SegmentInfo, base: String): Pair<Ref?, List<Ref>> {
        fun ref(el: Element, urlAttr: String, rangeAttr: String): Ref {
            val range = rangeRegex.matchEntire(el.attr(rangeAttr).trim())
            val url = el.attr(urlAttr).ifEmpty { null }?.let { resolveUrl(base, it) } ?: base
            return Ref(url, range?.groupValues?.get(1)?.toLong(), range?.groupValues?.get(2)?.toLongOrNull())
        }

        val el = s.element
        return el.kids("Initialization").firstOrNull()?.let { ref(it, "sourceURL", "range") } to
            el.kids("SegmentURL").map { ref(it, "media", "mediaRange") }
    }

    private fun register(ref: Ref): DownloadFragment = fragments.getOrPut(ref) {
        var name = URI(ref.url).path.substringAfterLast('/').ifEmpty { "segment" }
        if (ref.start != null) {
            val stem = name.substringBeforeLast('.')
            name = "${stem}_${ref.start}-${ref.end ?: ""}${name.removePrefix(stem)}"
        }
        var unique = "${prefix}_$name"
        var i = 1
        while (!usedNames.add(unique)) unique = "${name}_${i++}_$name"
        DownloadFragment(unique, ref.url, ref.start, ref.end)
    }

    private fun periodSeconds(mpd: Element, period: Element): Double? =
        period.attr("duration").parseSeconds()
            ?: mpd.attr("mediaPresentationDuration").parseSeconds()
                ?.minus(period.attr("start").parseSeconds() ?: 0.0)

    private fun resolveBaseUrl(parent: String, el: Element): String {
        return el.kids("BaseUrl").firstOrNull()
            ?.text()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let {
                resolveUrl(parent, it)
            }
            ?: parent
    }

    private fun String.parseSeconds(): Double? =
        takeIf { it.isNotBlank() }?.let { Duration.parse(it.uppercase()).toMillis() / 1000.0 }

    companion object {
        private val rangeRegex = Regex("""(\d+)-(\d*)""")
        private val tokenRegex = Regex("""\$(RepresentationID|Number|Bandwidth|Time)(?:%0(\d+)d)?\$|\$\$""")
    }
}

private fun Element.kids(name: String): List<Element> =
    children().filter { it.normalName() == name.lowercase() }
