package eu.kanade.tachiyomi.ui.player

import animiru.domain.player.model.VideoAspect
import dev.icerock.moko.resources.StringResource

enum class Sheets {
    None,
    PlaybackSpeed,
    SubtitleTracks,
    AudioTracks,
    QualityTracks,
    Chapters,
    More,
    Screenshot,
}

enum class Panels {
    None,
    SubtitleSettings,
    SubtitleDelay,
    AudioDelay,
    VideoFilters,
}

sealed interface Dialogs {
    data object None : Dialogs
    data object EpisodeList : Dialogs
    data class IntegerPicker(
        val defaultValue: Int,
        val minValue: Int,
        val maxValue: Int,
        val step: Int,
        val nameFormat: String,
        val title: String,
        val onChange: (Int) -> Unit,
        val onDismissRequest: () -> Unit,
    ) : Dialogs
}

sealed interface PlayerUpdates {
    data object None : PlayerUpdates
    data class DoubleSpeed(val speed: Float) : PlayerUpdates
    data class AspectRatio(val aspect: VideoAspect) : PlayerUpdates
    data class ShowText(val value: String) : PlayerUpdates
    data class ShowTextResource(val textResource: StringResource) : PlayerUpdates
    data class Transform(val zoom: Int) : PlayerUpdates
}
