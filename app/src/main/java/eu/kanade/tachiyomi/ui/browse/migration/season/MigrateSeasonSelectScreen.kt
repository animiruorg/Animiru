// AY -->
package eu.kanade.tachiyomi.ui.browse.migration.season

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.browse.BrowseSourceContent
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.ui.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import mihon.feature.migration.dialog.MigrateAnimeDialog
import mihon.feature.migration.dialog.SelectAnimeDialog
import mihon.feature.migration.list.MigrationListScreen
import mihon.presentation.core.util.collectAsLazyPagingItems
import tachiyomi.core.common.Constants
import tachiyomi.domain.anime.model.Anime
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.source.local.LocalSource

data class MigrateSeasonSelectScreen(
    private val oldAnime: Anime,
    private val anime: Anime,
    private val isFromList: Boolean = false,
) : Screen() {
    @Composable
    override fun Content() {
        val uriHandler = LocalUriHandler.current
        val navigator = LocalNavigator.currentOrThrow

        val viewModel = assistedMetroViewModel<MigrateSeasonSelectViewModel, MigrateSeasonSelectViewModel.Factory> {
            create(animeId = anime.id, sourceId = anime.source)
        }
        val state by viewModel.state.collectAsState()

        val source = state.source
        if (source == null) {
            LoadingScreen()
            return
        }

        val snackbarHostState = remember { SnackbarHostState() }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = anime.title,
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { paddingValues ->
            val openDialog: (Anime) -> Unit = {
                val dialog = if (isFromList) {
                    MigrateSeasonSelectViewModel.Dialog.Select(anime = it)
                } else {
                    MigrateSeasonSelectViewModel.Dialog.Migrate(newAnime = it, oldAnime = oldAnime)
                }
                viewModel.setDialog(dialog)
            }
            BrowseSourceContent(
                source = source,
                animeList = viewModel.seasonPagerFlowFlow.collectAsLazyPagingItems(),
                columns = viewModel.getColumnsPreference(LocalConfiguration.current.orientation),
                displayMode = viewModel.displayMode,
                snackbarHostState = snackbarHostState,
                contentPadding = paddingValues,
                onWebViewClick = {
                    val httpSource = source as? AnimeHttpSource ?: return@BrowseSourceContent
                    navigator.push(
                        WebViewScreen(
                            url = httpSource.getHomeUrl(),
                            initialTitle = httpSource.name,
                            sourceId = httpSource.id,
                        ),
                    )
                },
                onHelpClick = { uriHandler.openUri(Constants.URL_HELP) },
                onLocalSourceHelpClick = { uriHandler.openUri(LocalSource.HELP_URL) },
                onAnimeClick = openDialog,
                onAnimeLongClick = { navigator.push(AnimeScreen(it.id, true)) },
            )
        }

        val onDismissRequest = { viewModel.setDialog(null) }
        when (val dialog = state.dialog) {
            is MigrateSeasonSelectViewModel.Dialog.Migrate -> {
                MigrateAnimeDialog(
                    current = dialog.oldAnime,
                    target = dialog.newAnime,
                    onDismissRequest = onDismissRequest,
                    onClickTitle = { navigator.push(AnimeScreen(dialog.newAnime.id)) },
                    onClickSeasons = { navigator.push(MigrateSeasonSelectScreen(oldAnime, dialog.newAnime)) },
                    onComplete = {
                        val animeScreen = navigator.items
                            .filterIsInstance<AnimeScreen>()
                            .lastOrNull()

                        if (animeScreen != null) {
                            navigator.popUntil { it is AnimeScreen }
                            navigator.push(AnimeScreen(dialog.newAnime.id))
                        }
                    },
                )
            }
            is MigrateSeasonSelectViewModel.Dialog.Select -> {
                SelectAnimeDialog(
                    selected = dialog.anime,
                    onDismissRequest = onDismissRequest,
                    onClickTitle = { navigator.push(AnimeScreen(dialog.anime.id)) },
                    onClickSeasons = { navigator.push(MigrateSeasonSelectScreen(oldAnime, dialog.anime, true)) },
                    onClickSelect = {
                        val migrateListScreen = navigator.items
                            .filterIsInstance<MigrationListScreen>()
                            .last()

                        migrateListScreen.addMatchOverride(current = oldAnime.id, target = dialog.anime.id)
                        navigator.popUntil { screen -> screen is MigrationListScreen }
                    },
                )
            }
            null -> {}
        }
    }
}
// <-- AY
