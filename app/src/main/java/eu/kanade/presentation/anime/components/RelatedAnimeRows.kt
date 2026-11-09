// AY -->
package eu.kanade.presentation.anime.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import aniyomi.domain.anime.model.AnimeRelationGroup
import eu.kanade.presentation.browse.components.InLibraryBadge
import eu.kanade.presentation.library.components.AnimeComfortableGridItem
import eu.kanade.presentation.library.components.AnimeCompactGridItem
import eu.kanade.presentation.library.components.CommonAnimeItemDefaults
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.model.asAnimeCover
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.presentation.core.components.material.padding
import kotlin.math.ceil

private val RELATED_ITEM_WIDTH = 96.dp
private const val MAX_ITEMS_PER_ROW = 3

@Composable
fun RelatedAnimeRows(
    relations: List<AnimeRelationGroup>,
    displayMode: LibraryDisplayMode,
    onRelatedClick: (Anime) -> Unit,
    onRelatedLongClick: (Anime) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (relations.isEmpty()) return

    val (smalls, multis) = relations.partition { it.anime.size <= MAX_ITEMS_PER_ROW }

    Column(modifier = modifier.padding(bottom = MaterialTheme.padding.medium)) {
        if (smalls.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = MaterialTheme.padding.small,
                        vertical = MaterialTheme.padding.small,
                    ),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                smalls.packRows(MAX_ITEMS_PER_ROW).forEach { rowRelations ->
                    val startSlots = rowRelations.runningFold(0) { acc, relation ->
                        acc + relation.anime.size
                    }

                    Row(modifier = Modifier.fillMaxWidth()) {
                        rowRelations.forEachIndexed { index, relation ->
                            val size = relation.anime.size
                            val startSlot = startSlots[index]
                            val endSlot = startSlot + size - 1

                            Box(
                                modifier = Modifier.weight(size.toFloat()),
                                contentAlignment = when {
                                    startSlot == 0 -> Alignment.CenterStart
                                    endSlot == MAX_ITEMS_PER_ROW - 1 -> Alignment.CenterEnd
                                    else -> Alignment.Center
                                },
                            ) {
                                Column {
                                    Text(
                                        text = relation.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(Modifier.height(MaterialTheme.padding.extraSmall))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(
                                            MaterialTheme.padding.extraSmall,
                                        ),
                                    ) {
                                        relation.anime.forEach { anime ->
                                            Box(modifier = Modifier.widthIn(max = RELATED_ITEM_WIDTH)) {
                                                RelatedAnimeItem(
                                                    anime = anime,
                                                    displayMode = displayMode,
                                                    onClick = { onRelatedClick(anime) },
                                                    onLongClick = { onRelatedLongClick(anime) },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        val filled = rowRelations.sumOf { it.anime.size }
                        if (filled < MAX_ITEMS_PER_ROW) {
                            Spacer(Modifier.weight((MAX_ITEMS_PER_ROW - filled).toFloat()))
                        }
                    }
                }
            }
        }

        multis.forEach { relation ->
            Text(
                text = relation.name,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(
                    start = MaterialTheme.padding.small,
                    end = MaterialTheme.padding.small,
                    top = MaterialTheme.padding.small,
                ),
            )

            LazyRow(
                contentPadding = PaddingValues(MaterialTheme.padding.small),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
            ) {
                items(relation.anime, key = { it.id }) { anime ->
                    Box(modifier = Modifier.width(RELATED_ITEM_WIDTH)) {
                        RelatedAnimeItem(
                            anime = anime,
                            displayMode = displayMode,
                            onClick = { onRelatedClick(anime) },
                            onLongClick = { onRelatedLongClick(anime) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RelatedAnimeSingleRow(
    relations: List<AnimeRelationGroup>,
    displayMode: LibraryDisplayMode,
    onRelatedClick: (Anime) -> Unit,
    onRelatedLongClick: (Anime) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
        verticalAlignment = Alignment.Bottom,
    ) {
        relations.forEach { relation ->
            itemsIndexed(
                items = relation.anime,
                key = { _, r -> r.id },
            ) { i, r ->
                Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall)) {
                    if (i == 0) {
                        Text(
                            text = relation.name,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(
                                horizontal = MaterialTheme.padding.small,
                            ),
                        )
                    }

                    Box(modifier = Modifier.width(RELATED_ITEM_WIDTH)) {
                        RelatedAnimeItem(
                            anime = r,
                            displayMode = displayMode,
                            maxLines = 2,
                            onClick = { onRelatedClick(r) },
                            onLongClick = { onRelatedLongClick(r) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun RelatedAnimeItem(
    anime: Anime,
    displayMode: LibraryDisplayMode,
    maxLines: Int = 3,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val coverAlpha = if (anime.favorite) CommonAnimeItemDefaults.BrowseFavoriteCoverAlpha else 1f

    when (displayMode) {
        LibraryDisplayMode.ComfortableGrid,
        LibraryDisplayMode.List,
        -> {
            AnimeComfortableGridItem(
                title = anime.title,
                titleMaxLines = maxLines,
                coverData = anime.asAnimeCover(),
                coverAlpha = coverAlpha,
                coverBadgeStart = { InLibraryBadge(enabled = anime.favorite) },
                onClick = onClick,
                onLongClick = onLongClick,
            )
        }
        LibraryDisplayMode.CompactGrid -> {
            AnimeCompactGridItem(
                title = anime.title,
                coverData = anime.asAnimeCover(),
                coverAlpha = coverAlpha,
                coverBadgeStart = { InLibraryBadge(enabled = anime.favorite) },
                onClick = onClick,
                onLongClick = onLongClick,
            )
        }
        LibraryDisplayMode.CoverOnlyGrid -> {
            AnimeCompactGridItem(
                coverData = anime.asAnimeCover(),
                coverAlpha = coverAlpha,
                coverBadgeStart = { InLibraryBadge(enabled = anime.favorite) },
                onClick = onClick,
                onLongClick = onLongClick,
            )
        }
    }
}

private fun List<AnimeRelationGroup>.packRows(maxPerRow: Int): List<List<AnimeRelationGroup>> {
    if (isEmpty()) return emptyList()

    val total = sumOf { it.anime.size }
    val rowCount = ceil(total / maxPerRow.toDouble()).toInt()
    val base = total / rowCount
    val remainder = total % rowCount

    val rows = mutableListOf<List<AnimeRelationGroup>>()
    var current = mutableListOf<AnimeRelationGroup>()
    var filled = 0
    var cap = base + if (remainder > 0) 1 else 0

    forEach { relation ->
        val size = relation.anime.size
        if (current.isNotEmpty() && filled + size > cap) {
            rows += current
            current = mutableListOf()
            filled = 0
            cap = base + if (rows.size < remainder) 1 else 0
            if (cap < size) cap = maxPerRow
        }
        current += relation
        filled += size
    }
    if (current.isNotEmpty()) rows += current

    return rows
}
// <-- AY
