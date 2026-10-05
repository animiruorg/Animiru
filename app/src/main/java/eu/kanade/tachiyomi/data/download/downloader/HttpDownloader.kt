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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.retryWhen
import okhttp3.Headers
import java.security.MessageDigest
import kotlin.time.Duration.Companion.seconds

data class DownloadFragment(
    val name: String,
    val url: String,
    val start: Long? = null,
    val end: Long? = null,
)

@Inject
@SingleIn(AppScope::class)
class HttpDownloader(
    private val networkHelper: NetworkHelper,
) {
    private val client = networkHelper.client

    suspend fun downloadFragment(
        headers: Headers?,
        fragment: DownloadFragment,
        listener: ProgressListener,
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
            listener = listener,
            destDir = destDir,
            fileName = fragment.name,
        )
    }

    suspend fun downloadFile(
        url: String,
        headersBuilder: (UniFile) -> Headers,
        listener: ProgressListener,
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

    fun md5(file: UniFile): String {
        val digest = MessageDigest.getInstance("MD5")

        file.openInputStream().use { input ->
            val buffer = ByteArray(8 * 1024)

            while (true) {
                val bytesRead = input.read(buffer)
                if (bytesRead == -1) break

                digest.update(buffer, 0, bytesRead)
            }
        }

        return digest.digest()
            .joinToString("") { "%02x".format(it) }
    }
}
