package eu.kanade.tachiyomi.ui.player.cast.controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.sp
import eu.kanade.tachiyomi.ui.player.cast.components.CastControlButton
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.automirroredrounded.ArrowBack
import mihon.icons.materialsymbols.rounded.CastConnected
import mihon.icons.materialsymbols.rounded.Close
import tachiyomi.presentation.core.components.material.padding

@Composable
fun CastTopControls(
    deviceName: String,
    onBack: () -> Unit,
    onStopCasting: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        CastControlButton(
            icon = MaterialSymbols.AutoMirroredRounded.ArrowBack,
            onClick = onBack,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                MaterialSymbols.Rounded.CastConnected,
                null,
                tint = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(modifier = Modifier.width(MaterialTheme.padding.small))

            Text(
                text = deviceName,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        CastControlButton(
            icon = MaterialSymbols.Rounded.Close,
            onClick = onStopCasting,
        )
    }
}

@Composable
@PreviewLightDark
private fun CastTopControlsPreview() {
    CastTopControls(
        deviceName = "GM1911-180",
        onBack = { },
        onStopCasting = { },
    )
}
