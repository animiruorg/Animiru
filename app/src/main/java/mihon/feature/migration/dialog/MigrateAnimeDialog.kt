package mihon.feature.migration.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.ViewModel
import cafe.adriel.voyager.core.screen.Screen
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.domain.anime.model.hasCustomBackground
import eu.kanade.domain.anime.model.hasCustomCover
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.anime.components.IndicatorSize
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.data.cache.BackgroundCache
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import mihon.domain.migration.models.MigrationFlag
import mihon.domain.migration.usecases.MigrateAnimeUseCase
import mihon.feature.common.utils.getLabel
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Error
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.domain.anime.model.Anime
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

@Composable
internal fun Screen.MigrateAnimeDialog(
    current: Anime,
    target: Anime,
    onClickTitle: () -> Unit,
    // AY -->
    onClickSeasons: () -> Unit,
    // <-- AY
    onDismissRequest: () -> Unit,
    onComplete: () -> Unit = onDismissRequest,
) {
    val scope = rememberCoroutineScope()

    val viewModel = metroViewModel<MigrateDialogViewModel>()
    LaunchedEffect(current, target) {
        viewModel.init(current, target)
    }
    val state by viewModel.state.collectAsState()

    if (state.isMigrated) return

    // AY -->
    val canMigrate = remember(current.fetchType, target.fetchType) { current.fetchType == target.fetchType }
    // <-- AY

    if (state.isMigrating) {
        LoadingScreen(
            modifier = Modifier.background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f)),
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = stringResource(MR.strings.migration_dialog_what_to_include))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                if (canMigrate) {
                    state.applicableFlags.fastForEach { flag ->
                        LabeledCheckbox(
                            label = stringResource(flag.getLabel()),
                            checked = flag in state.selectedFlags,
                            onCheckedChange = { viewModel.toggleSelection(flag) },
                        )
                    }
                } else {
                    // AY -->
                    val message = if (current.fetchType == FetchType.Seasons) {
                        AYMR.strings.label_cant_migrate_season
                    } else {
                        AYMR.strings.label_cant_migrate_episode
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = MaterialSymbols.Rounded.Error,
                            contentDescription = null,
                            modifier = Modifier.size(IndicatorSize),
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = stringResource(message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    // <-- AY
                }
            }
        },
        confirmButton = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
            ) {
                TextButton(
                    onClick = {
                        onDismissRequest()
                        onClickTitle()
                    },
                ) {
                    Text(text = stringResource(MR.strings.action_show_manga))
                }

                // AY -->
                if (target.fetchType != FetchType.Episodes) {
                    TextButton(
                        onClick = {
                            onDismissRequest()
                            onClickSeasons()
                        },
                    ) {
                        Text(text = stringResource(AYMR.strings.label_show_seasons))
                    }
                }
                // <-- AY

                Spacer(modifier = Modifier.weight(1f))

                // AY -->
                if (canMigrate) {
                    // <-- AY
                    TextButton(
                        onClick = {
                            scope.launchIO {
                                viewModel.migrateAnime(replace = false)
                                withUIContext { onComplete() }
                            }
                        },
                    ) {
                        Text(text = stringResource(MR.strings.copy))
                    }
                    TextButton(
                        onClick = {
                            scope.launchIO {
                                viewModel.migrateAnime(replace = true)
                                withUIContext { onComplete() }
                            }
                        },
                    ) {
                        Text(text = stringResource(MR.strings.migrate))
                    }
                }
            }
        },
    )
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class MigrateDialogViewModel(
    private val sourcePreference: SourcePreferences,
    private val coverCache: CoverCache,
    // AY -->
    private val backgroundCache: BackgroundCache,
    // <-- AY
    private val downloadManager: DownloadManager,
    private val migrateAnime: MigrateAnimeUseCase,
) : ViewModel() {

    val state: StateFlow<MigrateDialogViewModel.State>
        field = MutableStateFlow<MigrateDialogViewModel.State>(State())

    fun init(current: Anime, target: Anime) {
        val applicableFlags = buildList {
            MigrationFlag.entries.forEach {
                val applicable = when (it) {
                    MigrationFlag.EPISODE -> true
                    MigrationFlag.CATEGORY -> true
                    MigrationFlag.CUSTOM_COVER -> current.hasCustomCover(coverCache)
                    // AY -->
                    MigrationFlag.CUSTOM_BACKGROUND -> current.hasCustomBackground(backgroundCache)
                    // <-- AY
                    MigrationFlag.NOTES -> current.notes.isNotBlank()
                    MigrationFlag.REMOVE_DOWNLOAD -> downloadManager.getDownloadCount(current) > 0
                }
                if (applicable) add(it)
            }
        }
        val selectedFlags = sourcePreference.migrationFlags.get()
        state.update {
            State(
                current = current,
                target = target,
                applicableFlags = applicableFlags,
                selectedFlags = selectedFlags,
            )
        }
    }

    fun toggleSelection(flag: MigrationFlag) {
        state.update {
            val selectedFlags = it.selectedFlags.toMutableSet()
                .apply { if (contains(flag)) remove(flag) else add(flag) }
                .toSet()
            it.copy(selectedFlags = selectedFlags)
        }
    }

    suspend fun migrateAnime(replace: Boolean) {
        val currentState = state.value
        val current = currentState.current ?: return
        val target = currentState.target ?: return
        sourcePreference.migrationFlags.set(currentState.selectedFlags)
        state.update { it.copy(isMigrating = true) }
        migrateAnime(current, target, replace)
        state.update { it.copy(isMigrating = false, isMigrated = true) }
    }

    data class State(
        val current: Anime? = null,
        val target: Anime? = null,
        val applicableFlags: List<MigrationFlag> = emptyList(),
        val selectedFlags: Set<MigrationFlag> = emptySet(),
        val isMigrating: Boolean = false,
        val isMigrated: Boolean = false,
    )
}
