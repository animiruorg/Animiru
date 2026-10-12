package eu.kanade.presentation.anime

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastMaxOfOrNull
import coil3.request.ImageRequest
import coil3.request.crossfade
import eu.kanade.presentation.anime.components.AnimeCover
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.components.TabbedDialogPaddings
import eu.kanade.presentation.more.settings.LocalPreferenceMinHeight
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.SAnime
import mihon.app.di.appGraph
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Add
import mihon.icons.materialsymbols.rounded.AttachMoney
import mihon.icons.materialsymbols.rounded.Block
import mihon.icons.materialsymbols.rounded.Brush
import mihon.icons.materialsymbols.rounded.CalendarClock
import mihon.icons.materialsymbols.rounded.Close
import mihon.icons.materialsymbols.rounded.Done
import mihon.icons.materialsymbols.rounded.DoneAll
import mihon.icons.materialsymbols.rounded.Person
import mihon.icons.materialsymbols.rounded.Schedule
import mihon.icons.materialsymbols.rounded.Warning
import mihon.icons.materialsymbols.roundedfilled.Pause
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.model.AnimeWithEpisodeCount
import tachiyomi.domain.source.model.StubSource
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.Badge
import tachiyomi.presentation.core.components.BadgeGroup
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.secondaryItemAlpha

@Composable
fun DuplicateAnimeDialog(
    duplicates: List<AnimeWithEpisodeCount>,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    onOpenAnime: (anime: Anime) -> Unit,
    onMigrate: (anime: Anime) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val sourceManager = remember { context.appGraph.sourceManager }
    val minHeight = LocalPreferenceMinHeight.current
    val horizontalPadding = PaddingValues(horizontal = TabbedDialogPaddings.Horizontal)
    val horizontalPaddingModifier = Modifier.padding(horizontalPadding)

    AdaptiveSheet(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = TabbedDialogPaddings.Vertical)
                .verticalScroll(rememberScrollState())
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
        ) {
            Text(
                text = stringResource(MR.strings.possible_duplicates_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier
                    .then(horizontalPaddingModifier)
                    .padding(top = MaterialTheme.padding.small),
            )

            Text(
                text = stringResource(MR.strings.possible_duplicates_summary),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.then(horizontalPaddingModifier),
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                modifier = Modifier.height(getMaximumAnimeCardHeight(duplicates)),
                contentPadding = horizontalPadding,
            ) {
                items(
                    items = duplicates,
                    key = { it.anime.id },
                ) {
                    DuplicateAnimeListItem(
                        duplicate = it,
                        getSource = { sourceManager.getOrStub(it.anime.source) },
                        onMigrate = { onMigrate(it.anime) },
                        onDismissRequest = onDismissRequest,
                        onOpenAnime = { onOpenAnime(it.anime) },
                    )
                }
            }

            Column(modifier = horizontalPaddingModifier) {
                HorizontalDivider()

                TextPreferenceWidget(
                    title = stringResource(MR.strings.action_add_anyway),
                    icon = MaterialSymbols.Rounded.Add,
                    onPreferenceClick = {
                        onDismissRequest()
                        onConfirm()
                    },
                    modifier = Modifier.clip(CircleShape),
                )
            }

            OutlinedButton(
                onClick = onDismissRequest,
                modifier = Modifier
                    .then(horizontalPaddingModifier)
                    .padding(bottom = MaterialTheme.padding.medium)
                    .heightIn(min = minHeight)
                    .fillMaxWidth(),
            ) {
                Text(
                    modifier = Modifier.padding(vertical = MaterialTheme.padding.extraSmall),
                    text = stringResource(MR.strings.action_cancel),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun DuplicateAnimeListItem(
    duplicate: AnimeWithEpisodeCount,
    getSource: suspend () -> AnimeSource,
    onDismissRequest: () -> Unit,
    onOpenAnime: () -> Unit,
    onMigrate: () -> Unit,
) {
    val source by produceState<AnimeSource?>(initialValue = null) { value = getSource() }
    val anime = duplicate.anime
    Column(
        modifier = Modifier
            .width(AnimeCardWidth)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .combinedClickable(
                onLongClick = { onOpenAnime() },
                onClick = {
                    onDismissRequest()
                    onMigrate()
                },
            )
            .padding(MaterialTheme.padding.small),
    ) {
        Box {
            AnimeCover.Book(
                data = ImageRequest.Builder(LocalContext.current)
                    .data(anime)
                    .crossfade(true)
                    .build(),
                modifier = Modifier.fillMaxWidth(),
            )
            BadgeGroup(
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.TopStart),
            ) {
                Badge(
                    color = MaterialTheme.colorScheme.secondary,
                    textColor = MaterialTheme.colorScheme.onSecondary,
                    text = pluralStringResource(
                        AYMR.plurals.anime_num_episodes,
                        duplicate.episodeCount.toInt(),
                        duplicate.episodeCount,
                    ),
                )
            }
        }

        Spacer(modifier = Modifier.height(MaterialTheme.padding.extraSmall))

        Text(
            text = anime.title,
            style = MaterialTheme.typography.titleSmall,
            overflow = TextOverflow.Ellipsis,
            maxLines = 2,
        )

        if (!anime.author.isNullOrBlank()) {
            AnimeDetailRow(
                text = anime.author!!,
                iconImageVector = MaterialSymbols.Rounded.Person,
                maxLines = 2,
            )
        }

        if (!anime.artist.isNullOrBlank() && anime.author != anime.artist) {
            AnimeDetailRow(
                text = anime.artist!!,
                iconImageVector = MaterialSymbols.Rounded.Brush,
                maxLines = 2,
            )
        }

        AnimeDetailRow(
            text = when (anime.status) {
                SAnime.ONGOING.toLong() -> stringResource(MR.strings.ongoing)
                SAnime.COMPLETED.toLong() -> stringResource(MR.strings.completed)
                SAnime.LICENSED.toLong() -> stringResource(MR.strings.licensed)
                SAnime.PUBLISHING_FINISHED.toLong() -> stringResource(MR.strings.publishing_finished)
                SAnime.CANCELLED.toLong() -> stringResource(MR.strings.cancelled)
                SAnime.ON_HIATUS.toLong() -> stringResource(MR.strings.on_hiatus)
                SAnime.UPCOMING.toLong() -> stringResource(AYMR.strings.upcoming)
                else -> stringResource(MR.strings.unknown)
            },
            iconImageVector = when (anime.status) {
                SAnime.ONGOING.toLong() -> MaterialSymbols.Rounded.Schedule
                SAnime.COMPLETED.toLong() -> MaterialSymbols.Rounded.DoneAll
                SAnime.LICENSED.toLong() -> MaterialSymbols.Rounded.AttachMoney
                SAnime.PUBLISHING_FINISHED.toLong() -> MaterialSymbols.Rounded.Done
                SAnime.CANCELLED.toLong() -> MaterialSymbols.Rounded.Close
                SAnime.ON_HIATUS.toLong() -> MaterialSymbols.RoundedFilled.Pause
                SAnime.UPCOMING.toLong() -> MaterialSymbols.Rounded.CalendarClock
                else -> MaterialSymbols.Rounded.Block
            },
        )

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            if (source is StubSource) {
                Icon(
                    imageVector = MaterialSymbols.Rounded.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                text = source?.name.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun AnimeDetailRow(
    text: String,
    iconImageVector: ImageVector,
    maxLines: Int = 1,
) {
    Row(
        modifier = Modifier
            .secondaryItemAlpha()
            .padding(top = MaterialTheme.padding.extraSmall),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = iconImageVector,
            contentDescription = null,
            modifier = Modifier.size(AnimeDetailsIconWidth),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            overflow = TextOverflow.Ellipsis,
            maxLines = maxLines,
        )
    }
}

@Composable
private fun getMaximumAnimeCardHeight(duplicates: List<AnimeWithEpisodeCount>): Dp {
    val density = LocalDensity.current
    val typography = MaterialTheme.typography
    val textMeasurer = rememberTextMeasurer()

    val smallPadding = with(density) { MaterialTheme.padding.small.roundToPx() }
    val extraSmallPadding = with(density) { MaterialTheme.padding.extraSmall.roundToPx() }

    val width = with(density) { AnimeCardWidth.roundToPx() - (2 * smallPadding) }
    val iconWidth = with(density) { AnimeDetailsIconWidth.roundToPx() }

    val coverHeight = width / AnimeCover.Book.ratio
    val constraints = Constraints(maxWidth = width)
    val detailsConstraints = Constraints(maxWidth = width - iconWidth - extraSmallPadding)

    return remember(
        duplicates,
        density,
        typography,
        textMeasurer,
        smallPadding,
        extraSmallPadding,
        coverHeight,
        constraints,
        detailsConstraints,
    ) {
        duplicates.fastMaxOfOrNull {
            calculateAnimeCardHeight(
                anime = it.anime,
                density = density,
                typography = typography,
                textMeasurer = textMeasurer,
                smallPadding = smallPadding,
                extraSmallPadding = extraSmallPadding,
                coverHeight = coverHeight,
                constraints = constraints,
                detailsConstraints = detailsConstraints,
            )
        }
            ?: 0.dp
    }
}

private fun calculateAnimeCardHeight(
    anime: Anime,
    density: Density,
    typography: Typography,
    textMeasurer: TextMeasurer,
    smallPadding: Int,
    extraSmallPadding: Int,
    coverHeight: Float,
    constraints: Constraints,
    detailsConstraints: Constraints,
): Dp {
    val titleHeight = textMeasurer.measureHeight(anime.title, typography.titleSmall, 2, constraints)
    val authorHeight = if (!anime.author.isNullOrBlank()) {
        textMeasurer.measureHeight(anime.author!!, typography.bodySmall, 2, detailsConstraints)
    } else {
        0
    }
    val artistHeight = if (!anime.artist.isNullOrBlank() && anime.author != anime.artist) {
        textMeasurer.measureHeight(anime.artist!!, typography.bodySmall, 2, detailsConstraints)
    } else {
        0
    }
    val statusHeight = textMeasurer.measureHeight("", typography.bodySmall, 2, detailsConstraints)
    val sourceHeight = textMeasurer.measureHeight("", typography.labelSmall, 1, constraints)

    val totalHeight = coverHeight + titleHeight + authorHeight + artistHeight + statusHeight + sourceHeight
    return with(density) { ((2 * smallPadding) + totalHeight + (5 * extraSmallPadding)).toDp() }
}

private fun TextMeasurer.measureHeight(
    text: String,
    style: TextStyle,
    maxLines: Int,
    constraints: Constraints,
): Int = measure(
    text = text,
    style = style,
    overflow = TextOverflow.Ellipsis,
    maxLines = maxLines,
    constraints = constraints,
)
    .size
    .height

private val AnimeCardWidth = 150.dp
private val AnimeDetailsIconWidth = 16.dp
