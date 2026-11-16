package eu.kanade.tachiyomi.ui.player.controls.components.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import animiru.domain.player.model.ArtType
import eu.kanade.presentation.player.components.PlayerSheet
import eu.kanade.presentation.player.components.SwitchPreference
import eu.kanade.tachiyomi.ui.player.controls.components.dialogs.PlayerDialog
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Photo
import mihon.icons.materialsymbols.rounded.Save
import mihon.icons.materialsymbols.rounded.Share
import mihon.icons.materialsymbols.roundedfilled.FastForward
import mihon.icons.materialsymbols.roundedfilled.FastRewind
import mihon.icons.materialsymbols.roundedfilled.Pause
import mihon.icons.materialsymbols.roundedfilled.PlayArrow
import tachiyomi.i18n.MR
import tachiyomi.i18n.animiru.AMMR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.ActionButton
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import java.io.InputStream

@Composable
fun ScreenshotSheet(
    isLocalSource: Boolean,
    hasSubTracks: Boolean,
    showSubtitles: Boolean,
    onToggleShowSubtitles: (Boolean) -> Unit,

    onSetAsArt: (ArtType, (() -> InputStream)) -> Unit,
    onShare: (() -> InputStream) -> Unit,
    onSave: (() -> InputStream) -> Unit,
    takeScreenshot: (Boolean) -> InputStream?,

    frameCount: Int,
    currentFrame: Int,
    paused: Boolean,
    onPlayPause: () -> Unit,
    onPreviousFrame: () -> Unit,
    onNextFrame: () -> Unit,

    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var setArtTypeAs: ArtType? by remember { mutableStateOf(null) }

    PlayerSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    ) {
        Column {
            Row(
                modifier = Modifier.padding(top = MaterialTheme.padding.medium),
            ) {
                ActionButton(
                    modifier = Modifier.weight(1f),
                    title = stringResource(MR.strings.set_as_cover),
                    icon = MaterialSymbols.Rounded.Photo,
                    onClick = { setArtTypeAs = ArtType.Cover },
                )
                ActionButton(
                    modifier = Modifier.weight(1f),
                    title = stringResource(AYMR.strings.set_as_background),
                    icon = MaterialSymbols.Rounded.Photo,
                    onClick = { setArtTypeAs = ArtType.Background },
                )
                if (isLocalSource) {
                    ActionButton(
                        modifier = Modifier.weight(1f),
                        title = stringResource(AYMR.strings.set_as_thumbnail),
                        icon = MaterialSymbols.Rounded.Photo,
                        onClick = { setArtTypeAs = ArtType.Thumbnail },
                    )
                }
                ActionButton(
                    modifier = Modifier.weight(1f),
                    title = stringResource(MR.strings.action_share),
                    icon = MaterialSymbols.Rounded.Share,
                    onClick = {
                        onShare { takeScreenshot(showSubtitles)!! }
                    },
                )
                ActionButton(
                    modifier = Modifier.weight(1f),
                    title = stringResource(MR.strings.action_save),
                    icon = MaterialSymbols.Rounded.Save,
                    onClick = {
                        onSave { takeScreenshot(showSubtitles)!! }
                    },
                )
            }

            if (hasSubTracks) {
                SwitchPreference(
                    value = showSubtitles,
                    onValueChange = onToggleShowSubtitles,
                    content = {
                        Text(
                            text = stringResource(AYMR.strings.screenshot_show_subs),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    },
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(
                    start = MaterialTheme.padding.large,
                    end = MaterialTheme.padding.large,
                    bottom = MaterialTheme.padding.medium,
                ),
            ) {
                Text(
                    text = stringResource(AMMR.strings.player_frame, currentFrame, frameCount),
                    style = MaterialTheme.typography.labelLarge,
                )

                Spacer(modifier = Modifier.weight(1f))

                FilledIconButton(onClick = onPreviousFrame) {
                    Icon(MaterialSymbols.RoundedFilled.FastRewind, null)
                }
                FilledIconButton(onClick = onPlayPause) {
                    Icon(
                        if (paused) {
                            MaterialSymbols.RoundedFilled.PlayArrow
                        } else {
                            MaterialSymbols.RoundedFilled.Pause
                        },
                        null,
                    )
                }
                FilledIconButton(onClick = onNextFrame) {
                    Icon(MaterialSymbols.RoundedFilled.FastForward, null)
                }
            }
        }
    }

    if (setArtTypeAs != null) {
        PlayerDialog(
            title = stringResource(MR.strings.confirm_set_image_as_cover),
            modifier = Modifier
                .fillMaxWidth(fraction = 0.6F)
                .padding(MaterialTheme.padding.medium),
            onConfirmRequest = {
                onSetAsArt(setArtTypeAs!!) {
                    takeScreenshot(showSubtitles)!!
                }
            },
            onDismissRequest = { setArtTypeAs = null },
        )
    }
}

@Preview
@Composable
private fun ScreenshowSheetPreview() {
    ScreenshotSheet(
        isLocalSource = false,
        hasSubTracks = true,
        showSubtitles = true,
        onToggleShowSubtitles = { },
        onSetAsArt = { _, _ -> },
        onShare = {},
        onSave = { },
        takeScreenshot = { _ -> null },
        frameCount = 412,
        currentFrame = 23,
        paused = true,
        onPlayPause = { },
        onPreviousFrame = { },
        onNextFrame = { },
        onDismissRequest = {},
    )
}
