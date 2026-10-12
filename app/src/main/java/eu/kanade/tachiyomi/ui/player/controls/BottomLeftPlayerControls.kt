/*
 * Copyright 2024 Abdallah Mehiz
 * https://github.com/abdallahmehiz/mpvKt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.kanade.tachiyomi.ui.player.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.vivvvek.seeker.Segment
import eu.kanade.tachiyomi.ui.player.Sheets
import eu.kanade.tachiyomi.ui.player.components.CurrentChapter
import eu.kanade.tachiyomi.ui.player.controls.components.ControlsButton
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.ScreenRotation
import mihon.icons.materialsymbols.roundedfilled.LockOpen
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun BottomLeftPlayerControls(
    playbackSpeed: Float,
    currentChapter: Segment?,
    showChapterIndicator: Boolean,
    onLockControls: () -> Unit,
    onCycleRotation: () -> Unit,
    onPlaybackSpeedChange: (Float) -> Unit,
    onOpenSheet: (Sheets) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ControlsButton(
            MaterialSymbols.RoundedFilled.LockOpen,
            onClick = onLockControls,
            verticalSpacing = MaterialTheme.padding.small,
        )
        ControlsButton(
            icon = MaterialSymbols.Rounded.ScreenRotation,
            onClick = onCycleRotation,
            verticalSpacing = MaterialTheme.padding.small,
        )
        ControlsButton(
            text = stringResource(AYMR.strings.player_speed, playbackSpeed),
            onClick = { onPlaybackSpeedChange(if (playbackSpeed >= 2) 0.25f else playbackSpeed + 0.25f) },
            onLongClick = { onOpenSheet(Sheets.PlaybackSpeed) },
            verticalSpacing = MaterialTheme.padding.small,
        )

        AnimatedVisibility(
            showChapterIndicator && currentChapter != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            CurrentChapter(
                chapter = currentChapter ?: return@AnimatedVisibility,
                onClick = { onOpenSheet(Sheets.Chapters) },
            )
        }
    }
}

@Composable
@Preview
private fun BottomLeftPlayerControlsPreview() {
    BottomLeftPlayerControls(
        playbackSpeed = 1.0f,
        currentChapter = Segment("Opening", 43f),
        showChapterIndicator = true,
        onLockControls = { },
        onCycleRotation = { },
        onPlaybackSpeedChange = { },
        onOpenSheet = { },
        modifier = Modifier,
    )
}
