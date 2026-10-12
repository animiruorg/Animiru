package eu.kanade.presentation.more.settings.widget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Cancel
import mihon.icons.materialsymbols.rounded.Error
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun EditTextPreferenceWidget(
    title: String,
    subtitle: String?,
    // AY -->
    dialogSubtitle: String? = null,
    // <-- AY
    icon: ImageVector?,
    value: String,
    onConfirm: suspend (String) -> Boolean,
    // AY -->
    singleLine: Boolean = true,
    canBeBlank: Boolean = false,
    formatSubtitle: Boolean = true,
    validate: (String) -> Boolean = { true },
    errorMessage: @Composable ((String) -> String)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    // <-- AY
) {
    var isDialogShown by remember { mutableStateOf(false) }

    TextPreferenceWidget(
        title = title,
        // AY -->
        subtitle = if (formatSubtitle) subtitle?.format(value) else subtitle,
        // <-- AY
        icon = icon,
        onPreferenceClick = { isDialogShown = true },
    )

    if (isDialogShown) {
        val scope = rememberCoroutineScope()
        val onDismissRequest = { isDialogShown = false }
        var textFieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
            mutableStateOf(TextFieldValue(value))
        }
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = {
                // AY -->
                Column {
                    Text(text = title)
                    if (dialogSubtitle != null) {
                        Text(text = dialogSubtitle, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                // <-- AY
            },
            text = {
                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = { textFieldValue = it },
                    trailingIcon = {
                        // AY -->
                        if ((textFieldValue.text.isBlank() && !canBeBlank) || !validate(textFieldValue.text)) {
                            // <-- AY
                            Icon(imageVector = MaterialSymbols.Rounded.Error, contentDescription = null)
                        } else {
                            IconButton(onClick = { textFieldValue = TextFieldValue("") }) {
                                Icon(imageVector = MaterialSymbols.Rounded.Cancel, contentDescription = null)
                            }
                        }
                    },
                    // AY -->
                    supportingText = {
                        if (!validate(textFieldValue.text) && errorMessage != null) {
                            Text(errorMessage(textFieldValue.text))
                        }
                    },
                    keyboardOptions = keyboardOptions,
                    isError = (textFieldValue.text.isBlank() && !canBeBlank) || !validate(textFieldValue.text),
                    singleLine = singleLine,
                    // <-- AY
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = true,
            ),
            confirmButton = {
                TextButton(
                    // AY -->
                    enabled = textFieldValue.text != value &&
                        (textFieldValue.text.isNotBlank() || canBeBlank) &&
                        validate(textFieldValue.text),
                    // <-- AY
                    onClick = {
                        scope.launch {
                            if (onConfirm(textFieldValue.text)) {
                                onDismissRequest()
                            }
                        }
                    },
                ) {
                    Text(text = stringResource(MR.strings.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
            },
        )
    }
}
