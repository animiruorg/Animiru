package eu.kanade.tachiyomi.ui.player.controls.components.dialogs

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.util.formatEpisodeNumber
import eu.kanade.tachiyomi.ui.player.components.EpisodeListItem
import eu.kanade.tachiyomi.util.lang.toRelativeString
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.episode.model.Episode
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.VerticalFastScroller
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun EpisodeListDialog(
    displayMode: Long?,
    currentEpisodeIndex: Int,
    episodeList: List<Episode>,
    dateRelativeTime: Boolean,
    dateFormat: String,
    onBookmarkClicked: (Long?, Boolean) -> Unit,
    onFillermarkClicked: (Long?, Boolean) -> Unit,
    onEpisodeClicked: (Long?) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val locale = LocalLocale.current

    val itemScrollIndex = (episodeList.size - currentEpisodeIndex) - 1
    val episodeListState = rememberLazyListState(initialFirstVisibleItemIndex = itemScrollIndex)
    val dateFormatter = remember(dateFormat) { UiPreferences.dateFormat(dateFormat, locale.platformLocale) }

    PlayerDialog(
        title = stringResource(AYMR.strings.episodes),
        modifier = Modifier.fillMaxHeight(fraction = 0.8F).fillMaxWidth(fraction = 0.8F),
        onDismissRequest = onDismissRequest,
    ) {
        VerticalFastScroller(
            listState = episodeListState,
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxHeight(),
                state = episodeListState,
            ) {
                items(
                    items = episodeList.reversed(),
                    key = { "episode-${it.id}" },
                    contentType = { "episode" },
                ) { episode ->

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
                                    relative = dateRelativeTime,
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
                }
            }
        }
    }
}
