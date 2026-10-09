package eu.kanade.tachiyomi.data.download.model

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.episode.model.Episode

data class Download(
    val source: AnimeHttpSource,
    val anime: Anime,
    val episode: Episode,
    // AY -->
    val changeDownloader: Boolean = false,
    var video: Video? = null,
    // <-- AY
) {

    @Transient
    private val _statusFlow = MutableStateFlow(State.NOT_DOWNLOADED)

    @Transient
    val statusFlow = _statusFlow.asStateFlow()
    var status: State
        get() = _statusFlow.value
        set(status) {
            _statusFlow.value = status
        }

    // AY -->
    @Transient
    private val progressStateFlow = MutableStateFlow(0)

    @Transient
    val progressFlow = progressStateFlow.asStateFlow()
    var progress: Int
        get() = progressStateFlow.value
        set(value) {
            progressStateFlow.value = value
        }
    // <-- AY

    var totalSize: Long = -1L

    enum class State(val value: Int) {
        NOT_DOWNLOADED(0),
        QUEUE(1),
        DOWNLOADING(2),
        DOWNLOADED(3),
        ERROR(4),
    }
}
