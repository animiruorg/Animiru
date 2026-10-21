package eu.kanade.tachiyomi.data.cache

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.util.storage.DiskUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mihon.core.metro.AppCoroutineScope
import tachiyomi.domain.anime.model.Anime
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Class used to create cover cache.
 * It is used to store the covers of the library.
 * Names of files are created with the md5 of the thumbnail URL.
 *
 * @param context the application context.
 * @constructor creates an instance of the cover cache.
 */
@Inject
@SingleIn(AppScope::class)
class CoverCache(
    @AppCoroutineScope private val scope: CoroutineScope,
    private val context: Context,
) {

    companion object {
        private const val COVERS_DIR = "covers"
        private const val CUSTOM_COVERS_DIR = "covers/custom"
    }

    /**
     * Cache directory used for cache management.
     */
    private val cacheDir by lazy { getCacheDir(COVERS_DIR) }

    private val customCoverCacheDir by lazy { getCacheDir(CUSTOM_COVERS_DIR) }

    private val customCoverNames = mutableSetOf<String>()

    // Deleted while the directory was being listed, so the listing may still contain them
    private val deletedWhileIndexing = mutableSetOf<String>()

    @Volatile
    private var customCoversIndexed = false

    init {
        scope.launch {
            val names = customCoverCacheDir.list().orEmpty()
            synchronized(customCoverNames) {
                names.filterNotTo(customCoverNames) { it in deletedWhileIndexing }
                deletedWhileIndexing.clear()
                customCoversIndexed = true
            }
        }
    }

    /**
     * Returns the cover from cache.
     *
     * @param animeThumbnailUrl thumbnail url for the anime.
     * @return cover image.
     */
    fun getCoverFile(animeThumbnailUrl: String?): File? {
        return animeThumbnailUrl?.let {
            File(cacheDir, DiskUtil.hashKeyForDisk(it))
        }
    }

    /**
     * Returns the custom cover from cache.
     *
     * @param animeId the anime id.
     * @return cover image.
     */
    fun getCustomCoverFile(animeId: Long?): File {
        return File(customCoverCacheDir, DiskUtil.hashKeyForDisk(animeId.toString()))
    }

    fun hasCustomCover(animeId: Long?): Boolean {
        val file = getCustomCoverFile(animeId)
        if (!customCoversIndexed) return file.exists()
        return synchronized(customCoverNames) { file.name in customCoverNames }
    }

    /**
     * Saves the given stream as the anime's custom cover to cache.
     *
     * @param anime the anime.
     * @param inputStream the stream to copy.
     * @throws IOException if there's any error.
     */
    @Throws(IOException::class)
    fun setCustomCoverToCache(anime: Anime, inputStream: InputStream) {
        val file = getCustomCoverFile(anime.id)
        file.outputStream().use {
            inputStream.copyTo(it)
        }
        synchronized(customCoverNames) {
            customCoverNames.add(file.name)
            deletedWhileIndexing.remove(file.name)
        }
    }

    /**
     * Delete the cover files of the anime from the cache.
     *
     * @param anime the anime.
     * @param deleteCustomCover whether the custom cover should be deleted.
     * @return number of files that were deleted.
     */
    fun deleteFromCache(anime: Anime, deleteCustomCover: Boolean = false): Int {
        var deleted = 0

        getCoverFile(anime.thumbnailUrl)?.let {
            if (it.exists() && it.delete()) ++deleted
        }

        if (deleteCustomCover) {
            if (deleteCustomCover(anime.id)) ++deleted
        }

        return deleted
    }

    /**
     * Delete custom cover of the anime from the cache
     *
     * @param animeId the anime id.
     * @return whether the cover was deleted.
     */
    fun deleteCustomCover(animeId: Long?): Boolean {
        val file = getCustomCoverFile(animeId)
        val deleted = file.exists() && file.delete()
        synchronized(customCoverNames) {
            customCoverNames.remove(file.name)
            if (!customCoversIndexed) deletedWhileIndexing.add(file.name)
        }
        return deleted
    }

    private fun getCacheDir(dir: String): File {
        return context.getExternalFilesDir(dir)
            ?: File(context.filesDir, dir).also { it.mkdirs() }
    }
}
