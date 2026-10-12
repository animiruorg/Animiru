package eu.kanade.tachiyomi.ui.player.cast.controls

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import dev.vivvvek.seeker.Seeker
import dev.vivvvek.seeker.SeekerDefaults
import dev.vivvvek.seeker.Segment
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import eu.kanade.tachiyomi.ui.player.cast.CastUiData
import eu.kanade.tachiyomi.ui.player.cast.components.CastControlButton
import eu.kanade.tachiyomi.ui.player.cast.components.SmallSlider
import eu.kanade.tachiyomi.ui.player.components.CurrentChapter
import eu.kanade.tachiyomi.ui.player.controls.components.VideoTimer
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.MusicNote
import mihon.icons.materialsymbols.rounded.Speed
import mihon.icons.materialsymbols.rounded.Subtitles
import mihon.icons.materialsymbols.rounded.VideoLibrary
import mihon.icons.materialsymbols.roundedfilled.FastForward
import mihon.icons.materialsymbols.roundedfilled.FastRewind
import mihon.icons.materialsymbols.roundedfilled.HighQuality
import mihon.icons.materialsymbols.roundedfilled.Pause
import mihon.icons.materialsymbols.roundedfilled.PlayArrow
import mihon.icons.materialsymbols.roundedfilled.SkipNext
import mihon.icons.materialsymbols.roundedfilled.SkipPrevious
import mihon.icons.materialsymbols.roundedfilled.VolumeDown
import mihon.icons.materialsymbols.roundedfilled.VolumeMute
import mihon.icons.materialsymbols.roundedfilled.VolumeUp
import tachiyomi.cast.CastState
import tachiyomi.presentation.core.components.material.padding

@Composable
fun CastMainControls(
    castState: CastState,
    castUiData: CastUiData,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onSkipIntroClick: () -> Unit,
    onCustomButtonClick: () -> Unit,
    onCustomButtonLongClick: () -> Unit,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onBackSeek: () -> Unit,
    onForwardSeek: () -> Unit,
    onClickChapter: () -> Unit,
    onClickDuration: () -> Unit,
    onSeekBarStart: (Float) -> Unit,
    onSeekBarEnd: () -> Unit,
    onClickPlaylist: () -> Unit,
    onClickSpeed: () -> Unit,
    onClickSubs: () -> Unit,
    onClickAudio: () -> Unit,
    onClickQuality: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.fillMaxWidth().padding(end = MaterialTheme.padding.medium).height(42.dp),
        ) {
            castUiData.skipIntroText?.let {
                OutlinedButton(onClick = onSkipIntroClick) {
                    Text(it)
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            CastControlButton(
                MaterialSymbols.RoundedFilled.SkipPrevious,
                onClick = onPrevious,
                iconSize = 36.dp,
                enabled = hasPrevious,
            )

            CastControlButton(
                MaterialSymbols.RoundedFilled.FastRewind,
                onClick = onBackSeek,
                iconSize = 36.dp,
                enabled = true,
            )

            if (castState.buffering || castState.isLoading || castUiData.isLoadingEpisode) {
                Box(modifier = Modifier.padding(MaterialTheme.padding.medium)) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                    )
                }
            } else {
                CastControlButton(
                    if (castState.playing) {
                        MaterialSymbols.RoundedFilled.Pause
                    } else {
                        MaterialSymbols.RoundedFilled.PlayArrow
                    },
                    onClick = onPlayPause,
                    iconSize = 36.dp,
                    enabled = true,
                )
            }

            CastControlButton(
                MaterialSymbols.RoundedFilled.FastForward,
                onClick = onForwardSeek,
                iconSize = 36.dp,
                enabled = true,
            )

            CastControlButton(
                MaterialSymbols.RoundedFilled.SkipNext,
                onClick = onNext,
                iconSize = 36.dp,
                enabled = hasNext,
            )
        }

        val seekPosition = if (castUiData.isSeeking) castUiData.seekPosition else castState.position.toFloat()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(40.dp),
        ) {
            VideoTimer(
                value = seekPosition,
                isInverted = false,
                onClick = { },
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.width(92.dp),
            )

            if (castUiData.showChapterIndicator && castUiData.currentChapter != null) {
                CurrentChapter(
                    chapter = castUiData.currentChapter,
                    background = MaterialTheme.colorScheme.onBackground,
                    onBackground = MaterialTheme.colorScheme.background,
                    onClick = onClickChapter,
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            val duration = if (castUiData.invertDurationTimer) {
                seekPosition - castUiData.duration.toFloat()
            } else {
                castUiData.duration.toFloat()
            }
            VideoTimer(
                value = duration,
                isInverted = castUiData.invertDurationTimer,
                onClick = onClickDuration,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.width(92.dp),
            )
        }

        Seeker(
            value = seekPosition,
            range = 0f..castUiData.duration.toFloat(),
            onValueChange = onSeekBarStart,
            onValueChangeFinished = onSeekBarEnd,
            readAheadValue = 0f,
            segments = castUiData.chapters,
            modifier = Modifier,
            colors = SeekerDefaults.seekerColors(
                progressColor = MaterialTheme.colorScheme.primary,
                thumbColor = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onBackground,
                readAheadColor = MaterialTheme.colorScheme.inversePrimary,
            ),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth(),
        ) {
            CastControlButton(
                icon = MaterialSymbols.Rounded.VideoLibrary,
                onClick = onClickPlaylist,
                onLongClick = { },
                horizontalSpacing = MaterialTheme.padding.mediumSmall,
            )

            CastControlButton(
                icon = MaterialSymbols.Rounded.Speed,
                onClick = onClickSpeed,
                onLongClick = { },
                horizontalSpacing = MaterialTheme.padding.mediumSmall,
            )

            CastControlButton(
                icon = MaterialSymbols.Rounded.Subtitles,
                onClick = onClickSubs,
                onLongClick = { },
                horizontalSpacing = MaterialTheme.padding.mediumSmall,
            )

            CastControlButton(
                icon = MaterialSymbols.Rounded.MusicNote,
                onClick = onClickAudio,
                onLongClick = { },
                horizontalSpacing = MaterialTheme.padding.mediumSmall,
            )

            CastControlButton(
                icon = MaterialSymbols.RoundedFilled.HighQuality,
                onClick = onClickQuality,
                onLongClick = { },
                horizontalSpacing = MaterialTheme.padding.mediumSmall,
            )

            CustomButton(
                skipIntroLength = castUiData.skipIntroLength,
                onClick = onCustomButtonClick,
                onLongClick = onCustomButtonLongClick,
            )
        }

        var volumeSliderValue by remember { mutableFloatStateOf(volume) }
        LaunchedEffect(volume) {
            volumeSliderValue = volume
        }

        VolumeSlider(
            volume = volumeSliderValue,
            onVolumeChange = {
                volumeSliderValue = it
                onVolumeChange(it)
            },
        )
    }
}

@Composable
private fun CustomButton(
    skipIntroLength: Long,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }

    if (skipIntroLength > 0L) {
        Box {
            Button(onClick = {}, modifier = modifier) {
                Text("+${skipIntroLength}s")
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick,
                        interactionSource = interactionSource,
                        indication = null,
                    ),
            )
        }
    }
}

@Composable
private fun VolumeSlider(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        modifier = modifier.fillMaxWidth(0.75f),
    ) {
        val icon = if (volume == 0f) {
            MaterialSymbols.RoundedFilled.VolumeMute
        } else if (volume < 35f) {
            MaterialSymbols.RoundedFilled.VolumeDown
        } else {
            MaterialSymbols.RoundedFilled.VolumeUp
        }

        Icon(
            icon,
            null,
            tint = MaterialTheme.colorScheme.onBackground,
        )

        SmallSlider(
            value = volume,
            onValueChange = onVolumeChange,
            valueRange = 0f..1f,
        )
    }
}

@Composable
@PreviewLightDark
private fun CastMainControlsPreview() {
    TachiyomiPreviewTheme {
        CastMainControls(
            castState = CastState(),
            castUiData = CastUiData(
                showChapterIndicator = true,
                currentChapter = Segment("Opening", 140f),
            ),
            hasNext = true,
            hasPrevious = false,
            onSkipIntroClick = { },
            onCustomButtonClick = { },
            onCustomButtonLongClick = { },
            volume = 0.4f,
            onVolumeChange = { },
            onPlayPause = { },
            onNext = { },
            onPrevious = { },
            onBackSeek = { },
            onForwardSeek = { },
            onClickChapter = { },
            onClickDuration = { },
            onSeekBarStart = { },
            onSeekBarEnd = { },
            onClickPlaylist = { },
            onClickSpeed = { },
            onClickSubs = { },
            onClickAudio = { },
            onClickQuality = { },
        )
    }
}
