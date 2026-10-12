// AM (SYNC) -->
package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.connection.SyncPreferences
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.data.SyncSettingsSelector
import eu.kanade.presentation.more.settings.screen.data.SyncTriggerOptionsScreen
import eu.kanade.presentation.util.relativeTimeSpanString
import eu.kanade.tachiyomi.data.connection.syncmiru.SyncDataJob
import eu.kanade.tachiyomi.data.connection.syncmiru.service.GoogleDriveSyncService
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.launch
import mihon.app.di.appGraph
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Close
import tachiyomi.i18n.MR
import tachiyomi.i18n.animiru.AMMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

object SettingsSyncmiruScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = AMMR.strings.pref_category_connection

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val syncPreferences = remember { context.appGraph.syncPreferences }
        val googleDriveRefreshToken by syncPreferences.googleDriveRefreshToken.collectAsState()
        val syncyomiApiKey by syncPreferences.clientAPIKey.collectAsState()

        return listOfNotNull(
            getSyncPreferences(syncPreferences = syncPreferences),
            if (googleDriveRefreshToken.isNotBlank() || syncyomiApiKey.isNotBlank()) {
                getSyncNowPref()
            } else {
                null
            },
            if (googleDriveRefreshToken.isNotBlank() || syncyomiApiKey.isNotBlank()) {
                getAutomaticSyncGroup(syncPreferences = syncPreferences)
            } else {
                null
            },
        )
    }

    @Composable
    private fun getSyncPreferences(syncPreferences: SyncPreferences): Preference.PreferenceGroup {
        val context = LocalContext.current
        val connectionManager = remember { context.appGraph.connectionManager }
        val googleDriveSync = remember { context.appGraph.googleDriveService }
        val googleDriveRefreshToken by syncPreferences.googleDriveRefreshToken.collectAsState()

        var dialog by remember { mutableStateOf<Any?>(null) }
        dialog?.run {
            when (this) {
                is LogoutConnectionDialog -> {
                    ConnectionLogoutDialog(
                        connection = connection,
                        onDismissRequest = { dialog = null },
                    )
                }
                is LoginConnectionDialog -> {
                    SyncYomiLoginDialog(
                        syncPreferences = syncPreferences,
                        onDismissRequest = { dialog = null },
                    )
                }
            }
        }

        return Preference.PreferenceGroup(
            title = stringResource(AMMR.strings.pref_sync_service_category),
            preferenceItems = listOf(
                // AM (SYNC_DRIVE) -->
                Preference.PreferenceItem.ConnectionPreference(
                    title = connectionManager.googleDrive.name,
                    connection = connectionManager.googleDrive,
                    login = {
                        val intent = googleDriveSync.getSignInIntent()
                        context.startActivity(intent)
                    },
                    openSettings = { dialog = LogoutConnectionDialog(connectionManager.googleDrive) },
                ),
                getGoogleDrivePurge(googleDriveRefreshToken),
                // <-- AM (SYNC_DRIVE)
                // AM (SYNC_YOMI) -->
                Preference.PreferenceItem.ConnectionPreference(
                    title = connectionManager.syncyomi.name,
                    connection = connectionManager.syncyomi,
                    login = { dialog = LoginConnectionDialog(connectionManager.syncyomi) },
                    openSettings = { dialog = LogoutConnectionDialog(connectionManager.syncyomi) },
                ),
                // <-- AM (SYNC_YOMI)
            ),
        )
    }

    // AM (SYNC_DRIVE) -->
    @Composable
    private fun getGoogleDrivePurge(googleDriveRefreshToken: String): Preference.PreferenceItem.TextPreference {
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        var showPurgeDialog by remember { mutableStateOf(false) }

        if (showPurgeDialog) {
            val googleDriveSync = remember { GoogleDriveSyncService(context) }
            PurgeConfirmationDialog(
                onConfirm = {
                    showPurgeDialog = false
                    scope.launch {
                        val result = googleDriveSync.deleteSyncDataFromGoogleDrive()
                        when (result) {
                            GoogleDriveSyncService.DeleteSyncDataStatus.NOT_INITIALIZED -> context.toast(
                                AMMR.strings.google_drive_not_signed_in,
                                duration = 5000,
                            )
                            GoogleDriveSyncService.DeleteSyncDataStatus.NO_FILES -> context.toast(
                                AMMR.strings.google_drive_sync_data_not_found,
                                duration = 5000,
                            )
                            GoogleDriveSyncService.DeleteSyncDataStatus.SUCCESS -> context.toast(
                                AMMR.strings.google_drive_sync_data_purged,
                                duration = 5000,
                            )
                            GoogleDriveSyncService.DeleteSyncDataStatus.ERROR -> context.toast(
                                AMMR.strings.google_drive_sync_data_purge_error,
                                duration = 10000,
                            )
                        }
                    }
                },
                onDismissRequest = { showPurgeDialog = false },
            )
        }

        return Preference.PreferenceItem.TextPreference(
            title = stringResource(AMMR.strings.pref_google_drive_purge_sync_data),
            onClick = { showPurgeDialog = true },
            enabled = googleDriveRefreshToken.isNotBlank(),
        )
    }

    @Composable
    private fun PurgeConfirmationDialog(
        onConfirm: () -> Unit,
        onDismissRequest: () -> Unit,
    ) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(text = stringResource(AMMR.strings.pref_purge_confirmation_title)) },
            text = { Text(text = stringResource(AMMR.strings.pref_purge_confirmation_message)) },
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text(text = stringResource(MR.strings.action_ok))
                }
            },
        )
    }
    // <-- AM (SYNC_DRIVE)

    @Composable
    private fun getSyncNowPref(): Preference.PreferenceGroup {
        val navigator = LocalNavigator.currentOrThrow
        return Preference.PreferenceGroup(
            title = stringResource(AMMR.strings.pref_sync_now_group_title),
            preferenceItems = listOf(
                getSyncOptionsPref(),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(AMMR.strings.pref_sync_now),
                    subtitle = stringResource(AMMR.strings.pref_sync_now_subtitle),
                    onClick = {
                        navigator.push(SyncSettingsSelector())
                    },
                ),
            ),
        )
    }

    @Composable
    private fun getSyncOptionsPref(): Preference.PreferenceItem.TextPreference {
        val navigator = LocalNavigator.currentOrThrow
        return Preference.PreferenceItem.TextPreference(
            title = stringResource(AMMR.strings.pref_sync_options),
            subtitle = stringResource(AMMR.strings.pref_sync_options_summ),
            onClick = { navigator.push(SyncTriggerOptionsScreen()) },
        )
    }

    @Composable
    private fun getAutomaticSyncGroup(syncPreferences: SyncPreferences): Preference.PreferenceGroup {
        val context = LocalContext.current
        val syncIntervalPref = syncPreferences.syncInterval
        val lastSync by syncPreferences.lastSyncTimestamp.collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(AMMR.strings.pref_sync_automatic_category),
            preferenceItems = listOf(
                Preference.PreferenceItem.ListPreference(
                    preference = syncIntervalPref,
                    title = stringResource(AMMR.strings.pref_sync_interval),
                    entries = mapOf(
                        0 to stringResource(MR.strings.off),
                        30 to stringResource(AMMR.strings.update_30min),
                        60 to stringResource(AMMR.strings.update_1hour),
                        180 to stringResource(AMMR.strings.update_3hour),
                        360 to stringResource(MR.strings.update_6hour),
                        720 to stringResource(MR.strings.update_12hour),
                        1440 to stringResource(MR.strings.update_24hour),
                        2880 to stringResource(MR.strings.update_48hour),
                        10080 to stringResource(MR.strings.update_weekly),
                    ),
                    onValueChanged = {
                        SyncDataJob.setupTask(context, it)
                        true
                    },
                ),
                Preference.PreferenceItem.InfoPreference(
                    stringResource(AMMR.strings.last_synchronization, relativeTimeSpanString(lastSync)),
                ),
            ),
        )
    }
}

@Composable
private fun SyncYomiLoginDialog(
    syncPreferences: SyncPreferences,
    onDismissRequest: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var host by remember { mutableStateOf(TextFieldValue("")) }
    var apiKey by remember { mutableStateOf(TextFieldValue("")) }
    var inputError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(AMMR.strings.pref_syncyomi_connect),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismissRequest) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Close,
                        contentDescription = stringResource(MR.strings.action_close),
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = host,
                    onValueChange = { host = it },
                    label = { Text(text = stringResource(AMMR.strings.pref_syncyomi_host)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    singleLine = true,
                    isError = inputError && host.text.isEmpty(),
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(text = stringResource(AMMR.strings.pref_syncyomi_api_key)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    singleLine = true,
                    isError = inputError && apiKey.text.isEmpty(),
                )
            }
        },
        confirmButton = {
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (host.text.isEmpty() || apiKey.text.isEmpty()) {
                        inputError = true
                        return@Button
                    }
                    inputError = false
                    scope.launch {
                        // Trim spaces at the beginning and end, then remove trailing slash if present
                        val trimmedValue = host.text.trim()
                        val modifiedValue = trimmedValue.trimEnd { it == '/' }
                        syncPreferences.clientHost.set(modifiedValue)
                    }
                    syncPreferences.clientAPIKey.set(apiKey.text)
                    onDismissRequest()
                },
            ) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
    )
}
// <-- AM (SYNC)
