package eu.kanade.presentation.anime

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.anime.components.RelatedAnimeItem
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.tachiyomi.ui.anime.relation.AnimeRelationScreen
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.i18n.animiru.AMMR
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen

@Composable
fun AnimeRelationScreen(
    state: AnimeRelationScreen.State,
    displayMode: LibraryDisplayMode,
    navigateUp: () -> Unit,
    onRefresh: () -> Unit,
    onRelatedClick: (Anime) -> Unit,
    onRelatedLongClick: (Anime) -> Unit,
) {
    Scaffold(
        topBar = { topBarScrollBehavior ->
            AppBar(
                titleContent = {
                    AppBarTitle(
                        title = stringResource(AMMR.strings.animeScreen_relation_header),
                    )
                },
                navigateUp = navigateUp,
                scrollBehavior = topBarScrollBehavior,
            )
        },
    ) { contentPadding ->
        val topPadding = contentPadding.calculateTopPadding()

        PullRefresh(
            refreshing = state.refreshing,
            enabled = true,
            onRefresh = onRefresh,
            indicatorPadding = PaddingValues(top = topPadding),
        ) {
            if (state.relations.isEmpty()) {
                EmptyScreen(
                    message = stringResource(AMMR.strings.animeScreen_no_relations),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.padding(top = topPadding),
                    contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
                ) {
                    items(state.relations) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                        ) {
                            Text(
                                text = it.name,
                                maxLines = 2,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(start = 12.dp),
                            )

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                                contentPadding = PaddingValues(horizontal = 12.dp),
                            ) {
                                items(
                                    items = it.anime,
                                    key = { it.id },
                                ) { r ->
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
            }
        }
    }
}

private val RELATED_ITEM_WIDTH = 128.dp
