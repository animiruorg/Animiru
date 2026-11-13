package eu.kanade.tachiyomi.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.toColorInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import animiru.domain.player.model.AudioChannels
import animiru.domain.player.model.DebandSettings
import animiru.domain.player.model.Debanding
import animiru.domain.player.model.Decoder.Companion.getDecoderFromValue
import animiru.domain.player.model.SubtitleJustification
import animiru.domain.player.model.SubtitlesBorderStyle
import animiru.domain.player.model.VideoFilters
import animiru.domain.player.service.SubtitleAssOverride
import cafe.adriel.voyager.core.annotation.InternalVoyagerApi
import eu.kanade.presentation.theme.playerRippleConfiguration
import eu.kanade.tachiyomi.ui.player.PlayerViewModel.PlayerEvent
import eu.kanade.tachiyomi.ui.player.cast.CastDialog
import eu.kanade.tachiyomi.ui.player.cast.CastScreen
import eu.kanade.tachiyomi.ui.player.cast.CastSheet
import eu.kanade.tachiyomi.ui.player.cast.components.CAST_PLAYBACK_MAX
import eu.kanade.tachiyomi.ui.player.cast.components.CAST_PLAYBACK_MIN
import eu.kanade.tachiyomi.ui.player.cast.components.CastDialogs
import eu.kanade.tachiyomi.ui.player.cast.components.CastSheets
import eu.kanade.tachiyomi.ui.player.components.BrightnessOverlay
import eu.kanade.tachiyomi.ui.player.components.MpvSurface
import eu.kanade.tachiyomi.ui.player.components.OrientationOverlay
import eu.kanade.tachiyomi.ui.player.components.SystemAwakeOverlay
import eu.kanade.tachiyomi.ui.player.components.SystemBarOverlay
import eu.kanade.tachiyomi.ui.player.controls.GestureHandler
import eu.kanade.tachiyomi.ui.player.controls.LocalPlayerButtonsClickEvent
import eu.kanade.tachiyomi.ui.player.controls.PlayerControls
import eu.kanade.tachiyomi.ui.player.controls.PlayerDialogs
import eu.kanade.tachiyomi.ui.player.controls.PlayerPanels
import eu.kanade.tachiyomi.ui.player.controls.PlayerSheets
import eu.kanade.tachiyomi.ui.player.controls.components.DoubleTapToSeekOvals
import eu.kanade.tachiyomi.ui.player.controls.components.panels.SubColorType
import eu.kanade.tachiyomi.ui.player.controls.components.panels.resetColors
import eu.kanade.tachiyomi.ui.player.controls.components.panels.resetTypography
import eu.kanade.tachiyomi.ui.player.controls.components.panels.toColorHexString
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.toFixed
import eu.kanade.tachiyomi.ui.player.mpv.MpvVideoTrack
import kotlinx.coroutines.delay
import mihon.app.di.appGraph
import tachiyomi.core.common.preference.deleteAndGet
import tachiyomi.core.common.preference.minusAssign
import tachiyomi.core.common.preference.plusAssign
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.source.local.isLocal
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(InternalVoyagerApi::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appGraph = remember { context.appGraph }
    val interactionSource = remember { MutableInteractionSource() }
    val uiPreferences = remember { appGraph.uiPreferences }
    val playerPreferences = remember { appGraph.playerPreferences }
    val audioPreferences = remember { appGraph.audioPreferences }
    val subtitlePreferences = remember { appGraph.subtitlePreferences }
    val decoderPreferences = remember { appGraph.decoderPreferences }
    val advancedPreferences = remember { appGraph.advancedPlayerPreferences }

    val stateData by viewModel.stateData.collectAsStateWithLifecycle()
    val uiData by viewModel.uiData.collectAsStateWithLifecycle()
    val playbackData by viewModel.playbackData.collectAsStateWithLifecycle()
    val castState by viewModel.castManager.castState.collectAsStateWithLifecycle()

    // Common
    val showFailedHosters by playerPreferences.showFailedHosters.collectAsState()
    val emptyHosters by playerPreferences.showEmptyHosters.collectAsState()
    val speedPresets by playerPreferences.speedPresets.collectAsState()
    val relativeTime by uiPreferences.relativeTime.collectAsState()
    val dateFormat by uiPreferences.dateFormat.collectAsState()

    var hasNavigatedBack by remember { mutableStateOf(false) }

    BackHandler {
        if (!hasNavigatedBack) {
            hasNavigatedBack = true
            if (!stateData.isCasting && stateData.isPipAvailable && !playbackData.paused &&
                playerPreferences.pipOnExit.get() &&
                uiData.sheetShown == Sheets.None &&
                uiData.panelShown == Panels.None &&
                uiData.dialogShown == Dialogs.None
            ) {
                viewModel.handlePlayerEvent(PlayerEvent.EnterPip)
            } else {
                viewModel.castManager.disconnect()
                onBack()
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        OrientationOverlay(
            orientation = playbackData.currentOrientation,
        )

        if (stateData.isCasting) {
            val castUiData by viewModel.castUiData.collectAsStateWithLifecycle()
            CastScreen(
                stateData = stateData,
                castState = castState,
                castUiData = castUiData,
                onBack = {
                    if (!hasNavigatedBack) {
                        hasNavigatedBack = true
                        viewModel.castManager.disconnect()
                        onBack()
                    }
                },
                onStopCasting = viewModel::stopCasting,
                onSkipIntroClicked = viewModel::castOnSkipIntro,
                onClickChapter = { viewModel.setCastSheet(CastSheet.Chapter) },
                onClickDuration = { viewModel.toggleDurationTimer() },
                onSeekBarStart = viewModel::castStartSeek,
                onSeekBarEnd = viewModel::castEndSeek,
                onClickPlaylist = {
                    viewModel.setCastSheet(CastSheet.Playlist)
                },
                onClickSpeed = {
                    viewModel.setCastSheet(CastSheet.PlaybackSpeed)
                },
                onClickSubs = {
                    viewModel.setCastSheet(CastSheet.Subtitle)
                },
                onClickAudio = {
                    viewModel.setCastSheet(CastSheet.Audio)
                },
                onClickQuality = {
                    viewModel.setCastSheet(CastSheet.Quality)
                },
                onClickCustomButton = viewModel::castOnSeekIntro,
                onLongClickCustomButton = { viewModel.setCastDialog(CastDialog.IntroLength) },
                onCastManagerEvent = viewModel.castManager::handleCastManagerEvent,
            )

            CastSheets(
                sheetShown = castUiData.sheetShown,
                isLoadingHosters = uiData.isLoadingHosters,
                hosterState = stateData.hosterState,
                expandedState = uiData.hosterExpandedList,
                selectedVideoIndex = uiData.selectedHosterVideoIndex,
                onClickHoster = viewModel::onHosterClicked,
                onClickVideo = viewModel::onVideoClicked,
                displayHosters = Pair(showFailedHosters, emptyHosters),
                castUiData = castUiData,
                onSelectTrack = viewModel::selectTrack,
                onClickChapter = {
                    viewModel.castSeekTo(it.start.toLong())
                    viewModel.dismissSheet()
                },
                speed = castState.speed.toFloat(),
                speedPresets = speedPresets.map {
                    it.toFloat()
                }.filter { it in CAST_PLAYBACK_MIN..CAST_PLAYBACK_MAX }.sorted(),
                onSpeedChange = {
                    viewModel.castManager.setSpeed(it.toDouble())
                },
                onAddSpeedPreset = { playerPreferences.speedPresets += it.toFixed(2).toString() },
                onRemoveSpeedPreset = { playerPreferences.speedPresets -= it.toFixed(2).toString() },
                onResetSpeedPresets = playerPreferences.speedPresets::delete,
                onMakeDefaultSpeed = { playerPreferences.playerSpeed.set(it.toFixed(2)) },
                onResetDefaultSpeed = {
                    viewModel.castManager.setSpeed(
                        playerPreferences.playerSpeed.deleteAndGet().toFixed(2).toDouble(),
                    )
                },
                episodeDisplayMode = stateData.currentAnime?.displayMode,
                currentEpisodeIndex = stateData.currentPlaylistIndex,
                episodeList = stateData.currentPlaylist,
                dateRelativeTime = relativeTime,
                dateFormat = dateFormat,
                onBookmarkClicked = viewModel::bookmarkEpisode,
                onFillermarkClicked = viewModel::fillermarkEpisode,
                onEpisodeClicked = {
                    viewModel.setCastSheet(CastSheet.None)
                    viewModel.changeEpisode(it)
                },
                onDismissRequest = { viewModel.setCastSheet(CastSheet.None) },
                dismissSheet = uiData.dismissSheet,
            )

            CastDialogs(
                dialogShown = castUiData.dialogShown,
                castUiData = castUiData,
                onSkipIntroLengthChange = viewModel::castSetSkipIntroLength,
                onDismissRequest = { viewModel.setCastDialog(CastDialog.None) },
            )
        } else {
            val mpvVolume by viewModel.propFlow<Int>("volume").collectAsStateWithLifecycle()
            val pausedForCache by viewModel.propFlow<Boolean>("paused-for-cache").collectAsStateWithLifecycle()
            val coreIdle by viewModel.propFlow<Boolean>("core-idle").collectAsStateWithLifecycle()
            val readAhead by viewModel.propFlow<Float>("demuxer-cache-time").collectAsStateWithLifecycle()
            val remaining by viewModel.propFlow<Int>("playtime-remaining").collectAsStateWithLifecycle()
            val playbackSpeed by viewModel.propFlow<Float>("speed").collectAsStateWithLifecycle()

            MpvSurface(
                modifier = Modifier.fillMaxSize(),
                mpv = viewModel.mpv,
                videoOutput = viewModel.videoOutput,
            )

            GestureHandler(
                modifier = Modifier.fillMaxSize(),
                areControlsLocked = uiData.isControlsLocked,
                onGestureEvent = viewModel::handleGestureEvent,
                interactionSource = interactionSource,
            )

            DoubleTapToSeekOvals(
                amount = playbackData.doubleTapSeekAmount,
                text = playbackData.seekText,
                interactionSource = interactionSource,
            )

            LaunchedEffect(playbackData.doubleTapSeekAmount) {
                delay(0.8.seconds)
                viewModel.updateSeekAmount(0)
                viewModel.updateSeekText(null)
                delay(0.1.seconds)
                viewModel.hideSeekBar()
            }

            SystemAwakeOverlay(
                paused = playbackData.paused,
            )

            BrightnessOverlay(
                brightness = playbackData.currentBrightness,
            )

            SystemBarOverlay(
                showStatusBar = uiData.statusBarShown,
            )

            var resetControls by remember { mutableStateOf(true) }

            LaunchedEffect(
                uiData.controlsShown,
                playbackData.paused,
                playbackData.isSeeking,
                resetControls,
            ) {
                if (uiData.controlsShown && !playbackData.paused && !playbackData.isSeeking) {
                    delay(uiData.playerTimeToDisappearMs.milliseconds)
                    viewModel.hideControls()
                }
            }

            CompositionLocalProvider(
                LocalRippleConfiguration provides playerRippleConfiguration,
                LocalPlayerButtonsClickEvent provides { resetControls = !resetControls },
                LocalContentColor provides Color.White,
            ) {
                PlayerControls(
                    stateData = stateData,
                    uiData = uiData,
                    playbackData = playbackData,
                    onBack = onBack,
                    onPlayerEvent = viewModel::handlePlayerEvent,
                    mpvVolume = mpvVolume,
                    pausedForCache = pausedForCache,
                    coreIdle = coreIdle,
                    readAhead = readAhead,
                    remaining = remaining,
                    playbackSpeed = playbackSpeed,
                    modifier = Modifier.fillMaxSize(),
                )

                // Sheets
                val showSubtitles by subtitlePreferences.screenshotSubtitles.collectAsState()
                val statisticsPage by advancedPreferences.playerStatisticsPage.collectAsState()

                val mpvDecoder by viewModel.propFlow<String>("hwdec-current").collectAsState()
                val decoder by remember { derivedStateOf { getDecoderFromValue(mpvDecoder ?: "auto") } }
                val audioChannels by audioPreferences.audioChannels.collectAsState()
                val pitchCorrection by audioPreferences.enablePitchCorrection.collectAsState()
                val mpvAudioPitchCorrection by viewModel.propFlow<Boolean>("audio-pitch-correction").collectAsState()

                val subtitles = remember(stateData.subtitleTracks, stateData.externalSubtitleTracks) {
                    stateData.subtitleTracks.map { MpvVideoTrack.Internal(it) } + stateData.externalSubtitleTracks
                }
                val audioTracks = remember(stateData.audioTracks, stateData.externalAudioTracks) {
                    stateData.audioTracks.map { MpvVideoTrack.Internal(it) } + stateData.externalAudioTracks
                }

                PlayerSheets(
                    sheetShown = uiData.sheetShown,
                    subtitles = subtitles,
                    onAddSubtitle = viewModel::addSubtitle,
                    onSelectSubtitle = viewModel::selectSub,
                    audioTracks = audioTracks,
                    onAddAudio = viewModel::addAudio,
                    onSelectAudio = viewModel::selectAudio,
                    isLoadingHosters = uiData.isLoadingHosters,
                    hosterState = stateData.hosterState,
                    expandedState = uiData.hosterExpandedList,
                    selectedVideoIndex = uiData.selectedHosterVideoIndex,
                    onClickHoster = viewModel::onHosterClicked,
                    onClickVideo = viewModel::onVideoClicked,
                    displayHosters = Pair(showFailedHosters, emptyHosters),
                    chapter = stateData.currentChapter,
                    chapters = stateData.chapters,
                    onSeekToChapter = viewModel::selectChapter,
                    decoder = decoder,
                    onUpdateDecoder = { viewModel.setPropertyString("hwdec", it.value) },
                    pitchCorrection = pitchCorrection || mpvAudioPitchCorrection == true,
                    onPitchCorrectionChange = {
                        audioPreferences.enablePitchCorrection.set(it)
                        viewModel.setPropertyBoolean("audio-pitch-correction", it)
                    },
                    speed = playbackSpeed ?: playerPreferences.playerSpeed.get(),
                    speedPresets = speedPresets.map { it.toFloat() }.sorted(),
                    onSpeedChange = {
                        viewModel.setPropertyFloat("speed", it.toFixed(2))
                    },
                    onAddSpeedPreset = { playerPreferences.speedPresets += it.toFixed(2).toString() },
                    onRemoveSpeedPreset = { playerPreferences.speedPresets -= it.toFixed(2).toString() },
                    onResetSpeedPresets = playerPreferences.speedPresets::delete,
                    onMakeDefaultSpeed = { playerPreferences.playerSpeed.set(it.toFixed(2)) },
                    onResetDefaultSpeed = {
                        viewModel.setPropertyFloat("speed", playerPreferences.playerSpeed.deleteAndGet().toFixed(2))
                    },
                    statisticsPage = statisticsPage,
                    audioChannels = audioChannels,
                    sleepTimerTimeRemaining = playbackData.remainingTime,
                    onStartSleepTimer = viewModel::startTimer,
                    onStatisticsPageChange = { page ->
                        if ((page == 0) xor
                            (statisticsPage == 0)
                        ) {
                            viewModel.mpvCommand("script-binding", "stats/display-stats-toggle")
                        }
                        if (page != 0) viewModel.mpvCommand("script-binding", "stats/display-page-$page")
                        advancedPreferences.playerStatisticsPage.set(page)
                    },
                    onAudioChannelsChange = {
                        audioPreferences.audioChannels.set(it)
                        if (it == AudioChannels.ReverseStereo) {
                            viewModel.setPropertyString(AudioChannels.AutoSafe.property, AudioChannels.AutoSafe.value)
                        } else {
                            viewModel.setPropertyString(AudioChannels.ReverseStereo.property, "")
                        }
                        viewModel.setPropertyString(it.property, it.value)
                    },
                    onCustomButtonClick = viewModel::executeButton,
                    onCustomButtonLongClick = viewModel::executeLongPressButton,
                    buttons = uiData.customButtons,
                    isLocalSource = stateData.currentSource?.isLocal() == true,
                    showSubtitles = showSubtitles,
                    onToggleShowSubtitles = { subtitlePreferences.screenshotSubtitles.set(it) },
                    onSetAsArt = viewModel::setAsArt,
                    onShare = viewModel::shareImage,
                    onSave = viewModel::saveImage,
                    takeScreenshot = viewModel::takeScreenshot,
                    onDismissScreenshot = {
                        viewModel.setSheet(Sheets.None)
                        viewModel.unpause()
                    },
                    onOpenPanel = viewModel::setPanel,
                    onDismissRequest = { viewModel.setSheet(Sheets.None) },
                    dismissSheet = uiData.dismissSheet,
                )

                // Panels
                val subDelayPref by subtitlePreferences.subtitlesDelay.collectAsState()
                val subDelaySecondaryPref by subtitlePreferences.subtitlesSecondaryDelay.collectAsState()
                val deband by decoderPreferences.debanding.collectAsState()
                var subtitleColorType by remember { mutableStateOf(SubColorType.Text) }

                val subDelay by viewModel.propFlow<Double>("sub-delay").collectAsState()
                val subDelaySecondary by viewModel.propFlow<Double>("secondary-sub-delay").collectAsState()
                val subSpeed by viewModel.propFlow<Double>("sub-speed").collectAsState()
                val audioDelay by viewModel.propFlow<Double>("audio-delay").collectAsState()
                val isBold by viewModel.propFlow<Boolean>("sub-bold").collectAsState()
                val isItalic by viewModel.propFlow<Boolean>("sub-italic").collectAsState()
                val subJustify by viewModel.propFlow<String>("sub-justify").collectAsState()
                val subFont by viewModel.propFlow<String>("sub-font").collectAsState()
                val subFontSize by viewModel.propFlow<Int>("sub-font-size").collectAsState()
                val subBorderStyle by viewModel.propFlow<String>("sub-border-style").collectAsState()
                val subBorderSize by viewModel.propFlow<Int>("sub-outline-size").collectAsState()
                val subShadowOffset by viewModel.propFlow<Int>("sub-shadow-offset").collectAsState()
                val subColor by viewModel.propFlow<String>("sub-color").collectAsState()
                val subBorderColor by viewModel.propFlow<String>("sub-outline-color").collectAsState()
                val subBackgroundColor by viewModel.propFlow<String>("sub-back-color").collectAsState()
                val overrideAssSubs by viewModel.propFlow<String>("sub-ass-override").collectAsState()
                val subScale by viewModel.propFlow<Float>("sub-scale").collectAsState()
                val subPos by viewModel.propFlow<Int>("sub-pos").collectAsState()
                val mpvGpuNext by viewModel.propFlow<String>("vo").collectAsState()
                val debandSettingsMap = DebandSettings.entries.associateWith { setting ->
                    viewModel.propFlow<Int>(setting.mpvProperty).collectAsState().value ?: 0
                }
                val filterValuesMap = VideoFilters.entries.associateWith { filter ->
                    viewModel.propFlow<Int>(filter.mpvProperty).collectAsState().value ?: 0
                }

                PlayerPanels(
                    panelShown = uiData.panelShown,
                    onDismissRequest = { viewModel.setPanel(Panels.None) },

                    // Subtitle settings panel state
                    isBold = isBold ?: subtitlePreferences.boldSubtitles.get(),
                    isItalic = isItalic ?: subtitlePreferences.italicSubtitles.get(),
                    subJustify = subJustify?.let {
                        SubtitleJustification.byValue(it)
                    } ?: subtitlePreferences.subtitleJustification.get(),
                    subFont = subFont ?: subtitlePreferences.subtitleFont.get(),
                    subFontList = uiData.fontList,
                    subFontSize = subFontSize ?: subtitlePreferences.subtitleFontSize.get(),
                    subBorderStyle = subBorderStyle?.let { SubtitlesBorderStyle.byValue(it) }
                        ?: subtitlePreferences.borderStyleSubtitles.get(),
                    subBorderSize = subBorderSize ?: subtitlePreferences.subtitleBorderSize.get(),
                    subShadowOffset = subShadowOffset ?: subtitlePreferences.shadowOffsetSubtitles.get(),
                    subColor = subtitleColorType,
                    currentSubtitleColor = when (subtitleColorType) {
                        SubColorType.Text -> subColor?.toColorInt() ?: subtitlePreferences.textColorSubtitles.get()
                        SubColorType.Border -> subBorderColor?.toColorInt()
                            ?: subtitlePreferences.borderColorSubtitles.get()
                        SubColorType.Background -> subBackgroundColor?.toColorInt()
                            ?: subtitlePreferences.backgroundColorSubtitles.get()
                    },
                    overrideAssSubs = overrideAssSubs?.let { SubtitleAssOverride.byValue(it) }
                        ?: subtitlePreferences.overrideSubsASS.get(),
                    subScale = subScale ?: subtitlePreferences.subtitleFontScale.get(),
                    subPos = subPos ?: subtitlePreferences.subtitlePos.get(),
                    onSubBoldChange = {
                        viewModel.setPropertyBoolean("sub-bold", it)
                        subtitlePreferences.boldSubtitles.set(it)
                    },
                    onSubItalicChange = {
                        viewModel.setPropertyBoolean("sub-italic", it)
                        subtitlePreferences.italicSubtitles.set(it)
                    },
                    onSubJustifyChange = {
                        viewModel.setPropertyString("sub-justify", it.value)
                        subtitlePreferences.subtitleJustification.set(it)
                    },
                    onSubFontChange = {
                        viewModel.setPropertyString("sub-font", it)
                        subtitlePreferences.subtitleFont.set(it)
                    },
                    onSubFontSizeChange = {
                        viewModel.setPropertyInt("sub-font-size", it)
                        subtitlePreferences.subtitleFontSize.set(it)
                    },
                    onSubBorderStyleChange = {
                        viewModel.setPropertyString("sub-border-style", it.value)
                        subtitlePreferences.borderStyleSubtitles.set(it)
                    },
                    onSubBorderSizeChange = {
                        viewModel.setPropertyInt("sub-outline-size", it)
                        subtitlePreferences.subtitleBorderSize.set(it)
                    },
                    onSubShadowOffsetChange = {
                        viewModel.setPropertyInt("sub-shadow-offset", it)
                        subtitlePreferences.shadowOffsetSubtitles.set(it)
                    },
                    onSubColorChange = {
                        when (subtitleColorType) {
                            SubColorType.Text -> {
                                viewModel.setPropertyString("sub-color", it.toColorHexString())
                                subtitlePreferences.textColorSubtitles.set(it)
                            }

                            SubColorType.Border -> {
                                viewModel.setPropertyString("sub-outline-color", it.toColorHexString())
                                subtitlePreferences.borderColorSubtitles.set(it)
                            }

                            SubColorType.Background -> {
                                viewModel.setPropertyString("sub-back-color", it.toColorHexString())
                                subtitlePreferences.backgroundColorSubtitles.set(it)
                            }
                        }
                    },
                    onOverrideAssSubsChange = {
                        viewModel.setPropertyString("sub-ass-override", it.value)
                        subtitlePreferences.overrideSubsASS.set(it)
                    },
                    onSubScaleChange = {
                        viewModel.setPropertyFloat("sub-scale", it)
                        subtitlePreferences.subtitleFontScale.set(it)
                    },
                    onSubPosChange = {
                        viewModel.setPropertyInt("sub-pos", it)
                        subtitlePreferences.subtitlePos.set(it)
                    },
                    onSubColorTypeChange = { subtitleColorType = it },
                    onSubColorReset = {
                        resetColors(
                            preferences = subtitlePreferences,
                            setStringValue = viewModel::setPropertyString,
                            type = subtitleColorType,
                        )
                    },
                    onSubtitleSettingsReset = {
                        resetTypography(
                            setStringValue = viewModel::setPropertyString,
                            setIntValue = viewModel::setPropertyInt,
                            setBooleanValue = viewModel::setPropertyBoolean,
                            preferences = subtitlePreferences,
                        )
                    },
                    onSubtitleMiscReset = {
                        subtitlePreferences.subtitlePos.deleteAndGet().let {
                            viewModel.setPropertyInt("sub-pos", it)
                        }
                        subtitlePreferences.subtitleFontScale.deleteAndGet().let {
                            viewModel.setPropertyFloat("sub-scale", it)
                        }
                        subtitlePreferences.overrideSubsASS.deleteAndGet().let {
                            viewModel.setPropertyString("sub-ass-override", it.value)
                        }
                    },
                    subDelayMsPrimary = subDelay?.times(1000)?.roundToInt() ?: subDelayPref,
                    subDelayMsSecondary = subDelaySecondary?.times(1000)?.roundToInt() ?: subDelaySecondaryPref,
                    subSpeed = subSpeed ?: subtitlePreferences.subtitlesSpeed.get().toDouble(),
                    onSubDelayPrimaryChange = {
                        viewModel.setPropertyDouble("sub-delay", it / 1000.0)
                    },
                    onSubDelaySecondaryChange = {
                        viewModel.setPropertyDouble("secondary-sub-delay", it / 1000.0)
                    },
                    onSubSpeedChange = {
                        viewModel.setPropertyDouble("sub-speed", it)
                    },
                    onSubDelayApply = {
                        subtitlePreferences.subtitlesDelay.set((subDelay?.times(1000)?.roundToInt()) ?: 0)
                        subtitlePreferences.subtitlesSecondaryDelay.set(
                            (subDelaySecondary?.times(1000)?.roundToInt()) ?: 0,
                        )
                    },
                    onSubDelayReset = {
                        viewModel.setPropertyDouble("sub-delay", subtitlePreferences.subtitlesDelay.get() / 1000.0)
                        viewModel.setPropertyDouble(
                            "secondary-sub-delay",
                            subtitlePreferences.subtitlesSecondaryDelay.get() / 1000.0,
                        )
                        viewModel.setPropertyDouble("sub-speed", subtitlePreferences.subtitlesSpeed.get().toDouble())
                    },
                    audioDelayMs = (audioDelay?.times(1000))?.roundToInt() ?: audioPreferences.audioDelay.get(),
                    onAudioDelayChange = { viewModel.setPropertyDouble("audio-delay", it / 1000.0) },
                    onAudioDelayApply = {
                        audioPreferences.audioDelay.set((audioDelay?.times(1000)?.roundToInt()) ?: 0)
                    },
                    onAudioDelayReset = {
                        viewModel.setPropertyDouble("audio-delay", audioPreferences.audioDelay.get() / 1000.0)
                    },
                    onDebandChange = {
                        decoderPreferences.debanding.set(it)
                        when (it) {
                            Debanding.None -> {
                                viewModel.setPropertyString("deband", "no")
                                viewModel.mpvCommand("vf", "remove", "@deband")
                            }

                            Debanding.CPU -> {
                                viewModel.setPropertyString("deband", "no")
                                viewModel.mpvCommand("vf", "add", "@deband:gradfun=radius=12")
                            }

                            Debanding.GPU -> {
                                viewModel.setPropertyString("deband", "yes")
                                viewModel.mpvCommand("vf", "remove", "@deband")
                            }
                        }
                    },
                    onDebandReset = {
                        viewModel.setPropertyString("deband", "no")
                        viewModel.mpvCommand("vf", "remove", "@deband")
                        DebandSettings.entries.forEach {
                            viewModel.setPropertyInt(it.mpvProperty, it.preference(decoderPreferences).deleteAndGet())
                        }
                    },
                    onDebandSettingsChange = { setting, value ->
                        setting.preference(decoderPreferences).set(value)
                        viewModel.setPropertyInt(setting.mpvProperty, value)
                    },
                    onVideoFilterChange = { filter, value ->
                        filter.preference(decoderPreferences).set(value)
                        viewModel.setPropertyInt(filter.mpvProperty, value)
                    },
                    onFilterReset = {
                        VideoFilters.entries.forEach {
                            viewModel.setPropertyInt(it.mpvProperty, it.preference(decoderPreferences).deleteAndGet())
                        }
                    },
                    deband = deband,
                    isGpuNextEnabled = mpvGpuNext == "gpu-next",
                    filterValue = { filterValuesMap[it] ?: 0 },
                    debandSettings = { debandSettingsMap[it] ?: 0 },
                    modifier = Modifier,
                )

                // Dialogs
                PlayerDialogs(
                    dialogShown = uiData.dialogShown,
                    episodeDisplayMode = stateData.currentAnime?.displayMode,
                    currentEpisodeIndex = stateData.currentPlaylistIndex,
                    episodeList = stateData.currentPlaylist,
                    dateRelativeTime = relativeTime,
                    dateFormat = dateFormat,
                    onBookmarkClicked = viewModel::bookmarkEpisode,
                    onFillermarkClicked = viewModel::fillermarkEpisode,
                    onEpisodeClicked = {
                        viewModel.setDialog(Dialogs.None)
                        viewModel.changeEpisode(it)
                    },
                    onDismissRequest = { viewModel.setDialog(Dialogs.None) },
                )
            }
        }
    }
}
