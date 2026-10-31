package eu.kanade.tachiyomi.torrentutils

import animiru.domain.torrent.service.TorrentUtilsHolder
import animiru.domain.torrent.service.TorrentUtilsImpl
import eu.kanade.tachiyomi.torrentutils.model.TorrentInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

object TorrentUtils {

    private val impl: TorrentUtilsImpl by lazy { TorrentUtilsHolder.torrentUtilsImpl }

    suspend fun getTorrentInfo(
        url: String,
        title: String,
    ): TorrentInfo {
        return impl.getTorrentInfo(url, title)
    }

    // Compatibility for older non-blocking calls
    @Deprecated(
        message = "This method exists only for bytecode compatibility with older extensions. Do not use.",
        level = DeprecationLevel.HIDDEN,
    )
    @JvmName("getTorrentInfo")
    fun blockingShimForGetTorrentInfo(
        url: String,
        title: String,
    ): TorrentInfo {
        return runBlocking(Dispatchers.IO) {
            impl.getTorrentInfo(url, title)
        }
    }
}
