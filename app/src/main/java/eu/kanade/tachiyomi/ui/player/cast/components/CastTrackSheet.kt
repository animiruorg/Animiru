package eu.kanade.tachiyomi.ui.player.cast.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.GenericTracksSheet
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.TrackSheetTitle
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Error
import tachiyomi.cast.domain.TrackInformation
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.SECONDARY_ALPHA
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun CastTrackSheet(
    isAudio: Boolean,
    tracks: List<TrackInformation>,
    selectedIndex: Long,
    onSelect: (TrackInformation) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GenericTracksSheet(
        tracks = tracks,
        onDismissRequest = onDismissRequest,
        header = {
            TrackSheetTitle(
                title = stringResource(
                    if (isAudio) AYMR.strings.pref_player_audio else AYMR.strings.pref_player_subtitle,
                ),
            )
        },
        track = {
            TrackRow(
                track = it,
                isSelected = selectedIndex == it.index,
                onClick = { onSelect(it) },
            )
        },
        modifier = modifier.windowInsetsPadding(WindowInsets.safeDrawing),
    )
}

@Composable
private fun TrackRow(
    track: TrackInformation,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = MaterialTheme.padding.small, end = MaterialTheme.padding.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick,
        )

        Text(
            text = stringResource(
                AYMR.strings.player_sheets_track_title_w_lang,
                track.index,
                track.title,
                track.language,
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal,
            fontStyle = if (isSelected) FontStyle.Italic else FontStyle.Normal,
            modifier = Modifier.weight(1f),
        )

        Text(
            text = "(${track.contentType.substringAfterLast('/')})",
            modifier = Modifier.alpha(SECONDARY_ALPHA),
        )

        if (track.loading) {
            CircularProgressIndicator(modifier = Modifier.then(Modifier.size(24.dp)))
        } else if (track.error) {
            Icon(MaterialSymbols.Rounded.Error, null, tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
@Preview
private fun CastTrackSheetPreview() {
    TachiyomiPreviewTheme {
        Column {
            listOf(
                TrackInformation(
                    title = "Arabic",
                    language = "und",
                    index = 0,
                    type = "subtitle",
                    contentType = "text/vtt",
                    contentId = null,
                    loading = false,
                    error = false,
                ),
                TrackInformation(
                    title = "English",
                    language = "und",
                    index = 1,
                    type = "subtitle",
                    contentType = "text/vtt",
                    contentId = null,
                    loading = false,
                    error = false,
                ),
                TrackInformation(
                    title = "French",
                    language = "und",
                    index = 2,
                    type = "subtitle",
                    contentType = "text/vtt",
                    contentId = null,
                    loading = false,
                    error = false,
                ),
                TrackInformation(
                    title = "German",
                    language = "und",
                    index = 3,
                    type = "subtitle",
                    contentType = "text/vtt",
                    contentId = null,
                    loading = false,
                    error = false,
                ),
                TrackInformation(
                    title = "Italian",
                    language = "und",
                    index = 4,
                    type = "subtitle",
                    contentType = "text/vtt",
                    contentId = null,
                    loading = false,
                    error = false,
                ),
                TrackInformation(
                    title = "Portuguese (- Portuguese(Brazil))",
                    language = "und",
                    index = 5,
                    type = "subtitle",
                    contentType = "text/vtt",
                    contentId = null,
                    loading = false,
                    error = false,
                ),
                TrackInformation(
                    title = "Russian",
                    language = "und",
                    index = 6,
                    type = "subtitle",
                    contentType = "text/vtt",
                    contentId = null,
                    loading = false,
                    error = false,
                ),
                TrackInformation(
                    title = "Spanish",
                    language = "und",
                    index = 7,
                    type = "subtitle",
                    contentType = "text/vtt",
                    contentId = null,
                    loading = false,
                    error = false,
                ),
                TrackInformation(
                    title = "Spanish (- Spanish(Latin America))",
                    language = "und",
                    index = 8,
                    type = "subtitle",
                    contentType = "text/vtt",
                    contentId = null,
                    loading = false,
                    error = false,
                ),
            ).forEach {
                TrackRow(
                    it,
                    false,
                    { },
                )
            }
        }
    }
}
