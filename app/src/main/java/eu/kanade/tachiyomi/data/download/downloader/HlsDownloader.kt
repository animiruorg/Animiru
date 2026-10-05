package eu.kanade.tachiyomi.data.download.downloader

import com.hippo.unifile.UniFile
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.ProgressListener
import eu.kanade.tachiyomi.network.get
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import okhttp3.Headers
import java.net.URI
import java.util.concurrent.ConcurrentLinkedQueue

@Inject
@SingleIn(AppScope::class)
class HlsDownloader(
    private val networkHelper: NetworkHelper,
    private val downloader: HttpDownloader,
) {
    private val client = networkHelper.client

    suspend fun download(
        url: String,
        headers: Headers,
        destDir: UniFile,
        name: String,
    ): String {
        val (playlist, fragments) = getSegments(url, headers, name)

        val downloaded = destDir.listFiles().orEmpty().mapNotNull { it.name }.toHashSet()
        val fragmentQueue = ConcurrentLinkedQueue(fragments.filter { it.name !in downloaded })

        val totalItems = fragments.size
        val threadCount = 5.coerceIn(1, fragmentQueue.size.coerceAtLeast(1))

        coroutineScope {
            List(threadCount) {
                launch {
                    val job = currentCoroutineContext().job
                    while (isActive) {
                        val fragment = fragmentQueue.poll() ?: break
                        downloader.downloadFragment(
                            headers = headers,
                            fragment = fragment,
                            listener = object : ProgressListener {
                                override fun update(bytesRead: Long, contentLength: Long, done: Boolean) {
                                    job.ensureActive()
                                    val progress = (100 * (bytesRead.toFloat() / contentLength)).toInt()
                                    // TODO(dl):
                                }
                            },
                            destDir = destDir,
                        )
                    }
                }
            }.joinAll()
        }

        destDir.findFile("$name-index")?.delete()
        val index = destDir.createFile("$name-index")!!
        index.openOutputStream().use { output ->
            output.write(playlist.toByteArray())
        }

        return "-f hls -i \"${index.filePath!!}\""
    }

    private suspend fun getSegments(
        playlistUrl: String,
        headers: Headers,
        name: String,
    ): Pair<String, List<DownloadFragment>> {
        val playlistContent = client.get(playlistUrl, headers).body.string()

        val fragments = mutableListOf<DownloadFragment>()
        val rewrittenPlaylist = StringBuilder()

        val keyMap = mutableMapOf<String, String>()

        var urlIndex = 0
        var keyIndex = 0
        var initIndex = 0

        var byteLen: Long? = null
        var byteOffset: Long? = null
        var hasByteOffset = false

        fun parseRange(range: String): Pair<Long, Long> {
            val len = range.substringBefore('@').toLong()
            val offset = range.substringAfter('@', "")
                .takeIf { it.isNotEmpty() }
                ?.toLong()

            byteOffset = offset ?: (byteOffset!! + byteLen!!)
            byteLen = len
            return byteOffset to len
        }

        playlistContent.lineSequence().forEach { raw ->
            val line = raw.trim()

            if (line.isBlank()) {
                rewrittenPlaylist.appendLine()
                return@forEach
            }

            if (line.startsWith('#')) {
                when {
                    line.startsWith("#EXT-X-BYTERANGE:") -> {
                        parseRange(line.substringAfter(':'))
                        hasByteOffset = true
                    }

                    line.startsWith("#EXT-X-KEY:") -> {
                        val rewritten = uriRegex.replace(line) { m ->
                            val uri = m.groupValues[1]
                            val newUri = if (uri.startsWith("data:", true)) {
                                uri
                            } else {
                                val fullUrl = resolveUrl(playlistUrl, uri)
                                if (keyMap.containsKey(fullUrl)) {
                                    keyMap[fullUrl]
                                } else {
                                    fragments.add(
                                        DownloadFragment(
                                            url = fullUrl,
                                            name = "$name-key$keyIndex",
                                        ),
                                    )
                                    keyMap[fullUrl] = "$name-key$keyIndex"
                                    "$name-key${keyIndex++}"
                                }
                            }
                            """URI="$newUri""""
                        }
                        rewrittenPlaylist.appendLine(rewritten)
                    }

                    line.startsWith("#EXT-X-MAP:") -> {
                        val rewritten = uriRegex.replace(line) { m ->
                            val uri = m.groupValues[1]
                            val newUri = if (uri.startsWith("data:", true)) {
                                uri
                            } else {
                                val fullUrl = resolveUrl(playlistUrl, uri)
                                val range = byteRangeRegex.find(line)?.groupValues?.get(1)
                                val (start, end) = range?.let(::parseRange) ?: Pair(null, null)

                                fragments.add(
                                    DownloadFragment(
                                        url = fullUrl,
                                        name = "$name-init$initIndex",
                                        start = start,
                                        end = end?.minus(1),
                                    ),
                                )
                                "$name-init${initIndex++}"
                            }
                            """URI="$newUri""""
                        }
                        rewrittenPlaylist.appendLine(
                            rewritten.replace(byteRangeRegex, "")
                                .trimEnd(',')
                                .replace(",,", ","),
                        )
                    }

                    line.startsWith("#EXT-X-PART:") ||
                        line.startsWith("#EXT-X-PRELOAD-HINT:") ||
                        line.startsWith("#EXT-X-RENDITION-REPORT:") -> {
                        // Ignore
                    }

                    else -> rewrittenPlaylist.appendLine(line)
                }

                return@forEach
            }

            val fullUrl = resolveUrl(playlistUrl, line)
            if (hasByteOffset) {
                fragments.add(
                    DownloadFragment(
                        url = fullUrl,
                        name = "$name-seg$urlIndex.ts",
                        start = byteOffset!!,
                        end = byteOffset + byteLen!! - 1,
                    ),
                )
            } else {
                fragments.add(
                    DownloadFragment(
                        url = fullUrl,
                        name = "$name-seg$urlIndex.ts",
                        start = null,
                        end = null,
                    ),
                )
            }
            rewrittenPlaylist.appendLine("$name-seg${urlIndex++}.ts")
            hasByteOffset = false
        }

        return Pair(rewrittenPlaylist.toString(), fragments)
    }

    private fun resolveUrl(base: String, relative: String): String =
        URI(base).resolve(relative).toString()

    companion object {
        private val uriRegex = Regex("""URI="([^"]+)"""")
        private val byteRangeRegex = Regex("""BYTERANGE="([^"]+)"""")
    }
}
