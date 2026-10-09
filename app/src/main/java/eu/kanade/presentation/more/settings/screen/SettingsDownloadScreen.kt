package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.util.fastMap
import eu.kanade.domain.base.BasePreferences
import eu.kanade.presentation.category.visualName
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.widget.TriStateListDialog
import eu.kanade.presentation.player.components.OutlinedNumericChooser
import mihon.app.di.appGraph
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.animiru.AMMR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.TextButton
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

object SettingsDownloadScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_downloads

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val getCategories = remember { context.appGraph.getCategories }
        val allCategories by getCategories.subscribe().collectAsState(initial = emptyList())

        val downloadPreferences = remember { context.appGraph.downloadPreferences }
        val parallelSourceLimit by downloadPreferences.parallelSourceLimit.collectAsState()
        // AY -->
        val basePreferences = remember { context.appGraph.basePreferences }
        // <-- AY

        // AM -->
        val internalDownloaderPref = downloadPreferences.useInternalDownloader
        val internalDownloader by internalDownloaderPref.collectAsState()

        val speedLimitPref = downloadPreferences.downloadSpeedLimit
        val speedLimit by speedLimitPref.collectAsState()

        var showDownloadLimitDialog by rememberSaveable { mutableStateOf(false) }
        var currentSpeedLimit by remember { mutableIntStateOf(speedLimit) }

        if (showDownloadLimitDialog) {
            DownloadLimitDialog(
                initialValue = currentSpeedLimit,
                onDismissRequest = {
                    showDownloadLimitDialog = false
                    currentSpeedLimit = speedLimit
                },
                onValueChanged = {
                    currentSpeedLimit = it
                },
                onConfirm = {
                    speedLimitPref.set(currentSpeedLimit)
                    showDownloadLimitDialog = false
                },
            )
        }
        // <-- AM

        return listOf(
            Preference.PreferenceItem.SwitchPreference(
                preference = downloadPreferences.downloadOnlyOverWifi,
                title = stringResource(MR.strings.connected_to_wifi),
            ),
            // AM -->
            Preference.PreferenceItem.SwitchPreference(
                preference = downloadPreferences.ignoreBrokenTracks,
                title = stringResource(AMMR.strings.pref_download_ignore_broken_tracks),
            ),
            // <-- AM
            Preference.PreferenceItem.SliderPreference(
                value = parallelSourceLimit,
                valueRange = 1..10,
                title = stringResource(MR.strings.pref_download_concurrent_sources),
                onValueChanged = { downloadPreferences.parallelSourceLimit.set(it) },
            ),
            // AM -->
            Preference.PreferenceItem.SwitchPreference(
                preference = internalDownloaderPref,
                title = stringResource(AMMR.strings.pref_download_use_internal),
            ),
            Preference.PreferenceItem.ListPreference(
                preference = downloadPreferences.downloadThreads,
                entries = (1..64).associateWith { it.toString() },
                title = stringResource(AMMR.strings.pref_download_threads),
                enabled = internalDownloader,
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(AMMR.strings.am_download_speed_limit),
                subtitle = if (speedLimit == 0) {
                    stringResource(MR.strings.off)
                } else {
                    stringResource(AMMR.strings.am_download_speed_limit_value, speedLimit)
                },
                enabled = internalDownloader,
                onClick = { showDownloadLimitDialog = true },
            ),
            // <-- AM
            getDeleteEpisodesGroup(
                downloadPreferences = downloadPreferences,
                categories = allCategories,
            ),
            getAutoDownloadGroup(
                downloadPreferences = downloadPreferences,
                allCategories = allCategories,
            ),
            getDownloadAheadGroup(downloadPreferences = downloadPreferences),
            // AY -->
            getExternalDownloaderGroup(
                downloadPreferences = downloadPreferences,
                basePreferences = basePreferences,
            ),
            // <-- AY
        )
    }

    @Composable
    private fun getDeleteEpisodesGroup(
        downloadPreferences: DownloadPreferences,
        categories: List<Category>,
    ): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(AMMR.strings.am_pref_category_delete_episodes),
            preferenceItems = listOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = downloadPreferences.removeAfterMarkedAsSeen,
                    title = stringResource(AMMR.strings.am_pref_remove_after_marked_as_seen),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = downloadPreferences.removeAfterSeenSlots,
                    entries = mapOf(
                        -1 to stringResource(MR.strings.disabled),
                        0 to stringResource(AMMR.strings.am_last_seen_episode),
                        1 to stringResource(AMMR.strings.am_second_to_last),
                        2 to stringResource(AMMR.strings.am_third_to_last),
                        3 to stringResource(AMMR.strings.am_fourth_to_last),
                        4 to stringResource(AMMR.strings.am_fifth_to_last),
                    ),
                    title = stringResource(AMMR.strings.am_pref_remove_after_seen),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = downloadPreferences.removeBookmarkedEpisodes,
                    title = stringResource(AMMR.strings.am_pref_remove_bookmarked_episodes),
                ),
                // AY -->
                Preference.PreferenceItem.SwitchPreference(
                    preference = downloadPreferences.downloadFillermarkedEpisodes,
                    title = stringResource(AYMR.strings.pref_download_fillermarked_items),
                ),
                // <-- AY
                getExcludedCategoriesPreference(
                    downloadPreferences = downloadPreferences,
                    categories = { categories },
                ),
            ),
        )
    }

    @Composable
    private fun getExcludedCategoriesPreference(
        downloadPreferences: DownloadPreferences,
        categories: () -> List<Category>,
    ): Preference.PreferenceItem.MultiSelectListPreference {
        return Preference.PreferenceItem.MultiSelectListPreference(
            preference = downloadPreferences.removeExcludeCategories,
            entries = categories()
                .associate { it.id.toString() to it.visualName },
            title = stringResource(MR.strings.pref_remove_exclude_categories),
        )
    }

    @Composable
    private fun getAutoDownloadGroup(
        downloadPreferences: DownloadPreferences,
        allCategories: List<Category>,
    ): Preference.PreferenceGroup {
        val downloadNewEpisodesPref = downloadPreferences.downloadNewEpisodes
        val downloadNewUnseenEpisodesOnlyPref = downloadPreferences.downloadNewUnseenEpisodesOnly
        val downloadNewEpisodeCategoriesPref = downloadPreferences.downloadNewEpisodeCategories
        val downloadNewEpisodeCategoriesExcludePref = downloadPreferences.downloadNewEpisodeCategoriesExclude

        val downloadNewEpisodes by downloadNewEpisodesPref.collectAsState()

        val included by downloadNewEpisodeCategoriesPref.collectAsState()
        val excluded by downloadNewEpisodeCategoriesExcludePref.collectAsState()
        var showDialog by rememberSaveable { mutableStateOf(false) }
        if (showDialog) {
            TriStateListDialog(
                title = stringResource(MR.strings.categories),
                message = stringResource(MR.strings.pref_download_new_categories_details),
                items = allCategories,
                initialChecked = included.mapNotNull { id -> allCategories.find { it.id.toString() == id } },
                initialInversed = excluded.mapNotNull { id -> allCategories.find { it.id.toString() == id } },
                itemLabel = { it.visualName },
                onDismissRequest = { showDialog = false },
                onValueChanged = { newIncluded, newExcluded ->
                    downloadNewEpisodeCategoriesPref.set(newIncluded.fastMap { it.id.toString() }.toSet())
                    downloadNewEpisodeCategoriesExcludePref.set(newExcluded.fastMap { it.id.toString() }.toSet())
                    showDialog = false
                },
            )
        }

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_auto_download),
            preferenceItems = listOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = downloadNewEpisodesPref,
                    title = stringResource(AYMR.strings.pref_download_new_episodes),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = downloadNewUnseenEpisodesOnlyPref,
                    title = stringResource(AYMR.strings.pref_download_new_unseen_episodes_only),
                    enabled = downloadNewEpisodes,
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.categories),
                    subtitle = getCategoriesLabel(
                        allCategories = allCategories,
                        included = included,
                        excluded = excluded,
                    ),
                    enabled = downloadNewEpisodes,
                    onClick = { showDialog = true },
                ),
            ),
        )
    }

    @Composable
    private fun getDownloadAheadGroup(
        downloadPreferences: DownloadPreferences,
    ): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.download_ahead),
            preferenceItems = listOf(
                Preference.PreferenceItem.ListPreference(
                    preference = downloadPreferences.autoDownloadWhileWatching,
                    entries = listOf(0, 2, 3, 5, 10)
                        .associateWith {
                            if (it == 0) {
                                stringResource(MR.strings.disabled)
                            } else {
                                pluralStringResource(AYMR.plurals.next_unseen_episodes, count = it, it)
                            }
                        },
                    title = stringResource(AYMR.strings.auto_download_while_watching),
                ),
                Preference.PreferenceItem.InfoPreference(stringResource(AMMR.strings.am_download_ahead_info)),
            ),
        )
    }

    // AY -->
    @Composable
    private fun getExternalDownloaderGroup(
        downloadPreferences: DownloadPreferences,
        basePreferences: BasePreferences,
    ): Preference.PreferenceGroup {
        val useExternalDownloader = downloadPreferences.useExternalDownloader
        val externalDownloaderPreference = downloadPreferences.externalDownloaderSelection

        val pm = basePreferences.context.packageManager
        val installedPackages = pm.getInstalledPackages(0)
        val supportedDownloaders = installedPackages.filter {
            when (it.packageName) {
                "idm.internet.download.manager" -> true
                "idm.internet.download.manager.plus" -> true
                "idm.internet.download.manager.adm.lite" -> true
                "com.dv.adm" -> true
                else -> false
            }
        }
        val packageNames = supportedDownloaders.map { it.packageName }
        val packageNamesReadable = supportedDownloaders
            .map { pm.getApplicationLabel(it.applicationInfo!!).toString() }

        val packageNamesMap: Map<String, String> =
            mapOf("" to "None") + packageNames.zip(packageNamesReadable).toMap()

        return Preference.PreferenceGroup(
            title = stringResource(AYMR.strings.pref_category_external_downloader),
            preferenceItems = listOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = useExternalDownloader,
                    title = stringResource(AYMR.strings.pref_use_external_downloader),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = externalDownloaderPreference,
                    entries = packageNamesMap,
                    title = stringResource(AYMR.strings.pref_external_downloader_selection),
                ),
            ),
        )
    }
    // <-- AY

    // AM -->
    @Composable
    private fun DownloadLimitDialog(
        initialValue: Int,
        onDismissRequest: () -> Unit,
        onValueChanged: (newValue: Int) -> Unit,
        onConfirm: () -> Unit,
    ) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(stringResource(AYMR.strings.download_speed_limit)) },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .padding(bottom = MaterialTheme.padding.medium)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        OutlinedNumericChooser(
                            value = initialValue,
                            onChange = onValueChanged,
                            max = 1000000,
                            step = 100,
                            min = 0,
                            label = {
                                Text(stringResource(AMMR.strings.am_download_speed_limit))
                            },
                            suffix = {
                                Text(stringResource(AMMR.strings.am_download_speed_limit_unit))
                            },
                        )
                    }
                    Text(text = stringResource(AYMR.strings.download_speed_limit_hint))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onConfirm()
                    },
                ) {
                    Text(text = stringResource(MR.strings.action_ok))
                }
            },
        )
    }
    // <-- AM
}
