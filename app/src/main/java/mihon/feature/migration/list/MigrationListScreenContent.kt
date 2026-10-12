package mihon.feature.migration.list

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.anime.components.AnimeCover
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.util.formatEpisodeNumber
import eu.kanade.presentation.util.rememberResourceBitmapPainter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.animesource.model.FetchType
import mihon.feature.migration.list.models.MigratingAnime
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.automirroredrounded.ArrowForward
import mihon.icons.materialsymbols.rounded.Close
import mihon.icons.materialsymbols.rounded.ContentCopy
import mihon.icons.materialsymbols.rounded.CopyAll
import mihon.icons.materialsymbols.rounded.Done
import mihon.icons.materialsymbols.rounded.DoneAll
import mihon.icons.materialsymbols.rounded.MoreVert
import mihon.icons.materialsymbols.rounded.Warning
import tachiyomi.domain.anime.model.Anime
import tachiyomi.i18n.MR
import tachiyomi.i18n.animiru.AMMR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.Badge
import tachiyomi.presentation.core.components.BadgeGroup
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.plus

@Composable
fun MigrationListScreenContent(
    items: List<MigratingAnime>,
    migrationComplete: Boolean,
    finishedCount: Int,
    onItemClick: (Anime) -> Unit,
    onSearchManually: (MigratingAnime) -> Unit,
    // AY -->
    onSearchSeasons: (Anime, Anime) -> Unit,
    // <-- AY
    onSkip: (Long) -> Unit,
    onMigrate: (Long) -> Unit,
    onCopy: (Long) -> Unit,
    openMigrationDialog: (Boolean) -> Unit,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = if (items.isNotEmpty()) {
                    stringResource(MR.strings.migrationListScreenTitleWithProgress, finishedCount, items.size)
                } else {
                    stringResource(MR.strings.migrationListScreenTitle)
                },
                actions = {
                    AppBarActions(
                        listOf(
                            AppBar.Action(
                                title = stringResource(MR.strings.migrationListScreen_copyActionLabel),
                                icon = if (items.size ==
                                    1
                                ) {
                                    MaterialSymbols.Rounded.ContentCopy
                                } else {
                                    MaterialSymbols.Rounded.CopyAll
                                },
                                onClick = { openMigrationDialog(true) },
                                enabled = migrationComplete,
                            ),
                            AppBar.Action(
                                title = stringResource(MR.strings.migrationListScreen_migrateActionLabel),
                                icon = if (items.size ==
                                    1
                                ) {
                                    MaterialSymbols.Rounded.Done
                                } else {
                                    MaterialSymbols.Rounded.DoneAll
                                },
                                onClick = { openMigrationDialog(false) },
                                enabled = migrationComplete,
                            ),
                        ),
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        FastScrollLazyColumn(contentPadding = contentPadding + topSmallPaddingValues) {
            items(items, key = { it.anime.id }) { item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .animateItem()
                        .padding(
                            start = MaterialTheme.padding.medium,
                            end = MaterialTheme.padding.small,
                        )
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MigrationListItem(
                        modifier = Modifier
                            .weight(1f)
                            .align(Alignment.Top)
                            .fillMaxHeight(),
                        anime = item.anime,
                        source = item.source,
                        episodeCount = item.episodeCount,
                        latestEpisode = item.latestEpisode,
                        onClick = { onItemClick(item.anime) },
                    )

                    Icon(
                        imageVector = MaterialSymbols.AutoMirroredRounded.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.weight(0.2f),
                    )

                    val result by item.searchResult.collectAsState()
                    MigrationListItemResult(
                        modifier = Modifier
                            .weight(1f)
                            .align(Alignment.Top)
                            .fillMaxHeight(),
                        result = result,
                        onItemClick = onItemClick,
                    )

                    MigrationListItemAction(
                        modifier = Modifier.weight(0.2f),
                        result = result,
                        onSearchManually = { onSearchManually(item) },
                        // AY -->
                        onSearchSeasons = { onSearchSeasons(item.anime, it) },
                        // <-- AY
                        onSkip = { onSkip(item.anime.id) },
                        onMigrate = { onMigrate(item.anime.id) },
                        onCopy = { onCopy(item.anime.id) },
                    )
                }
            }
        }
    }
}

// AY -->
@Composable
fun MigrationMismatchListItem(
    modifier: Modifier,
    anime: Anime,
    source: String,
    episodeCount: Int,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .widthIn(max = 150.dp)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(AnimeCover.Book.ratio),
        ) {
            AnimeCover.Book(
                modifier = Modifier.fillMaxWidth(),
                data = anime,
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to MaterialTheme.colorScheme.background,
                        ),
                    )
                    .fillMaxHeight(0.4f)
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
            )
            Text(
                modifier = Modifier
                    .padding(8.dp)
                    .align(Alignment.BottomStart),
                text = anime.title,
                overflow = TextOverflow.Ellipsis,
                maxLines = 2,
                style = MaterialTheme.typography.labelMedium,
            )
            BadgeGroup(modifier = Modifier.padding(4.dp)) {
                Badge(text = "$episodeCount")
            }
        }

        Column(
            modifier = Modifier
                .padding(MaterialTheme.padding.extraSmall),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = source,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                style = MaterialTheme.typography.titleSmall,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Icon(
                    imageVector = MaterialSymbols.Rounded.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error,
                )

                Text(
                    text = stringResource(AMMR.strings.migrationListScreen_mismatchedFetchType),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
// <-- AY

@Composable
fun MigrationListItem(
    modifier: Modifier,
    anime: Anime,
    source: String,
    episodeCount: Int,
    latestEpisode: Double?,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .widthIn(max = 150.dp)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(AnimeCover.Book.ratio),
        ) {
            AnimeCover.Book(
                modifier = Modifier.fillMaxWidth(),
                data = anime,
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to MaterialTheme.colorScheme.background,
                        ),
                    )
                    .fillMaxHeight(0.4f)
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
            )
            Text(
                modifier = Modifier
                    .padding(8.dp)
                    .align(Alignment.BottomStart),
                text = anime.title,
                overflow = TextOverflow.Ellipsis,
                maxLines = 2,
                style = MaterialTheme.typography.labelMedium,
            )
            BadgeGroup(modifier = Modifier.padding(4.dp)) {
                Badge(text = "$episodeCount")
            }
        }

        Column(
            modifier = Modifier
                .padding(MaterialTheme.padding.extraSmall),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = source,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                style = MaterialTheme.typography.titleSmall,
            )
            val formattedLatestEpisodes = remember(latestEpisode) {
                latestEpisode?.let(::formatEpisodeNumber)
            }
            Text(
                text = stringResource(
                    MR.strings.migrationListScreen_latestChapterLabel,
                    formattedLatestEpisodes ?: stringResource(MR.strings.migrationListScreen_unknownLatestChapter),
                ),
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
fun MigrationListItemResult(
    modifier: Modifier,
    result: MigratingAnime.SearchResult,
    onItemClick: (Anime) -> Unit,
) {
    Box(modifier.height(IntrinsicSize.Min)) {
        when (result) {
            MigratingAnime.SearchResult.Searching -> {
                Box(
                    modifier = Modifier
                        .widthIn(max = 150.dp)
                        .fillMaxSize()
                        .aspectRatio(AnimeCover.Book.ratio),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            MigratingAnime.SearchResult.NotFound -> {
                Column(
                    Modifier
                        .widthIn(max = 150.dp)
                        .fillMaxSize()
                        .padding(4.dp),
                ) {
                    Image(
                        painter = rememberResourceBitmapPainter(id = R.drawable.cover_error),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(AnimeCover.Book.ratio)
                            .clip(MaterialTheme.shapes.extraSmall),
                        contentScale = ContentScale.Crop,
                    )
                    Text(
                        text = stringResource(MR.strings.migrationListScreen_noMatchFoundText),
                        modifier = Modifier.padding(MaterialTheme.padding.extraSmall),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
            // AY -->
            is MigratingAnime.SearchResult.MismatchedFetchType -> {
                MigrationMismatchListItem(
                    modifier = Modifier.fillMaxSize(),
                    anime = result.anime,
                    source = result.source,
                    episodeCount = result.episodeCount,
                    onClick = { onItemClick(result.anime) },
                )
            }
            // <-- AY
            is MigratingAnime.SearchResult.Success -> {
                MigrationListItem(
                    modifier = Modifier.fillMaxSize(),
                    anime = result.anime,
                    source = result.source,
                    episodeCount = result.episodeCount,
                    latestEpisode = result.latestEpisode,
                    onClick = { onItemClick(result.anime) },
                )
            }
        }
    }
}

@Composable
private fun MigrationListItemAction(
    modifier: Modifier,
    result: MigratingAnime.SearchResult,
    onSearchManually: () -> Unit,
    // AY -->
    onSearchSeasons: (Anime) -> Unit,
    // <-- AY
    onSkip: () -> Unit,
    onMigrate: () -> Unit,
    onCopy: () -> Unit,
) {
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    val closeMenu = { menuExpanded = false }
    Box(modifier) {
        when (result) {
            MigratingAnime.SearchResult.Searching -> {
                IconButton(onClick = onSkip) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Close,
                        contentDescription = null,
                    )
                }
            }
            MigratingAnime.SearchResult.NotFound,
            // AY -->
            is MigratingAnime.SearchResult.MismatchedFetchType,
            // <-- AY
            is MigratingAnime.SearchResult.Success,
            -> {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.MoreVert,
                        contentDescription = null,
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = closeMenu,
                    offset = DpOffset(8.dp, (-56).dp),
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(MR.strings.migrationListScreen_searchManuallyActionLabel)) },
                        onClick = {
                            closeMenu()
                            onSearchManually()
                        },
                    )
                    // AY -->
                    if (result is MigratingAnime.SearchResult.MismatchedFetchType ||
                        result is MigratingAnime.SearchResult.Success
                    ) {
                        val anime = when (result) {
                            is MigratingAnime.SearchResult.MismatchedFetchType -> result.anime
                            is MigratingAnime.SearchResult.Success -> result.anime
                            else -> error("How did we get here?")
                        }

                        if (anime.fetchType == FetchType.Seasons) {
                            DropdownMenuItem(
                                text = { Text(stringResource(AYMR.strings.label_show_seasons)) },
                                onClick = {
                                    closeMenu()
                                    onSearchSeasons(anime)
                                },
                            )
                        }
                    }
                    // <-- AY
                    DropdownMenuItem(
                        text = { Text(stringResource(MR.strings.migrationListScreen_skipActionLabel)) },
                        onClick = {
                            closeMenu()
                            onSkip()
                        },
                    )
                    if (result is MigratingAnime.SearchResult.Success) {
                        DropdownMenuItem(
                            text = { Text(stringResource(MR.strings.migrationListScreen_migrateNowActionLabel)) },
                            onClick = {
                                closeMenu()
                                onMigrate()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(MR.strings.migrationListScreen_copyNowActionLabel)) },
                            onClick = {
                                closeMenu()
                                onCopy()
                            },
                        )
                    }
                }
            }
        }
    }
}
