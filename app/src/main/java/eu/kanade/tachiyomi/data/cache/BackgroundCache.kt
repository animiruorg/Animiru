// AY -->
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
 * Class used to create background cache.
 * It is used to store the background of the library.
 * Names of files are created with the md5 of the background URL.
 *
 * @param context the application context.
 * @constructor creates an instance of the background cache.
 */
@Inject
@SingleIn(AppScope::class)
class BackgroundCache(
    @AppCoroutineScope private val scope: CoroutineScope,
    private val context: Context,
) {

    companion object {
        private const val BACKGROUNDS_DIR = "backgrounds"
        private const val CUSTOM_BACKGROUNDS_DIR = "backgrounds/custom"
    }

    /**
     * Cache directory used for cache management.
     */
    private val cacheDir by lazy { getCacheDir(BACKGROUNDS_DIR) }

    private val customBackgroundCacheDir by lazy { getCacheDir(CUSTOM_BACKGROUNDS_DIR) }

    private val customBackgroundNames = mutableSetOf<String>()

    // Deleted while the directory was being listed, so the listing may still contain them
    private val deletedWhileIndexing = mutableSetOf<String>()

    @Volatile
    private var customBackgroundsIndexed = false

    init {
        scope.launch {
            val names = customBackgroundCacheDir.list().orEmpty()
            synchronized(customBackgroundNames) {
                names.filterNotTo(customBackgroundNames) { it in deletedWhileIndexing }
                deletedWhileIndexing.clear()
                customBackgroundsIndexed = true
            }
        }
    }

    /**
     * Returns the background from cache.
     *
     * @param animeBackgroundUrl the anime.
     * @return background image.
     */
    fun getBackgroundFile(animeBackgroundUrl: String?): File? {
        return animeBackgroundUrl?.let {
            File(cacheDir, DiskUtil.hashKeyForDisk(it))
        }
    }

    /**
     * Returns the custom background from cache.
     *
     * @param animeId the anime id.
     * @return background image.
     */
    fun getCustomBackgroundFile(animeId: Long?): File {
        return File(customBackgroundCacheDir, DiskUtil.hashKeyForDisk(animeId.toString()))
    }

    fun hasCustomBackground(animeId: Long?): Boolean {
        val file = getCustomBackgroundFile(animeId)
        if (!customBackgroundsIndexed) return file.exists()
        return synchronized(customBackgroundNames) { file.name in customBackgroundNames }
    }

    /**
     * Saves the given stream as the anime's custom background to cache.
     *
     * @param anime the anime.
     * @param inputStream the stream to copy.
     * @throws IOException if there's any error.
     */
    @Throws(IOException::class)
    fun setCustomBackgroundToCache(anime: Anime, inputStream: InputStream) {
        val file = getCustomBackgroundFile(anime.id)
        file.outputStream().use {
            inputStream.copyTo(it)
        }
        synchronized(customBackgroundNames) {
            customBackgroundNames.add(file.name)
            deletedWhileIndexing.remove(file.name)
        }
    }

    /**
     * Delete the background files of the anime from the cache.
     *
     * @param anime the anime.
     * @param deleteCustomBackground whether the custom background should be deleted.
     * @return number of files that were deleted.
     */
    fun deleteFromCache(anime: Anime, deleteCustomBackground: Boolean = false): Int {
        var deleted = 0

        getBackgroundFile(anime.backgroundUrl)?.let {
            if (it.exists() && it.delete()) ++deleted
        }

        if (deleteCustomBackground) {
            if (deleteCustomBackground(anime.id)) ++deleted
        }

        return deleted
    }

    /**
     * Delete custom background of the anime from the cache
     *
     * @param animeId the anime id.
     * @return whether the background was deleted.
     */
    fun deleteCustomBackground(animeId: Long?): Boolean {
        val file = getCustomBackgroundFile(animeId)
        val deleted = file.exists() && file.delete()
        synchronized(customBackgroundNames) {
            customBackgroundNames.remove(file.name)
            if (!customBackgroundsIndexed) deletedWhileIndexing.add(file.name)
        }
        return deleted
    }

    private fun getCacheDir(dir: String): File {
        return context.getExternalFilesDir(dir)
            ?: File(context.filesDir, dir).also { it.mkdirs() }
    }
}
// <-- AY
