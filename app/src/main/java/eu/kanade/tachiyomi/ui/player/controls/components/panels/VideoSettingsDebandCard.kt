package eu.kanade.tachiyomi.ui.player.controls.components.panels

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
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
import animiru.domain.player.model.DebandSettings
import animiru.domain.player.model.Debanding
import eu.kanade.presentation.player.components.ExpandableCard
import eu.kanade.presentation.player.components.SliderItem
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.player.controls.CARDS_MAX_WIDTH
import eu.kanade.tachiyomi.ui.player.controls.panelCardsColors
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Block
import mihon.icons.materialsymbols.rounded.Gradient
import mihon.icons.materialsymbols.rounded.Memory
import tachiyomi.i18n.MR
import tachiyomi.i18n.animiru.AMMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun VideoSettingsDebandCard(
    deband: Debanding,
    onDebandingChange: (Debanding) -> Unit,
    debandSettingsValue: (DebandSettings) -> Int,
    onDebandingSettingsChange: (DebandSettings, Int) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember { mutableStateOf(true) }

    ExpandableCard(
        isExpanded,
        title = {
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium)) {
                Icon(MaterialSymbols.Rounded.Gradient, null)
                Text(stringResource(AMMR.strings.player_sheets_deband_title))
            }
        },
        onExpand = { isExpanded = !isExpanded },
        modifier.widthIn(max = CARDS_MAX_WIDTH),
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
                Debanding.entries.forEach {
                    IconToggleButton(
                        checked = deband == it,
                        onCheckedChange = { _ -> onDebandingChange(it) },
                    ) {
                        when (it) {
                            Debanding.None -> Icon(MaterialSymbols.Rounded.Block, null)
                            Debanding.CPU -> Icon(MaterialSymbols.Rounded.Memory, null)
                            Debanding.GPU -> Icon(painterResource(R.drawable.expansion_card), null)
                        }
                    }
                }

                Text(stringResource(deband.stringRes))

                Spacer(Modifier.weight(1f))
                TextButton(onClick = onReset) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(painterResource(R.drawable.reset_iso_24px), null)
                        Text(stringResource(MR.strings.action_reset))
                    }
                }
            }

            DebandSettings.entries.forEach { debandSettings ->
                SliderItem(
                    label = stringResource(debandSettings.stringRes),
                    value = debandSettingsValue(debandSettings),
                    valueText = debandSettingsValue(debandSettings).toString(),
                    onChange = { onDebandingSettingsChange(debandSettings, it) },
                    min = debandSettings.start,
                    max = debandSettings.end,
                )
            }
        }
    }
}
