package eu.kanade.tachiyomi.ui.player.cast

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import eu.kanade.tachiyomi.ui.player.PlayerViewModel
import eu.kanade.tachiyomi.ui.player.cast.controls.CastInfoBox
import eu.kanade.tachiyomi.ui.player.cast.controls.CastMainControls
import eu.kanade.tachiyomi.ui.player.cast.controls.CastTopControls
import tachiyomi.cast.CastManagerEvent
import tachiyomi.cast.CastState
import tachiyomi.i18n.animiru.AMMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun CastScreen(
    stateData: PlayerViewModel.PlayerStateData,
    castState: CastState,
    castUiData: CastUiData,
    onBack: () -> Unit,
    onStopCasting: () -> Unit,
    onSkipIntroClicked: () -> Unit,
    onClickChapter: () -> Unit,
    onClickDuration: () -> Unit,
    onSeekBarStart: (Float) -> Unit,
    onSeekBarEnd: () -> Unit,
    onClickPlaylist: () -> Unit,
    onClickSpeed: () -> Unit,
    onClickSubs: () -> Unit,
    onClickAudio: () -> Unit,
    onClickQuality: () -> Unit,
    onClickCustomButton: () -> Unit,
    onLongClickCustomButton: () -> Unit,
    onCastManagerEvent: (CastManagerEvent) -> Unit,
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    ConstraintLayout(
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = MaterialTheme.padding.medium),
    ) {
        val topControls = createRef()
        val infoBox = createRef()
        val controls = createRef()

        CastTopControls(
            deviceName = castState.deviceName ?: stringResource(AMMR.strings.player_cast_unknown_device),
            onBack = onBack,
            onStopCasting = onStopCasting,
            modifier = Modifier.constrainAs(topControls) {
                start.linkTo(parent.start)
                end.linkTo(parent.end)
                top.linkTo(parent.top)
            },
        )

        CastInfoBox(
            anime = stateData.currentAnime,
            episodeTitle = stateData.currentEpisode?.name,
            episodeNumber = stateData.currentEpisode?.episodeNumber,
            modifier = Modifier.constrainAs(infoBox) {
                if (isLandscape) {
                    start.linkTo(parent.start)
                    top.linkTo(topControls.bottom, MaterialTheme.padding.small)
                    bottom.linkTo(parent.bottom, MaterialTheme.padding.small)
                    width = Dimension.percent(0.4f)
                    height = Dimension.fillToConstraints
                } else {
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                    top.linkTo(topControls.bottom, MaterialTheme.padding.medium)
                    bottom.linkTo(controls.top, MaterialTheme.padding.medium)
                    height = Dimension.fillToConstraints
                }
            },
        )

        CastMainControls(
            castState = castState,
            castUiData = castUiData,
            hasPrevious = stateData.hasPreviousEpisode,
            hasNext = stateData.hasNextEpisode,
            onSkipIntroClick = onSkipIntroClicked,
            onCustomButtonClick = onClickCustomButton,
            onCustomButtonLongClick = onLongClickCustomButton,
            volume = castState.volume.toFloat(),
            onVolumeChange = { onCastManagerEvent(CastManagerEvent.VolumeChange(it)) },
            onPlayPause = { onCastManagerEvent(CastManagerEvent.PlayPause) },
            onNext = { onCastManagerEvent(CastManagerEvent.Next(true)) },
            onPrevious = { onCastManagerEvent(CastManagerEvent.Next(false)) },
            onBackSeek = { onCastManagerEvent(CastManagerEvent.DoubleTapSeek(forwards = false)) },
            onForwardSeek = { onCastManagerEvent(CastManagerEvent.DoubleTapSeek(forwards = true)) },
            onClickChapter = onClickChapter,
            onClickDuration = onClickDuration,
            onSeekBarStart = onSeekBarStart,
            onSeekBarEnd = onSeekBarEnd,
            onClickPlaylist = onClickPlaylist,
            onClickSpeed = onClickSpeed,
            onClickSubs = onClickSubs,
            onClickAudio = onClickAudio,
            onClickQuality = onClickQuality,
            modifier = Modifier.constrainAs(controls) {
                if (isLandscape) {
                    start.linkTo(infoBox.end)
                    end.linkTo(parent.end)
                    top.linkTo(topControls.bottom, MaterialTheme.padding.small)
                    bottom.linkTo(parent.bottom)
                    width = Dimension.fillToConstraints
                    height = Dimension.fillToConstraints
                } else {
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                    bottom.linkTo(parent.bottom, MaterialTheme.padding.medium)
                    width = Dimension.fillToConstraints
                }
            },
        )
    }
}

@Composable
@PreviewScreenSizes
private fun CastScreenPreview() {
    TachiyomiPreviewTheme {
        CastScreen(
            stateData = PlayerViewModel.PlayerStateData(
                maxVolume = 0,
            ),
            castState = CastState(),
            castUiData = CastUiData(),
            onBack = { },
            onStopCasting = { },
            onSkipIntroClicked = { },
            onClickChapter = { },
            onClickDuration = { },
            onSeekBarStart = { },
            onSeekBarEnd = { },
            onClickPlaylist = { },
            onClickSpeed = { },
            onClickSubs = { },
            onClickAudio = { },
            onClickQuality = { },
            onClickCustomButton = { },
            onLongClickCustomButton = { },
            onCastManagerEvent = { },
        )
    }
}
