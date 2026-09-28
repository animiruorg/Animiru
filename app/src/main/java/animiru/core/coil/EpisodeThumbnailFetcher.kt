package animiru.core.coil

import androidx.core.net.toUri
import animiru.core.cache.ThumbnailCache
import animiru.domain.episode.model.EpisodeThumbnail
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.await
import logcat.LogPriority
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.Request
import okhttp3.Response
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import okio.Source
import okio.buffer
import okio.sink
import okio.source
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.source.service.SourceManager
import java.io.File
import java.io.IOException

/**
 * A [Fetcher] that fetches thumbnail image for [Episode] object
 *
 * It uses [Episode.preview_url].
 * Disk caching for library items is handled by [ThumbnailCache], otherwise
 * handled by Coil's [DiskCache].
 */
class EpisodeThumbnailFetcher(
    private val url: String?,
    private val isLibraryAnime: Boolean,
    private val options: Options,
    private val thumbnailFileLazy: Lazy<File?>,
    private val diskCacheKeyLazy: Lazy<String>,
    private val sourceLazy: Lazy<AnimeHttpSource?>,
    private val callFactoryLazy: Lazy<Call.Factory>,
    private val imageLoader: ImageLoader,
) : Fetcher {

    private val diskCacheKey: String
        get() = diskCacheKeyLazy.value

    override suspend fun fetch(): FetchResult? {
        if (url == null) error("No thumbnail specified")
        return when (getResourceType(url)) {
            Type.File -> fileLoader(File(url.substringAfter("file://")))
            Type.URI -> fileUriLoader(url)
            Type.URL -> httpLoader()
            null -> error("Invalid image")
        }
    }

    private fun fileLoader(file: File): FetchResult {
        return SourceFetchResult(
            source = ImageSource(
                file = file.toOkioPath(),
                fileSystem = FileSystem.SYSTEM,
                diskCacheKey = diskCacheKey,
            ),
            mimeType = "image/*",
            dataSource = DataSource.DISK,
        )
    }

    private fun fileUriLoader(uri: String): FetchResult {
        val source = UniFile.fromUri(options.context, uri.toUri())!!
            .openInputStream()
            .source()
            .buffer()
        return SourceFetchResult(
            source = ImageSource(source = source, fileSystem = FileSystem.SYSTEM),
            mimeType = "image/*",
            dataSource = DataSource.DISK,
        )
    }

    private suspend fun httpLoader(): FetchResult {
        // Only cache separately if it's a library item
        val libraryThumbnailCacheFile = if (isLibraryAnime) {
            thumbnailFileLazy.value ?: error("No thumbnail specified")
        } else {
            null
        }
        if (libraryThumbnailCacheFile?.exists() == true && options.diskCachePolicy.readEnabled) {
            return fileLoader(libraryThumbnailCacheFile)
        }

        var snapshot = readFromDiskCache()
        try {
            // Fetch from disk cache
            if (snapshot != null) {
                val snapshotThumbnailCache = moveSnapshotToThumbnailCache(snapshot, libraryThumbnailCacheFile)
                if (snapshotThumbnailCache != null) {
                    // Read from thumbnail cache after added to library
                    return fileLoader(snapshotThumbnailCache)
                }

                // Read from snapshot
                return SourceFetchResult(
                    source = snapshot.toImageSource(),
                    mimeType = "image/*",
                    dataSource = DataSource.DISK,
                )
            }

            // Fetch from network
            val response = executeNetworkRequest()
            val responseBody = checkNotNull(response.body) { "Null response source" }
            try {
                // Read from thumbnail cache after library episode thumbnail updated
                val responseThumbnailCache = writeResponseToThumbnailCache(response, libraryThumbnailCacheFile)
                if (responseThumbnailCache != null) {
                    return fileLoader(responseThumbnailCache)
                }

                // Read from disk cache
                snapshot = writeToDiskCache(response)
                if (snapshot != null) {
                    return SourceFetchResult(
                        source = snapshot.toImageSource(),
                        mimeType = "image/*",
                        dataSource = DataSource.NETWORK,
                    )
                }

                // Read from response if cache is unused or unusable
                return SourceFetchResult(
                    source = ImageSource(source = responseBody.source(), fileSystem = FileSystem.SYSTEM),
                    mimeType = "image/*",
                    dataSource = if (response.cacheResponse != null) DataSource.DISK else DataSource.NETWORK,
                )
            } catch (e: Exception) {
                responseBody.close()
                throw e
            }
        } catch (e: Exception) {
            snapshot?.close()
            throw e
        }
    }

    private suspend fun executeNetworkRequest(): Response {
        val client = sourceLazy.value?.client ?: callFactoryLazy.value
        val response = client.newCall(newRequest()).await()
        if (!response.isSuccessful && response.code != HTTP_NOT_MODIFIED) {
            response.close()
            throw IOException(response.message)
        }
        return response
    }

    private fun newRequest(): Request {
        val request = Request.Builder().apply {
            url(url!!)

            val sourceHeaders = sourceLazy.value?.headers
            if (sourceHeaders != null) {
                headers(sourceHeaders)
            }
        }

        when {
            options.networkCachePolicy.readEnabled -> {
                // don't take up okhttp cache
                request.cacheControl(CACHE_CONTROL_NO_STORE)
            }
            else -> {
                // This causes the request to fail with a 504 Unsatisfiable Request.
                request.cacheControl(CACHE_CONTROL_NO_NETWORK_NO_CACHE)
            }
        }

        return request.build()
    }

    private fun moveSnapshotToThumbnailCache(snapshot: DiskCache.Snapshot, cacheFile: File?): File? {
        if (cacheFile == null) return null
        return try {
            imageLoader.diskCache?.run {
                fileSystem.source(snapshot.data).use { input ->
                    writeSourceToThumbnaulCache(input, cacheFile)
                }
                remove(diskCacheKey)
            }
            cacheFile.takeIf { it.exists() }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to write snapshot data to thumbnail cache ${cacheFile.name}" }
            null
        }
    }

    private fun writeResponseToThumbnailCache(response: Response, cacheFile: File?): File? {
        if (cacheFile == null || !options.diskCachePolicy.writeEnabled) return null
        return try {
            response.peekBody(Long.MAX_VALUE).source().use { input ->
                writeSourceToThumbnaulCache(input, cacheFile)
            }
            cacheFile.takeIf { it.exists() }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to write response data to thumbnail cache ${cacheFile.name}" }
            null
        }
    }

    private fun writeSourceToThumbnaulCache(input: Source, cacheFile: File) {
        cacheFile.parentFile?.mkdirs()
        cacheFile.delete()
        try {
            cacheFile.sink().buffer().use { output ->
                output.writeAll(input)
            }
        } catch (e: Exception) {
            cacheFile.delete()
            throw e
        }
    }

    private fun readFromDiskCache(): DiskCache.Snapshot? {
        return if (options.diskCachePolicy.readEnabled) {
            imageLoader.diskCache?.openSnapshot(diskCacheKey)
        } else {
            null
        }
    }

    private fun writeToDiskCache(
        response: Response,
    ): DiskCache.Snapshot? {
        val diskCache = imageLoader.diskCache
        val editor = diskCache?.openEditor(diskCacheKey) ?: return null
        try {
            diskCache.fileSystem.write(editor.data) {
                response.body.source().readAll(this)
            }
            return editor.commitAndOpenSnapshot()
        } catch (e: Exception) {
            try {
                editor.abort()
            } catch (ignored: Exception) {
            }
            throw e
        }
    }

    private fun DiskCache.Snapshot.toImageSource(): ImageSource {
        return ImageSource(
            file = data,
            fileSystem = FileSystem.SYSTEM,
            diskCacheKey = diskCacheKey,
            closeable = this,
        )
    }

    private fun getResourceType(thumbnail: String?): Type? {
        return when {
            thumbnail.isNullOrEmpty() -> null
            thumbnail.startsWith("http", true) || thumbnail.startsWith("Custom-", true) -> Type.URL
            thumbnail.startsWith("/") || thumbnail.startsWith("file://") -> Type.File
            thumbnail.startsWith("content") -> Type.URI
            else -> null
        }
    }

    private enum class Type {
        File,
        URI,
        URL,
    }

    class EpisodeFactory(
        private val callFactoryLazy: Lazy<Call.Factory>,
        private val thumbnailCache: ThumbnailCache,
        private val sourceManager: SourceManager,
    ) : Fetcher.Factory<EpisodeThumbnail> {

        override fun create(data: EpisodeThumbnail, options: Options, imageLoader: ImageLoader): Fetcher? {
            return EpisodeThumbnailFetcher(
                url = data.url,
                isLibraryAnime = data.isLibraryAnime,
                options = options,
                thumbnailFileLazy = lazy { thumbnailCache.getThumbnailFile(data.url) },
                diskCacheKeyLazy = lazy { imageLoader.components.key(data, options)!! },
                sourceLazy = lazy { sourceManager.get(data.sourceId) as? AnimeHttpSource },
                callFactoryLazy = callFactoryLazy,
                imageLoader = imageLoader,
            )
        }
    }

    companion object {
        private val CACHE_CONTROL_NO_STORE = CacheControl.Builder().noStore().build()
        private val CACHE_CONTROL_NO_NETWORK_NO_CACHE = CacheControl.Builder().noCache().onlyIfCached().build()

        private const val HTTP_NOT_MODIFIED = 304
    }
}
