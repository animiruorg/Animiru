package eu.kanade.tachiyomi.ui.player.cast.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import eu.kanade.presentation.player.components.PlayerSheet
import eu.kanade.presentation.player.components.SliderItem
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.toFixed
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Add
import mihon.icons.materialsymbols.rounded.Close
import mihon.icons.materialsymbols.rounded.RestartAlt
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.Button
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

const val CAST_PLAYBACK_MIN = 0.5f
const val CAST_PLAYBACK_MAX = 2.0f

@Composable
fun CastPlaybackSpeedSheet(
    speed: Float,
    speedPresets: List<Float>,
    onSpeedChange: (Float) -> Unit,
    onAddSpeedPreset: (Float) -> Unit,
    onRemoveSpeedPreset: (Float) -> Unit,
    onResetPresets: () -> Unit,
    onMakeDefault: (Float) -> Unit,
    onResetDefault: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlayerSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(vertical = MaterialTheme.padding.medium),
        ) {
            SliderItem(
                label = stringResource(AYMR.strings.player_sheets_speed_slider_label),
                value = speed,
                valueText = stringResource(AYMR.strings.player_speed, speed),
                onChange = onSpeedChange,
                max = CAST_PLAYBACK_MAX,
                min = CAST_PLAYBACK_MIN,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MaterialTheme.padding.medium),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
            ) {
                FilledTonalIconButton(onClick = onResetPresets) {
                    Icon(MaterialSymbols.Rounded.RestartAlt, null)
                }
                LazyRow(
                    modifier = Modifier
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                ) {
                    items(speedPresets, key = { it }) {
                        InputChip(
                            selected = speed == it,
                            onClick = { onSpeedChange(it) },
                            label = { Text(stringResource(AYMR.strings.player_speed, it)) },
                            modifier = Modifier
                                .animateItem(),
                            trailingIcon = {
                                Icon(
                                    MaterialSymbols.Rounded.Close,
                                    null,
                                    modifier = Modifier.clickable { onRemoveSpeedPreset(it.toFixed(2)) },
                                )
                            },
                        )
                    }
                }
                FilledTonalIconButton(onClick = { onAddSpeedPreset(speed.toFixed(2)) }) {
                    Icon(MaterialSymbols.Rounded.Add, null)
                }
            }
            Row(
                modifier = Modifier
                    .padding(horizontal = MaterialTheme.padding.medium),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { onMakeDefault(speed) },
                ) {
                    Text(text = stringResource(AYMR.strings.player_sheets_speed_make_default))
                }
                FilledIconButton(onClick = onResetDefault) {
                    Icon(imageVector = MaterialSymbols.Rounded.RestartAlt, contentDescription = null)
                }
            }
        }
    }
}
