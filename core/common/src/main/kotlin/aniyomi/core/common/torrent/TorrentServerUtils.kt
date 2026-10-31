package aniyomi.core.common.torrent

import aniyomi.core.common.torrent.model.Torrent
import dev.zacsweers.metro.Inject
import java.io.File
import java.net.URLEncoder

@Inject
class TorrentServerUtils(
    preferences: TorrentPreferences,
    private val api: TorrentServerApi,
) {
    private val animeTrackers = preferences.torrServerTrackers.get()
        .split("\n")
        .joinToString(",\n")

    fun getTorrentPlayLink(torr: Torrent, index: Int): String {
        val file = torr.fileStats?.firstOrNull {
            it.id == index
        }
        val name = file?.let { File(it.path).name } ?: torr.title
        return "http://127.0.0.1:${api.getPort()}/stream/${name.urlEncode()}?link=${torr.hash}&index=$index&play"
    }

    private fun String.urlEncode(): String = URLEncoder.encode(this, "utf8")
}
