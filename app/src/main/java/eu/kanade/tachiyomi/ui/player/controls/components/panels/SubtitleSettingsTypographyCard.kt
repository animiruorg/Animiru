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

package eu.kanade.tachiyomi.ui.player.controls.components.panels

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import animiru.domain.player.model.SubtitleJustification
import animiru.domain.player.model.SubtitlesBorderStyle
import animiru.domain.player.service.SubtitlePreferences
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.presentation.player.components.ExpandableCard
import eu.kanade.presentation.player.components.ExposedTextDropDownMenu
import eu.kanade.presentation.player.components.SliderItem
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.player.controls.CARDS_MAX_WIDTH
import eu.kanade.tachiyomi.ui.player.controls.panelCardsColors
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.BorderColor
import mihon.icons.materialsymbols.rounded.BorderStyle
import mihon.icons.materialsymbols.rounded.Check
import mihon.icons.materialsymbols.rounded.FormatAlignCenter
import mihon.icons.materialsymbols.rounded.FormatAlignJustify
import mihon.icons.materialsymbols.rounded.FormatAlignLeft
import mihon.icons.materialsymbols.rounded.FormatAlignRight
import mihon.icons.materialsymbols.rounded.FormatBold
import mihon.icons.materialsymbols.rounded.FormatClear
import mihon.icons.materialsymbols.rounded.FormatColorText
import mihon.icons.materialsymbols.rounded.FormatItalic
import mihon.icons.materialsymbols.rounded.FormatSize
import tachiyomi.core.common.preference.deleteAndGet
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun SubtitleSettingsTypographyCard(
    isBold: Boolean,
    isItalic: Boolean,
    justify: SubtitleJustification,
    font: String,
    fontList: List<String>,
    fontSize: Int,
    borderStyle: SubtitlesBorderStyle,
    borderSize: Int,
    shadowOffset: Int,
    onIsBoldChange: (Boolean) -> Unit,
    onIsItalicChange: (Boolean) -> Unit,
    onJustificationChange: (SubtitleJustification) -> Unit,
    onFontChange: (String) -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onBorderStyleChange: (SubtitlesBorderStyle) -> Unit,
    onBorderSizeChange: (Int) -> Unit,
    onShadowOffsetChange: (Int) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember { mutableStateOf(true) }

    var fontsLoadingIndicator: (@Composable () -> Unit)? by remember {
        val indicator: (@Composable () -> Unit) = {
            CircularProgressIndicator(Modifier.size(32.dp))
        }
        mutableStateOf(indicator)
    }

    ExpandableCard(
        isExpanded = isExpanded,
        onExpand = { isExpanded = !isExpanded },
        title = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
            ) {
                Icon(MaterialSymbols.Rounded.FormatColorText, null)
                Text(stringResource(AYMR.strings.player_sheets_sub_typography_title))
            }
        },
        modifier = modifier.widthIn(max = CARDS_MAX_WIDTH),
        colors = panelCardsColors(),
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = MaterialTheme.padding.extraSmall, end = MaterialTheme.padding.medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconToggleButton(
                    checked = isBold,
                    onCheckedChange = onIsBoldChange,
                ) {
                    Icon(
                        MaterialSymbols.Rounded.FormatBold,
                        null,
                        modifier = Modifier.size(32.dp),
                    )
                }
                IconToggleButton(
                    checked = isItalic,
                    onCheckedChange = onIsItalicChange,
                ) {
                    Icon(
                        MaterialSymbols.Rounded.FormatItalic,
                        null,
                        modifier = Modifier.size(32.dp),
                    )
                }
                SubtitleJustification.entries.minus(SubtitleJustification.Auto).forEach { justification ->
                    IconToggleButton(
                        checked = justify == justification,
                        onCheckedChange = { onJustificationChange(justification) },
                    ) {
                        val imageVector = when (justification) {
                            SubtitleJustification.Left -> MaterialSymbols.Rounded.FormatAlignLeft
                            SubtitleJustification.Center -> MaterialSymbols.Rounded.FormatAlignCenter
                            SubtitleJustification.Right -> MaterialSymbols.Rounded.FormatAlignRight
                            SubtitleJustification.Auto -> MaterialSymbols.Rounded.FormatAlignJustify
                        }

                        Icon(imageVector, null)
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onReset) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(MaterialSymbols.Rounded.FormatClear, null)
                        Text(stringResource(MR.strings.action_reset))
                    }
                }
            }
            Row(
                modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(R.drawable.outline_brand_family_24),
                    null,
                    modifier = Modifier.size(32.dp),
                )
                ExposedTextDropDownMenu(
                    selectedValue = font,
                    options = fontList,
                    label = stringResource(AYMR.strings.player_sheets_sub_typography_font),
                    onValueChangedEvent = onFontChange,
                    leadingIcon = if (fontList.isEmpty()) fontsLoadingIndicator else null,
                )
            }
            SliderItem(
                label = stringResource(AYMR.strings.player_sheets_sub_typography_font_size),
                max = 100,
                min = 1,
                value = fontSize,
                valueText = fontSize.toString(),
                onChange = onFontSizeChange,
            ) {
                Icon(MaterialSymbols.Rounded.FormatSize, null)
            }

            var selectingBorderStyle by remember { mutableStateOf(false) }
            Box {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            onClick = {
                                selectingBorderStyle = !selectingBorderStyle
                            },
                        )
                        .padding(
                            horizontal = MaterialTheme.padding.medium,
                            vertical = MaterialTheme.padding.small,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.large),
                ) {
                    Icon(MaterialSymbols.Rounded.BorderStyle, null)
                    Column {
                        Text(
                            text = stringResource(AYMR.strings.player_sheets_sub_typography_border_style),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = stringResource(borderStyle.titleRes),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                DropdownMenu(expanded = selectingBorderStyle, onDismissRequest = { selectingBorderStyle = false }) {
                    SubtitlesBorderStyle.entries.map {
                        DropdownMenuItem(
                            text = { Text(stringResource(it.titleRes)) },
                            onClick = { onBorderStyleChange(it) },
                            trailingIcon = {
                                if (borderStyle == it) {
                                    Icon(
                                        imageVector = MaterialSymbols.Rounded.Check,
                                        contentDescription = null,
                                    )
                                }
                            },
                        )
                    }
                }
            }
            SliderItem(
                stringResource(AYMR.strings.player_sheets_sub_typography_border_size),
                value = borderSize,
                valueText = borderSize.toString(),
                onChange = onBorderSizeChange,
                max = 100,
                icon = { Icon(MaterialSymbols.Rounded.BorderColor, null) },
            )
            SliderItem(
                stringResource(AYMR.strings.player_sheets_subtitles_shadow_offset),
                value = shadowOffset,
                valueText = shadowOffset.toString(),
                onChange = onShadowOffsetChange,
                max = 100,
                icon = { Icon(painterResource(R.drawable.sharp_shadow_24), null) },
            )
        }
    }
}

fun resetTypography(
    setStringValue: (String, String) -> Unit,
    setBooleanValue: (String, Boolean) -> Unit,
    setIntValue: (String, Int) -> Unit,
    preferences: SubtitlePreferences,
) {
    setBooleanValue("sub-bold", preferences.boldSubtitles.deleteAndGet())
    setBooleanValue("sub-italic", preferences.italicSubtitles.deleteAndGet())
    setStringValue("sub-justify", preferences.subtitleJustification.deleteAndGet().value)
    setStringValue("sub-font", preferences.subtitleFont.deleteAndGet())
    setIntValue("sub-font-size", preferences.subtitleFontSize.deleteAndGet())
    setIntValue("sub-outline-size", preferences.subtitleBorderSize.deleteAndGet())
    setIntValue("sub-shadow-offset", preferences.shadowOffsetSubtitles.deleteAndGet())
    setStringValue("sub-border-style", preferences.borderStyleSubtitles.deleteAndGet().value)
}
