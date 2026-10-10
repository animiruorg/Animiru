package eu.kanade.tachiyomi.data.download.downloader

import com.hippo.unifile.UniFile
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.ProgressListener
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.newCachelessCallWithProgress
import eu.kanade.tachiyomi.util.storage.saveTo
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import okhttp3.Headers
import okhttp3.OkHttpClient
import okio.Throttler
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

data class DownloadFragment(
    val name: String,
    val url: String,
    val start: Long? = null,
    val end: Long? = null,
)

@Inject
@SingleIn(AppScope::class)
class HttpDownloader {
    suspend fun downloadFragment(
        headers: Headers?,
        client: OkHttpClient,
        fragment: DownloadFragment,
        listener: ProgressListener,
        throttler: Throttler?,
        destDir: UniFile,
    ): UniFile {
        return downloadFile(
            url = fragment.url,
            headersBuilder = { file ->
                val headersBuilder = (headers ?: Headers.EMPTY).newBuilder()
                if (fragment.start != null) {
                    val range = buildString {
                        append(fragment.start + file.length())
                        append('-')
                        fragment.end?.let {
                            append(it)
                        }
                    }

                    headersBuilder.set("Range", "bytes=$range")
                }
                headersBuilder.build()
            },
            client = client,
            listener = listener,
            throttler = throttler,
            destDir = destDir,
            fileName = fragment.name,
        )
    }

    suspend fun downloadFile(
        url: String,
        headersBuilder: (UniFile) -> Headers,
        client: OkHttpClient,
        listener: ProgressListener,
        throttler: Throttler?,
        destDir: UniFile,
        fileName: String,
    ): UniFile {
        return flow {
            val file = destDir.findFile("$fileName.tmp")
                ?: destDir.createFile("$fileName.tmp")!!

            val headers = headersBuilder(file)

            try {
                client.newCachelessCallWithProgress(
                    request = GET(url, headers),
                    listener = listener,
                    existingSize = file.length(),
                    throttler = throttler,
                )
                    .awaitSuccess()
                    .use { response ->
                        response.body.source().saveTo(
                            // If the server supports partial downloads (HTTP 206),
                            // append to the existing file.
                            // Otherwise, start from scratch and overwrite the file.
                            stream = file.openOutputStream(response.code == 206),
                        )
                        file.renameTo(fileName)
                    }
            } catch (e: HttpException) {
                if (e.code == 416) {
                    file.delete()
                }
                throw e
            }
            emit(file)
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
            .first()
    }

    suspend fun downloadPlaylist(
        headers: Headers,
        client: OkHttpClient,
        playlist: String,
        fragments: List<DownloadFragment>,
        progress: ItemProgress,
        throttler: Throttler?,
        threadCount: Int,
        destDir: UniFile,
        name: String,
        ffmpegName: String,
        ffmpegType: String,
    ): String {
        val downloaded = destDir.listFiles().orEmpty().mapNotNull { it.name }.toHashSet()
        val fragmentQueue = ConcurrentLinkedQueue(fragments.filter { it.name !in downloaded })

        val totalItems = fragments.size
        val threadCount = threadCount.coerceIn(1, fragmentQueue.size.coerceAtLeast(1))

        val finished = AtomicInteger(totalItems - fragmentQueue.size)
        val downloading = ConcurrentHashMap<String, Float>()

        fun report() {
            if (totalItems == 0) {
                progress.report(1f)
                return
            }
            val prog = (finished.get() + downloading.values.sum()) / totalItems
            progress.report(prog)
        }

        report()

        coroutineScope {
            List(threadCount) {
                launch {
                    val job = currentCoroutineContext().job
                    while (isActive) {
                        val fragment = fragmentQueue.poll() ?: break
                        downloadFragment(
                            headers = headers,
                            client = client,
                            fragment = fragment,
                            listener = object : ProgressListener {
                                override fun update(bytesRead: Long, contentLength: Long, done: Boolean) {
                                    job.ensureActive()
                                    if (contentLength > 0) {
                                        val progress = (bytesRead.toFloat() / contentLength).coerceIn(0f, 1f)
                                        downloading[fragment.name] = progress
                                        report()
                                    }
                                }
                            },
                            throttler = throttler,
                            destDir = destDir,
                        )
                        downloading[fragment.name] = 1f
                    }
                }
            }.joinAll()
        }

        destDir.findFile("$name-$ffmpegName")?.delete()
        val index = destDir.createFile("$name-$ffmpegName")!!
        index.openOutputStream().use { output ->
            output.write(playlist.toByteArray())
        }

        progress.report(1f)

        return listOf(
            "-allowed_extensions ALL",
            "-f",
            ffmpegType,
            "-i",
            "\"${index.filePath!!}\"",
        )
            .filter(String::isNotEmpty)
            .joinToString(" ")
    }
}
