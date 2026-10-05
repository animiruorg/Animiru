package eu.kanade.tachiyomi.data.download.downloader

import com.hippo.unifile.UniFile
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.ProgressListener
import eu.kanade.tachiyomi.network.get
import eu.kanade.tachiyomi.util.storage.size
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import okhttp3.Headers

@Inject
@SingleIn(AppScope::class)
class DirectDownloader(
    private val networkHelper: NetworkHelper,
    private val downloader: HttpDownloader,
) {
    private val client = networkHelper.client

    suspend fun download(
        url: String,
        headers: Headers,
        destDir: UniFile,
        name: String,
        forceSingle: Boolean,
    ): String {
        // TODO(dl): Update download size
        var size = -1L
        var supportsRanges = false

        if (size <= 0 && !forceSingle) {
            val probeHeaders = headers.newBuilder()
                .set("Range", "bytes=0-0")
                .build()
            try {
                client.get(url, probeHeaders).use {
                    supportsRanges = it.code == 206

                    val contentRange = it.header("Content-Range")
                    size = if (contentRange != null) {
                        contentRange.substringAfterLast("/").toLongOrNull() ?: -1L
                    } else {
                        it.header("Content-Length")?.toLongOrNull() ?: -1L
                    }
                }
            } catch (_: Exception) { }

            // TODO(dl): Update download size
            // download.totalSize = size
        }

        val threadCount = 64

        return if (!forceSingle && size > 0 && supportsRanges && threadCount > 1) {
            val partSize = size / threadCount

            coroutineScope {
                List(threadCount) {
                    async {
                        if (destDir.findFile("$name-part$it")?.exists() == true) {
                            // Already downloaded
                            return@async
                        }

                        val job = currentCoroutineContext().job

                        val listener = object : ProgressListener {
                            override fun update(bytesRead: Long, contentLength: Long, done: Boolean) {
                                job.ensureActive()
                            }
                        }

                        downloader.downloadFile(
                            url = url,
                            headersBuilder = { part ->
                                val start = it * partSize + part.size()
                                val end = if (it == threadCount - 1) {
                                    size - 1
                                } else {
                                    (it + 1) * partSize - 1
                                }

                                headers.newBuilder()
                                    .set("Range", "bytes=$start-$end")
                                    .build()
                            },
                            listener = listener,
                            destDir = destDir,
                            fileName = "$name-part$it",
                        )
                    }
                }.joinAll()
            }

            val parts = (0 until threadCount).joinToString("|") { "${destDir.filePath!!}/$name-part$it" }
            "-i \"concat:$parts\""
        } else {
            var oldProg = 0
            val job = currentCoroutineContext().job

            val file = downloader.downloadFile(
                url = url,
                headersBuilder = { headers },
                listener = object : ProgressListener {
                    override fun update(bytesRead: Long, contentLength: Long, done: Boolean) {
                        // TODO(dl):
                        job.ensureActive()
                        val progress = if (contentLength > 0) {
                            (100 * (bytesRead.toFloat() / contentLength)).toInt()
                        } else {
                            -1
                        }
                        oldProg = progress
                    }
                },
                destDir = destDir,
                fileName = name,
            )

            "-i \"${file.filePath!!}\""
        }
    }
}
