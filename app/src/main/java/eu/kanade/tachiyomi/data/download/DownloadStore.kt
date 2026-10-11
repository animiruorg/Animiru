package eu.kanade.tachiyomi.data.download

import android.content.Context
import androidx.core.content.edit
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.data.download.model.Download
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import tachiyomi.domain.anime.interactor.GetAnime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.episode.interactor.GetEpisode
import tachiyomi.domain.source.service.SourceManager

/**
 * This class is used to persist active downloads across application restarts.
 */
@Inject
@SingleIn(AppScope::class)
class DownloadStore(
    context: Context,
    private val sourceManager: SourceManager,
    private val json: Json,
    private val getAnime: GetAnime,
    private val getEpisode: GetEpisode,
) {

    /**
     * Preference file where active downloads are stored.
     */
    private val preferences = context.getSharedPreferences("active_downloads", Context.MODE_PRIVATE)

    /**
     * Counter used to keep the queue order.
     */
    private var counter = 0

    /**
     * Adds a list of downloads to the store.
     *
     * @param downloads the list of downloads to add.
     */
    fun addAll(downloads: List<Download>) {
        preferences.edit {
            downloads.forEach { putString(getKey(it), serialize(it)) }
        }
    }

    /**
     * Removes a download from the store.
     *
     * @param download the download to remove.
     */
    fun remove(download: Download) {
        preferences.edit {
            remove(getKey(download))
        }
    }

    /**
     * Removes a list of downloads from the store.
     *
     * @param downloads the download to remove.
     */
    fun removeAll(downloads: List<Download>) {
        preferences.edit {
            downloads.forEach { remove(getKey(it)) }
        }
    }

    /**
     * Removes all the downloads from the store.
     */
    fun clear() {
        preferences.edit {
            clear()
        }
    }

    /**
     * Returns the preference's key for the given download.
     *
     * @param download the download.
     */
    private fun getKey(download: Download): String {
        return download.episode.id.toString()
    }

    /**
     * Returns the list of downloads to restore. It should be called in a background thread.
     */
    suspend fun restore(): List<Download> {
        val objs = preferences.all
            .mapNotNull { it.value as? String }
            .mapNotNull { deserialize(it) }
            .sortedBy { it.order }

        val downloads = mutableListOf<Download>()
        if (objs.isNotEmpty()) {
            val cachedAnime = mutableMapOf<Long, Anime?>()
            for ((animeId, episodeId) in objs) {
                val anime = cachedAnime.getOrPut(animeId) {
                    getAnime.await(animeId)
                } ?: continue
                val source = sourceManager.get(anime.source) as? AnimeHttpSource ?: continue
                val episode = getEpisode.await(episodeId) ?: continue
                downloads.add(Download(source, anime, episode))
            }
        }

        // Clear the store, downloads will be added again immediately.
        clear()
        return downloads
    }

    /**
     * Converts a download to a string.
     *
     * @param download the download to serialize.
     */
    private fun serialize(download: Download): String {
        val obj = DownloadObject(download.anime.id, download.episode.id, counter++)
        return json.encodeToString(obj)
    }

    /**
     * Restore a download from a string.
     *
     * @param string the download as string.
     */
    private fun deserialize(string: String): DownloadObject? {
        return try {
            json.decodeFromString<DownloadObject>(string)
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Class used for download serialization
 *
 * @param animeId the id of the anime.
 * @param episodeId the id of the episode.
 * @param order the order of the download in the queue.
 */
@Serializable
private data class DownloadObject(val animeId: Long, val episodeId: Long, val order: Int)
