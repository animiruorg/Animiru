package eu.kanade.tachiyomi.ui.player.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import eu.kanade.tachiyomi.ui.player.Panels
import eu.kanade.tachiyomi.ui.player.PlayerUpdates
import eu.kanade.tachiyomi.ui.player.PlayerViewModel
import eu.kanade.tachiyomi.ui.player.PlayerViewModel.PlayerEvent
import eu.kanade.tachiyomi.ui.player.Sheets
import eu.kanade.tachiyomi.ui.player.controls.components.BrightnessSlider
import eu.kanade.tachiyomi.ui.player.controls.components.ControlsButton
import eu.kanade.tachiyomi.ui.player.controls.components.SeekbarWithTimers
import eu.kanade.tachiyomi.ui.player.controls.components.TextPlayerUpdate
import eu.kanade.tachiyomi.ui.player.controls.components.ThumbnailPreview
import eu.kanade.tachiyomi.ui.player.controls.components.VolumeSlider
import kotlinx.coroutines.delay
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.roundedfilled.Lock
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

@Suppress("CompositionLocalAllowlist")
val LocalPlayerButtonsClickEvent = staticCompositionLocalOf { {} }

@Composable
fun PlayerControls(
    stateData: PlayerViewModel.PlayerStateData,
    uiData: PlayerViewModel.PlayerUiData,
    playbackData: PlayerViewModel.PlayerPlaybackData,
    onBack: () -> Unit,
    onPlayerEvent: (PlayerEvent) -> Unit,
    mpvVolume: Int?,
    pausedForCache: Boolean?,
    coreIdle: Boolean?,
    readAhead: Float?,
    remaining: Int?,
    playbackSpeed: Float?,
    modifier: Modifier = Modifier,
) {
    val transparentOverlay by animateFloatAsState(
        if (uiData.controlsShown && !uiData.isControlsLocked) .8f else 0f,
        animationSpec = playerControlsExitAnimationSpec(),
        label = "controls_transparent_overlay",
    )

    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Ltr,
    ) {
        ConstraintLayout(
            modifier = modifier.fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        Pair(0f, Color.Black),
                        Pair(.2f, Color.Transparent),
                        Pair(.7f, Color.Transparent),
                        Pair(1f, Color.Black),
                    ),
                    alpha = transparentOverlay,
                )
                .padding(horizontal = MaterialTheme.padding.medium),
        ) {
            val (topLeftControls, topRightControls) = createRefs()
            val (volumeSlider, brightnessSlider) = createRefs()
            val unlockControlsButton = createRef()
            val (bottomRightControls, bottomLeftControls) = createRefs()
            val centerControls = createRef()
            val thumbnail = createRef()
            val seekbar = createRef()
            val (playerUpdates) = createRefs()
            val skipButton = createRef()

            LaunchedEffect(playbackData.currentVolume, mpvVolume, uiData.isVolumeSliderShown) {
                delay(2.seconds)
                if (uiData.isVolumeSliderShown) onPlayerEvent(PlayerEvent.ShowVolumeSlider(false))
            }
            LaunchedEffect(playbackData.currentBrightness, uiData.isBrightnessSliderShown) {
                delay(2.seconds)
                if (uiData.isBrightnessSliderShown) onPlayerEvent(PlayerEvent.ShowBrightnessSlider(false))
            }
            AnimatedVisibility(
                visible = uiData.isBrightnessSliderShown,
                enter = if (!uiData.reduceMotion) {
                    slideInHorizontally(playerControlsEnterAnimationSpec()) {
                        if (uiData.swapVolumeAndBrightness) it else -it
                    } +
                        fadeIn(
                            playerControlsEnterAnimationSpec(),
                        )
                } else {
                    fadeIn(playerControlsEnterAnimationSpec())
                },
                exit = if (!uiData.reduceMotion) {
                    slideOutHorizontally(playerControlsExitAnimationSpec()) {
                        if (uiData.swapVolumeAndBrightness) it else -it
                    } +
                        fadeOut(
                            playerControlsExitAnimationSpec(),
                        )
                } else {
                    fadeOut(playerControlsExitAnimationSpec())
                },
                modifier = Modifier.constrainAs(brightnessSlider) {
                    if (uiData.swapVolumeAndBrightness) {
                        start.linkTo(parent.start, MaterialTheme.padding.medium)
                    } else {
                        end.linkTo(parent.end, MaterialTheme.padding.medium)
                    }
                    top.linkTo(parent.top)
                    bottom.linkTo(parent.bottom)
                },
            ) {
                BrightnessSlider(
                    brightness = playbackData.currentBrightness,
                    positiveRange = 0f..1f,
                    negativeRange = 0f..0.75f,
                )
            }

            AnimatedVisibility(
                visible = uiData.isVolumeSliderShown,
                enter = if (!uiData.reduceMotion) {
                    slideInHorizontally(playerControlsEnterAnimationSpec()) {
                        if (uiData.swapVolumeAndBrightness) it else -it
                    } +
                        fadeIn(
                            playerControlsEnterAnimationSpec(),
                        )
                } else {
                    fadeIn(playerControlsEnterAnimationSpec())
                },
                exit = if (!uiData.reduceMotion) {
                    slideOutHorizontally(playerControlsExitAnimationSpec()) {
                        if (uiData.swapVolumeAndBrightness) it else -it
                    } +
                        fadeOut(
                            playerControlsExitAnimationSpec(),
                        )
                } else {
                    fadeOut(playerControlsExitAnimationSpec())
                },
                modifier = Modifier.constrainAs(volumeSlider) {
                    if (uiData.swapVolumeAndBrightness) {
                        end.linkTo(parent.end, MaterialTheme.padding.medium)
                    } else {
                        start.linkTo(parent.start, MaterialTheme.padding.medium)
                    }
                    top.linkTo(parent.top)
                    bottom.linkTo(parent.bottom)
                },
            ) {
                VolumeSlider(
                    volume = playbackData.currentVolume,
                    mpvVolume = mpvVolume ?: 100,
                    range = 0..stateData.maxVolume,
                    boostRange = if (uiData.boostCap > 0) 0..uiData.boostCap else null,
                    displayAsPercentage = uiData.displayVolumeAsPercentage,
                )
            }

            LaunchedEffect(uiData.playerUpdate) {
                if (uiData.playerUpdate is PlayerUpdates.DoubleSpeed || uiData.playerUpdate is PlayerUpdates.None) {
                    return@LaunchedEffect
                }
                delay(2.seconds)
                onPlayerEvent(PlayerEvent.ShowPlayerUpdate(PlayerUpdates.None))
            }
            AnimatedVisibility(
                visible = uiData.playerUpdate !is PlayerUpdates.None,
                enter = fadeIn(playerControlsEnterAnimationSpec()),
                exit = fadeOut(playerControlsExitAnimationSpec()),
                modifier = Modifier.constrainAs(playerUpdates) {
                    linkTo(parent.start, parent.end)
                    linkTo(parent.top, parent.bottom, bias = 0.2f)
                },
            ) {
                when (uiData.playerUpdate) {
                    PlayerUpdates.None -> {}
                    PlayerUpdates.DoubleSpeed -> {}
                    is PlayerUpdates.AspectRatio -> TextPlayerUpdate(
                        stringResource(uiData.playerUpdate.aspect.titleRes),
                    )
                    is PlayerUpdates.ShowText -> TextPlayerUpdate(uiData.playerUpdate.value)
                    is PlayerUpdates.ShowTextResource -> TextPlayerUpdate(
                        stringResource(uiData.playerUpdate.textResource),
                    )
                }
            }

            AnimatedVisibility(
                visible = uiData.controlsShown && uiData.isControlsLocked,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.constrainAs(unlockControlsButton) {
                    top.linkTo(parent.top, MaterialTheme.padding.medium)
                    start.linkTo(parent.start, MaterialTheme.padding.medium)
                },
            ) {
                ControlsButton(
                    MaterialSymbols.RoundedFilled.Lock,
                    onClick = { onPlayerEvent(PlayerEvent.LockControls(false)) },
                )
            }

            AnimatedVisibility(
                visible = (
                    uiData.controlsShown && (!uiData.isControlsLocked || playbackData.gestureSeekAmount != null)
                    ) ||
                    (pausedForCache == true || (coreIdle == true && !playbackData.paused)) ||
                    uiData.isLoadingEpisode,
                enter = fadeIn(playerControlsEnterAnimationSpec()),
                exit = fadeOut(playerControlsExitAnimationSpec()),
                modifier = Modifier.constrainAs(centerControls) {
                    end.linkTo(parent.absoluteRight)
                    start.linkTo(parent.absoluteLeft)
                    top.linkTo(parent.top)
                    bottom.linkTo(parent.bottom)
                },
            ) {
                MiddlePlayerControls(
                    hasPrevious = stateData.hasPreviousEpisode,
                    onSkipPrevious = { onPlayerEvent(PlayerEvent.NextEpisode(false)) },
                    hasNext = stateData.hasNextEpisode,
                    onSkipNext = { onPlayerEvent(PlayerEvent.NextEpisode(true)) },
                    isStopped = stateData.isStopped,
                    isLoading = pausedForCache == true || (coreIdle == true && !playbackData.paused),
                    isLoadingEpisode = uiData.isLoadingEpisode,
                    controlsShown = uiData.controlsShown,
                    areControlsLocked = uiData.isControlsLocked,
                    showLoadingCircle = uiData.showLoadingCircle,
                    paused = playbackData.paused,
                    gestureSeekAmount = playbackData.gestureSeekAmount,
                    onPlayPauseClick = { onPlayerEvent(PlayerEvent.PlayPause) },
                    enter = fadeIn(playerControlsEnterAnimationSpec()),
                    exit = fadeOut(playerControlsExitAnimationSpec()),
                )
            }
            AnimatedVisibility(
                visible = (uiData.controlsShown || uiData.seekBarShown) && !uiData.isControlsLocked,
                enter = if (!uiData.reduceMotion) {
                    slideInVertically(playerControlsEnterAnimationSpec()) { it } +
                        fadeIn(playerControlsEnterAnimationSpec())
                } else {
                    fadeIn(playerControlsEnterAnimationSpec())
                },
                exit = if (!uiData.reduceMotion) {
                    slideOutVertically(playerControlsExitAnimationSpec()) { it } +
                        fadeOut(playerControlsExitAnimationSpec())
                } else {
                    fadeOut(playerControlsExitAnimationSpec())
                },
                modifier = Modifier.constrainAs(seekbar) {
                    bottom.linkTo(bottomLeftControls.top)
                },
            ) {
                SeekbarWithTimers(
                    playerPosition = playbackData.position.toFloat(),
                    seekPosition = playbackData.seekPosition,
                    isGestureSeeking = playbackData.isGestureSeeking,
                    isSeeking = playbackData.isSeeking,
                    duration = playbackData.duration.toFloat(),
                    remaining = remaining?.toFloat() ?: 0f,
                    readAheadValue = readAhead ?: 0f,
                    onValueChange = { onPlayerEvent(PlayerEvent.Seek(it.roundToInt())) },
                    onValueChangeFinished = { onPlayerEvent(PlayerEvent.SeekFinished(it.roundToInt())) },
                    timersInverted = Pair(false, uiData.invertDuration),
                    durationTimerOnCLick = { onPlayerEvent(PlayerEvent.ToggleDurationTimer) },
                    positionTimerOnClick = { },
                    chapters = stateData.chapters,
                )
            }

            AnimatedVisibility(
                visible = uiData.controlsShown && !uiData.isControlsLocked,
                enter = if (!uiData.reduceMotion) {
                    slideInHorizontally(playerControlsEnterAnimationSpec()) { -it } +
                        fadeIn(playerControlsEnterAnimationSpec())
                } else {
                    fadeIn(playerControlsEnterAnimationSpec())
                },
                exit = if (!uiData.reduceMotion) {
                    slideOutHorizontally(playerControlsExitAnimationSpec()) { -it } +
                        fadeOut(playerControlsExitAnimationSpec())
                } else {
                    fadeOut(playerControlsExitAnimationSpec())
                },
                modifier = Modifier.constrainAs(topLeftControls) {
                    top.linkTo(parent.top, MaterialTheme.padding.medium)
                    start.linkTo(parent.start)
                    width = Dimension.fillToConstraints
                    end.linkTo(topRightControls.start)
                },
            ) {
                TopLeftPlayerControls(
                    animeTitle = uiData.animeTitle,
                    mediaTitle = uiData.mediaTitle,
                    onTitleClick = { onPlayerEvent(PlayerEvent.ShowEpisodeDialog) },
                    onBackClick = onBack,
                )
            }

            AnimatedVisibility(
                visible = uiData.controlsShown && !uiData.isControlsLocked,
                enter = if (!uiData.reduceMotion) {
                    slideInHorizontally(playerControlsEnterAnimationSpec()) { it } +
                        fadeIn(playerControlsEnterAnimationSpec())
                } else {
                    fadeIn(playerControlsEnterAnimationSpec())
                },
                exit = if (!uiData.reduceMotion) {
                    slideOutHorizontally(playerControlsExitAnimationSpec()) { it } +
                        fadeOut(playerControlsExitAnimationSpec())
                } else {
                    fadeOut(playerControlsExitAnimationSpec())
                },
                modifier = Modifier.constrainAs(topRightControls) {
                    top.linkTo(parent.top, MaterialTheme.padding.medium)
                    end.linkTo(parent.end)
                },
            ) {
                TopRightPlayerControls(
                    autoPlayEnabled = uiData.autoPlayEnabled,
                    onToggleAutoPlay = { onPlayerEvent(PlayerEvent.SetAutoPlay(it)) },
                    onSubtitlesClick = { onPlayerEvent(PlayerEvent.SetSheet(Sheets.SubtitleTracks)) },
                    onSubtitlesLongClick = { onPlayerEvent(PlayerEvent.SetPanel(Panels.SubtitleSettings)) },
                    onAudioClick = { onPlayerEvent(PlayerEvent.SetSheet(Sheets.AudioTracks)) },
                    onAudioLongClick = { onPlayerEvent(PlayerEvent.SetPanel(Panels.AudioDelay)) },
                    onQualityClick = { onPlayerEvent(PlayerEvent.SetSheet(Sheets.QualityTracks)) },
                    isEpisodeOnline = stateData.isEpisodeOnline,
                    onMoreClick = { onPlayerEvent(PlayerEvent.SetSheet(Sheets.More)) },
                    onMoreLongClick = { onPlayerEvent(PlayerEvent.SetPanel(Panels.VideoFilters)) },
                )
            }

            AnimatedVisibility(
                visible = uiData.controlsShown && !uiData.isControlsLocked,
                enter = if (!uiData.reduceMotion) {
                    slideInVertically(playerControlsEnterAnimationSpec()) { it } +
                        fadeIn(playerControlsEnterAnimationSpec())
                } else {
                    fadeIn(playerControlsEnterAnimationSpec())
                },
                exit = if (!uiData.reduceMotion) {
                    slideOutVertically(playerControlsExitAnimationSpec()) { it } +
                        fadeOut(playerControlsExitAnimationSpec())
                } else {
                    fadeOut(playerControlsExitAnimationSpec())
                },
                modifier = Modifier.constrainAs(skipButton) {
                    end.linkTo(seekbar.end, MaterialTheme.padding.small)
                    bottom.linkTo(seekbar.top)
                },
            ) {
                SkipIntroControls(
                    customButton = uiData.primaryButton,
                    customButtonTitle = uiData.primaryButtonTitle,
                    skipIntroButton = uiData.skipIntroText,
                    onPressSkipIntroButton = { onPlayerEvent(PlayerEvent.SkipIntro) },
                    onCustomButtonClick = { onPlayerEvent(PlayerEvent.ExecuteCustomButton(false)) },
                    onCustomButtonLongClick = { onPlayerEvent(PlayerEvent.ExecuteCustomButton(true)) },
                )
            }

            AnimatedVisibility(
                visible = uiData.controlsShown && !uiData.isControlsLocked,
                enter = if (!uiData.reduceMotion) {
                    slideInHorizontally(playerControlsEnterAnimationSpec()) { it } +
                        fadeIn(playerControlsEnterAnimationSpec())
                } else {
                    fadeIn(playerControlsEnterAnimationSpec())
                },
                exit = if (!uiData.reduceMotion) {
                    slideOutHorizontally(playerControlsExitAnimationSpec()) { it } +
                        fadeOut(playerControlsExitAnimationSpec())
                } else {
                    fadeOut(playerControlsExitAnimationSpec())
                },
                modifier = Modifier.constrainAs(bottomRightControls) {
                    bottom.linkTo(parent.bottom, MaterialTheme.padding.small)
                    end.linkTo(seekbar.end)
                },
            ) {
                BottomRightPlayerControls(
                    castEnabled = uiData.enableCast,
                    castLoading = stateData.isLoadingCasting,
                    castError = stateData.isErrorCasting,
                    isPipAvailable = stateData.isPipAvailable,
                    onPipClick = { onPlayerEvent(PlayerEvent.EnterPip) },
                    onAspectClick = { onPlayerEvent(PlayerEvent.ChangeAspect) },
                )
            }

            AnimatedVisibility(
                visible = uiData.controlsShown && !uiData.isControlsLocked,
                enter = if (!uiData.reduceMotion) {
                    slideInHorizontally(playerControlsEnterAnimationSpec()) { -it } +
                        fadeIn(playerControlsEnterAnimationSpec())
                } else {
                    fadeIn(playerControlsEnterAnimationSpec())
                },
                exit = if (!uiData.reduceMotion) {
                    slideOutHorizontally(playerControlsExitAnimationSpec()) { -it } +
                        fadeOut(playerControlsExitAnimationSpec())
                } else {
                    fadeOut(playerControlsExitAnimationSpec())
                },
                modifier = Modifier.constrainAs(bottomLeftControls) {
                    bottom.linkTo(parent.bottom, MaterialTheme.padding.small)
                    start.linkTo(seekbar.start)
                    width = Dimension.fillToConstraints
                    end.linkTo(bottomRightControls.start)
                },
            ) {
                BottomLeftPlayerControls(
                    playbackSpeed = playbackSpeed ?: uiData.playerSpeedPref,
                    showChapterIndicator = uiData.showChapterIndicator,
                    currentChapter = stateData.currentChapter,
                    onLockControls = { onPlayerEvent(PlayerEvent.LockControls(true)) },
                    onCycleRotation = { onPlayerEvent(PlayerEvent.CycleRotation) },
                    onPlaybackSpeedChange = { onPlayerEvent(PlayerEvent.ChangeSpeed(it)) },
                    onOpenSheet = { onPlayerEvent(PlayerEvent.SetSheet(it)) },
                )
            }

            ThumbnailPreview(
                visible = playbackData.isSeeking,
                image = playbackData.thumbnailImage,
                positionS = playbackData.seekPosition.toLong(),
                durationS = playbackData.duration.toLong(),
                chapters = stateData.chapters,
                modifier = Modifier.fillMaxWidth().constrainAs(thumbnail) {
                    bottom.linkTo(seekbar.top, MaterialTheme.padding.medium)
                },
            )
        }
    }
}

@Composable
@Preview(
    device = "spec:width=411dp,height=891dp,dpi=420,isRound=false,chinSize=0dp,orientation=landscape",
)
private fun PlayerControlsPreview() {
    MaterialTheme {
        PlayerControls(
            stateData = PlayerViewModel.PlayerStateData(
                maxVolume = 0,
                isEpisodeOnline = true,
                isPipAvailable = true,
            ),
            uiData = PlayerViewModel.PlayerUiData(
                animeTitle = "ef - a tale of memories.",
                mediaTitle = "Ep. 2 - Upon a Time",
                playerUpdate = PlayerUpdates.DoubleSpeed,
            ),
            playbackData = PlayerViewModel.PlayerPlaybackData(
                currentVolume = 0,
                currentBrightness = 0f,
            ),
            onBack = { },
            onPlayerEvent = { },
            mpvVolume = 0,
            pausedForCache = false,
            coreIdle = false,
            readAhead = 0f,
            remaining = 0,
            playbackSpeed = 1f,
        )
    }
}

fun <T> playerControlsExitAnimationSpec(): FiniteAnimationSpec<T> = tween(
    durationMillis = 300,
    easing = FastOutSlowInEasing,
)

fun <T> playerControlsEnterAnimationSpec(): FiniteAnimationSpec<T> = tween(
    durationMillis = 100,
    easing = LinearOutSlowInEasing,
)
