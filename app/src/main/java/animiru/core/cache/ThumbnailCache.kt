package animiru.core.cache

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.util.storage.DiskUtil
import java.io.File

/**
 * Class used to create episode thumbnail cache.
 * It is used to store the episode thumbnails.
 * Names of files are created with the md5 of the thumbnail URL.
 *
 * @param context the application context.
 * @constructor creates an instance of the thumbnail cache.
 */
@Inject
@SingleIn(AppScope::class)
class ThumbnailCache(private val context: Context) {

    companion object {
        private const val THUMBNAILS_DIR = "thumbnails"
    }

    /**
     * Cache directory used for cache management.
     */
    private val cacheDir = getCacheDir(THUMBNAILS_DIR)

    /**
     * Returns the thumbnail from cache.
     *
     * @param episodeThumbnailUrl the episode thumbnail url.
     * @return thumbnail image.
     */
    fun getThumbnailFile(episodeThumbnailUrl: String?): File? {
        return episodeThumbnailUrl?.let {
            File(cacheDir, DiskUtil.hashKeyForDisk(it))
        }
    }

    /**
     * Delete the thumbnail file from the cache.
     *
     * @param thumbnailUrl the thumbnail url.
     * @return number of files that were deleted.
     */
    fun deleteFromCache(thumbnailUrl: String): Int {
        var deleted = 0

        getThumbnailFile(thumbnailUrl)?.let {
            if (it.exists() && it.delete()) ++deleted
        }

        return deleted
    }

    private fun getCacheDir(dir: String): File {
        return context.getExternalFilesDir(dir)
            ?: File(context.filesDir, dir).also { it.mkdirs() }
    }
}
