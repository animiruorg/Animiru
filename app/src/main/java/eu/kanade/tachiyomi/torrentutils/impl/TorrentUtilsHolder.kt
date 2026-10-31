package animiru.domain.torrent.service

object TorrentUtilsHolder {
    lateinit var torrentUtilsImpl: TorrentUtilsImpl
        private set

    fun init(torrentUtilsImpl: TorrentUtilsImpl) {
        this.torrentUtilsImpl = torrentUtilsImpl
    }
}
