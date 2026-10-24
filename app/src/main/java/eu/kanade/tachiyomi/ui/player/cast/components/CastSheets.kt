package eu.kanade.tachiyomi.ui.player.cast.components

import androidx.compose.runtime.Composable
import dev.vivvvek.seeker.Segment
import eu.kanade.tachiyomi.ui.player.cast.CastSheet
import eu.kanade.tachiyomi.ui.player.cast.CastUiData
import eu.kanade.tachiyomi.ui.player.components.ChaptersSheet
import eu.kanade.tachiyomi.ui.player.components.HosterState
import eu.kanade.tachiyomi.ui.player.components.QualitySheet
import tachiyomi.cast.domain.TrackInformation
import tachiyomi.domain.episode.model.Episode

@Composable
fun CastSheets(
    sheetShown: CastSheet,

    // Quality sheet
    isLoadingHosters: Boolean,
    hosterState: List<HosterState>,
    expandedState: List<Boolean>,
    selectedVideoIndex: Pair<Int, Int>,
    onClickHoster: (Int) -> Unit,
    onClickVideo: (Int, Int) -> Unit,
    displayHosters: Pair<Boolean, Boolean>,

    // Tracks sheet
    castUiData: CastUiData,
    onSelectTrack: (TrackInformation, Boolean) -> Unit,

    // Chapter sheet
    onClickChapter: (Segment) -> Unit,

    // Speed sheet
    speed: Float,
    speedPresets: List<Float>,
    onSpeedChange: (Float) -> Unit,
    onAddSpeedPreset: (Float) -> Unit,
    onRemoveSpeedPreset: (Float) -> Unit,
    onResetSpeedPresets: () -> Unit,
    onMakeDefaultSpeed: (Float) -> Unit,
    onResetDefaultSpeed: () -> Unit,

    // Playlist sheet
    episodeDisplayMode: Long?,
    currentEpisodeIndex: Int,
    episodeList: List<Episode>,
    dateRelativeTime: Boolean,
    dateFormat: String,
    onBookmarkClicked: (Long?, Boolean) -> Unit,
    onFillermarkClicked: (Long?, Boolean) -> Unit,
    onEpisodeClicked: (Long?) -> Unit,

    onDismissRequest: () -> Unit,
    dismissSheet: Boolean,
) {
    when (sheetShown) {
        CastSheet.None -> { }
        CastSheet.Quality -> {
            QualitySheet(
                isLoadingHosters = isLoadingHosters,
                hosterState = hosterState,
                expandedState = expandedState,
                selectedVideoIndex = selectedVideoIndex,
                onClickHoster = onClickHoster,
                onClickVideo = onClickVideo,
                displayHosters = displayHosters,
                onDismissRequest = onDismissRequest,
                dismissSheet = dismissSheet,
            )
        }
        CastSheet.Audio -> {
            CastTrackSheet(
                isAudio = true,
                tracks = castUiData.audioTracks,
                selectedIndex = castUiData.currentAudioId,
                onSelect = { onSelectTrack(it, true) },
                onDismissRequest = onDismissRequest,
            )
        }
        CastSheet.Subtitle -> {
            CastTrackSheet(
                isAudio = false,
                tracks = castUiData.subTracks,
                selectedIndex = castUiData.currentSubId,
                onSelect = { onSelectTrack(it, false) },
                onDismissRequest = onDismissRequest,
            )
        }
        CastSheet.Chapter -> {
            if (castUiData.currentChapter == null) return
            ChaptersSheet(
                chapters = castUiData.chapters,
                currentChapter = castUiData.currentChapter,
                onClick = onClickChapter,
                onDismissRequest = onDismissRequest,
                dismissSheet = dismissSheet,
            )
        }
        CastSheet.PlaybackSpeed -> {
            CastPlaybackSpeedSheet(
                speed = speed,
                onSpeedChange = onSpeedChange,
                speedPresets = speedPresets,
                onAddSpeedPreset = onAddSpeedPreset,
                onRemoveSpeedPreset = onRemoveSpeedPreset,
                onResetPresets = onResetSpeedPresets,
                onMakeDefault = onMakeDefaultSpeed,
                onResetDefault = onResetDefaultSpeed,
                onDismissRequest = onDismissRequest,
            )
        }
        CastSheet.Playlist -> {
            CastPlaylistSheet(
                displayMode = episodeDisplayMode,
                currentEpisodeIndex = currentEpisodeIndex,
                episodeList = episodeList,
                dateRelativeFormat = dateRelativeTime,
                dateFormat = dateFormat,
                onBookmarkClicked = onBookmarkClicked,
                onFillermarkClicked = onFillermarkClicked,
                onEpisodeClicked = onEpisodeClicked,
                onDismissRequest = onDismissRequest,
            )
        }
    }
}
