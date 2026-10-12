// AM (DISCORD_RPC) -->
package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.util.fastMap
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.connection.service.ConnectionPreferences
import eu.kanade.presentation.category.visualName
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.widget.TriStateListDialog
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.runBlocking
import mihon.app.di.appGraph
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.automirroredrounded.Help
import mihon.icons.materialsymbols.rounded.Cancel
import tachiyomi.i18n.MR
import tachiyomi.i18n.animiru.AMMR
import tachiyomi.presentation.core.components.material.TextButton
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

object SettingsDiscordScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = AMMR.strings.pref_category_connection

    @Composable
    override fun RowScope.AppBarAction() {
        val uriHandler = LocalUriHandler.current
        IconButton(onClick = { uriHandler.openUri("https://aniyomi.org/docs/guides/tracking") }) {
            Icon(
                imageVector = MaterialSymbols.AutoMirroredRounded.Help,
                contentDescription = stringResource(MR.strings.tracking_guide),
            )
        }
    }

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val connectionPreferences = remember { context.appGraph.connectionPreferences }
        val connectionManager = remember { context.appGraph.connectionManager }
        val enableDRPCPref = connectionPreferences.enableDiscordRPC
        val discordRPCStatus = connectionPreferences.discordRPCStatus

        val enableDRPC by enableDRPCPref.collectAsState()

        var dialog by remember { mutableStateOf<Any?>(null) }
        dialog?.run {
            when (this) {
                is LogoutConnectionDialog -> {
                    ConnectionLogoutDialog(
                        connection = connection,
                        onDismissRequest = {
                            dialog = null
                            enableDRPCPref.set(false)
                            navigator.pop()
                        },
                    )
                }
            }
        }

        return listOf(
            Preference.PreferenceGroup(
                title = stringResource(AMMR.strings.connection_discord),
                preferenceItems = listOf(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = enableDRPCPref,
                        title = stringResource(AMMR.strings.pref_enable_discord_rpc),
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = discordRPCStatus,
                        title = stringResource(AMMR.strings.pref_discord_status),
                        entries = mapOf(
                            -1 to stringResource(AMMR.strings.pref_discord_dnd),
                            0 to stringResource(AMMR.strings.pref_discord_idle),
                            1 to stringResource(AMMR.strings.pref_discord_online),
                        ),
                        enabled = enableDRPC,
                        onValueChanged = {
                            context.toast(MR.strings.requires_app_restart)
                            true
                        },
                    ),
                ),
            ),
            getRPCIncognitoGroup(
                connectionPreferences = connectionPreferences,
                enabled = enableDRPC,
            ),
            getCustomizationGroup(
                connectionPreferences = connectionPreferences,
                enabled = enableDRPC,
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.logout),
                onClick = { dialog = LogoutConnectionDialog(connectionManager.discord) },
            ),
        )
    }

    @Composable
    private fun getRPCIncognitoGroup(
        connectionPreferences: ConnectionPreferences,
        enabled: Boolean,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current
        val getAnimeCategories = remember { context.appGraph.getCategories }
        val allAnimeCategories by getAnimeCategories.subscribe().collectAsState(
            initial = runBlocking {
                getAnimeCategories.await()
            },
        )

        val discordRPCIncognitoPref = connectionPreferences.discordRPCIncognito
        val discordRPCIncognitoCategoriesPref = connectionPreferences.discordRPCIncognitoCategories

        val includedAnime by discordRPCIncognitoCategoriesPref.collectAsState()
        var showAnimeDialog by rememberSaveable { mutableStateOf(false) }
        if (showAnimeDialog) {
            TriStateListDialog(
                title = stringResource(MR.strings.categories),
                message = stringResource(AMMR.strings.pref_discord_incognito_categories_details),
                items = allAnimeCategories,
                initialChecked = includedAnime.mapNotNull { id -> allAnimeCategories.find { it.id.toString() == id } },
                initialInversed = includedAnime.mapNotNull { allAnimeCategories.find { false } },
                itemLabel = { it.visualName },
                onDismissRequest = { showAnimeDialog = false },
                onValueChanged = { newIncluded, _ ->
                    discordRPCIncognitoCategoriesPref.set(
                        newIncluded.fastMap { it.id.toString() }
                            .toSet(),
                    )
                    showAnimeDialog = false
                },
                onlyChecked = true,
            )
        }

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.categories),
            preferenceItems = listOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = discordRPCIncognitoPref,
                    title = stringResource(AMMR.strings.pref_discord_incognito),
                    subtitle = stringResource(AMMR.strings.pref_discord_incognito_summary),
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.categories),
                    subtitle = getCategoriesLabel(
                        allCategories = allAnimeCategories,
                        included = includedAnime,
                    ),
                    onClick = { showAnimeDialog = true },
                ),
                Preference.PreferenceItem.InfoPreference(
                    stringResource(AMMR.strings.pref_discord_incognito_categories_details),
                ),
            ),
            enabled = enabled,
        )
    }

    @Composable
    private fun getCustomizationGroup(
        connectionPreferences: ConnectionPreferences,
        enabled: Boolean,
    ): Preference.PreferenceGroup {
        val customMessagePref = connectionPreferences.discordCustomMessage
        val showProgressPref = connectionPreferences.discordShowProgress
        val showEpisodeTitlePref = connectionPreferences.discordShowEpisodeTitle
        val showTimestampPref = connectionPreferences.discordShowTimestamp
        val showButtonsPref = connectionPreferences.discordShowButtons
        val showDownloadButtonPref = connectionPreferences.discordShowDownloadButton
        val showDiscordButtonPref = connectionPreferences.discordShowDiscordButton

        val showProgress by showProgressPref.collectAsState()
        val showButtons by showButtonsPref.collectAsState()

        var showCustomMessageDialog by rememberSaveable { mutableStateOf(false) }
        var tempCustomMessage by rememberSaveable { mutableStateOf(customMessagePref.get()) }

        if (showCustomMessageDialog) {
            AlertDialog(
                onDismissRequest = {
                    showCustomMessageDialog = false
                    tempCustomMessage = customMessagePref.get()
                },
                title = { Text(stringResource(AMMR.strings.pref_discord_custom_message)) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = tempCustomMessage,
                            onValueChange = { tempCustomMessage = it },
                            label = { Text(stringResource(AMMR.strings.pref_discord_custom_message_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = { tempCustomMessage = "" }) {
                                    Icon(imageVector = MaterialSymbols.Rounded.Cancel, contentDescription = null)
                                }
                            },
                            singleLine = true,
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            customMessagePref.set(tempCustomMessage)
                            showCustomMessageDialog = false
                        },
                    ) {
                        Text(stringResource(MR.strings.action_ok))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showCustomMessageDialog = false
                            tempCustomMessage = customMessagePref.get()
                        },
                    ) {
                        Text(stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }

        return Preference.PreferenceGroup(
            title = stringResource(AMMR.strings.pref_category_discord_customization),
            enabled = enabled,
            preferenceItems = listOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(AMMR.strings.pref_discord_custom_message),
                    subtitle = stringResource(AMMR.strings.pref_discord_custom_message_summary),
                    onClick = { showCustomMessageDialog = true },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = showProgressPref,
                    title = stringResource(AMMR.strings.pref_discord_show_progress),
                    subtitle = stringResource(AMMR.strings.pref_discord_show_progress_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = showEpisodeTitlePref,
                    title = stringResource(AMMR.strings.pref_discord_show_episode_title),
                    subtitle = stringResource(AMMR.strings.pref_discord_show_episode_title_summary),
                    enabled = showProgress,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = showTimestampPref,
                    title = stringResource(AMMR.strings.pref_discord_show_timestamp),
                    subtitle = stringResource(AMMR.strings.pref_discord_show_timestamp_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = showButtonsPref,
                    title = stringResource(AMMR.strings.pref_discord_show_buttons),
                    subtitle = stringResource(AMMR.strings.pref_discord_show_buttons_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = showDownloadButtonPref,
                    title = stringResource(AMMR.strings.pref_discord_show_download_button),
                    subtitle = stringResource(AMMR.strings.pref_discord_show_download_button_summary),
                    enabled = showButtons,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = showDiscordButtonPref,
                    title = stringResource(AMMR.strings.pref_discord_show_discord_button),
                    subtitle = stringResource(AMMR.strings.pref_discord_show_discord_button_summary),
                    enabled = showButtons,
                ),
            ),
        )
    }
}
// <-- AM (DISCORD_RPC)
