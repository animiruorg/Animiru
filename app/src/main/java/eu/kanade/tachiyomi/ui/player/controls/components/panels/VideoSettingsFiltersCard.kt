package eu.kanade.tachiyomi.ui.player.controls.components.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
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
import animiru.domain.player.model.VideoFilters
import eu.kanade.presentation.player.components.ExpandableCard
import eu.kanade.presentation.player.components.SliderItem
import eu.kanade.tachiyomi.ui.player.controls.CARDS_MAX_WIDTH
import eu.kanade.tachiyomi.ui.player.controls.panelCardsColors
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Info
import mihon.icons.materialsymbols.rounded.Tune
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun VideoSettingsFiltersCard(
    isGpuNextEnabled: Boolean,
    filterValue: (VideoFilters) -> Int,
    onFilterValueChange: (VideoFilters, Int) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember { mutableStateOf(true) }

    ExpandableCard(
        isExpanded = isExpanded,
        onExpand = { isExpanded = !isExpanded },
        title = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
            ) {
                Icon(MaterialSymbols.Rounded.Tune, null)
                Text(stringResource(AYMR.strings.player_sheets_filters_title))
            }
        },
        colors = panelCardsColors(),
        modifier = modifier.widthIn(max = CARDS_MAX_WIDTH),
    ) {
        Column {
            TextButton(onClick = onReset) {
                Text(text = stringResource(MR.strings.action_reset))
            }

            VideoFilters.entries.forEach { filter ->
                val value = filterValue(filter)
                SliderItem(
                    label = stringResource(filter.titleRes),
                    value = value,
                    valueText = value.toString(),
                    onChange = { onFilterValueChange(filter, it) },
                    max = 100,
                    min = -100,
                )
            }

            if (!isGpuNextEnabled) {
                Column(
                    modifier = Modifier
                        .padding(MaterialTheme.padding.medium)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Icon(MaterialSymbols.Rounded.Info, null)
                    Text(stringResource(AYMR.strings.player_sheets_filters_warning))
                }
            }
        }
    }
}
