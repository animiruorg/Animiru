package eu.kanade.tachiyomi.ui.player.cast.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.util.formatEpisodeNumber
import eu.kanade.tachiyomi.ui.player.components.EpisodeListItem
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.GenericTracksSheet
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.TrackSheetTitle
import eu.kanade.tachiyomi.util.lang.toRelativeString
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.episode.model.Episode
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun CastPlaylistSheet(
    displayMode: Long?,
    currentEpisodeIndex: Int,
    episodeList: List<Episode>,
    dateRelativeFormat: Boolean,
    dateFormat: String,
    onBookmarkClicked: (Long?, Boolean) -> Unit,
    onFillermarkClicked: (Long?, Boolean) -> Unit,
    onEpisodeClicked: (Long?) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val locale = LocalLocale.current

    val dateFormatter = remember(dateFormat) { UiPreferences.dateFormat(dateFormat, locale.platformLocale) }

    GenericTracksSheet(
        tracks = episodeList,
        onDismissRequest = onDismissRequest,
        header = { TrackSheetTitle(stringResource(AYMR.strings.episodes)) },
        track = { episode ->
            val isCurrentEpisode = episode.id == episodeList[currentEpisodeIndex].id
            val title = if (displayMode == Anime.EPISODE_DISPLAY_NUMBER) {
                stringResource(
                    AYMR.strings.display_mode_episode,
                    formatEpisodeNumber(episode.episodeNumber),
                )
            } else {
                episode.name
            }

            val date = episode.dateUpload
                ?.let {
                    it.toLocalDateTime(TimeZone.currentSystemDefault())
                        .date
                        .toRelativeString(
                            context = context,
                            relative = dateRelativeFormat,
                            dateFormat = dateFormatter,
                        )
                } ?: ""

            EpisodeListItem(
                episode = episode,
                isCurrentEpisode = isCurrentEpisode,
                title = title,
                date = date,
                onBookmarkClicked = onBookmarkClicked,
                onFillermarkClicked = onFillermarkClicked,
                onEpisodeClicked = onEpisodeClicked,
            )
        },
        modifier = modifier.windowInsetsPadding(WindowInsets.safeDrawing),
    )
}
