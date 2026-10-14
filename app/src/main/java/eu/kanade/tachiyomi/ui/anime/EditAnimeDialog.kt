// AM (CUSTOM_INFORMATION) -->
package eu.kanade.tachiyomi.ui.anime

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.anime.components.AnimeCover
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.presentation.components.RadioMenuItem
import eu.kanade.tachiyomi.animesource.model.SAnime
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Add
import mihon.icons.materialsymbols.rounded.ArrowDropDown
import mihon.icons.materialsymbols.rounded.Close
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.model.asAnimeCover
import tachiyomi.i18n.MR
import tachiyomi.i18n.animiru.AMMR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.Button
import tachiyomi.presentation.core.components.material.TextButton
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun EditAnimeDialog(
    anime: Anime,
    onDismissRequest: () -> Unit,
    onPositiveClick: (
        title: String?,
        author: String?,
        artist: String?,
        description: String?,
        tags: List<String>?,
        status: Long?,
    ) -> Unit,
) {
    var title by remember { mutableStateOf(anime.title) }
    var artist by remember { mutableStateOf(anime.artist.orEmpty()) }
    var author by remember { mutableStateOf(anime.author.orEmpty()) }
    var description by remember { mutableStateOf(anime.description.orEmpty()) }
    val genre = remember { anime.genre.orEmpty().toMutableStateList() }
    var status by remember { mutableLongStateOf(anime.status) }

    // Dropdown for publishing status
    var expanded by remember { mutableStateOf(false) }
    val statusOptions = listOf(
        MR.strings.ongoing,
        MR.strings.completed,
        MR.strings.licensed,
        MR.strings.publishing_finished,
        MR.strings.cancelled,
        MR.strings.on_hiatus,
        AYMR.strings.upcoming,
        MR.strings.unknown,
    )
    val statusMap = mapOf(
        MR.strings.unknown to SAnime.UNKNOWN,
        MR.strings.ongoing to SAnime.ONGOING,
        MR.strings.completed to SAnime.COMPLETED,
        MR.strings.licensed to SAnime.LICENSED,
        MR.strings.publishing_finished to SAnime.PUBLISHING_FINISHED,
        MR.strings.cancelled to SAnime.CANCELLED,
        MR.strings.on_hiatus to SAnime.ON_HIATUS,
        AYMR.strings.upcoming to SAnime.UPCOMING,
    )
    var selectedStatus by remember {
        mutableStateOf(
            statusOptions.first {
                statusMap[it] == anime.customStatus?.toInt() ?: 0
            },
        )
    }
    // Text field for adding genres when pressing enter
    var newGenre by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    var showAddGenre by remember { mutableStateOf(false) }
    var addGenreText by remember { mutableStateOf("") }
    if (showAddGenre) {
        AlertDialog(
            onDismissRequest = {
                addGenreText = ""
                showAddGenre = false
            },
            title = { Text(text = stringResource(AMMR.strings.custom_info_add_genre)) },
            dismissButton = {
                TextButton(onClick = {
                    addGenreText = ""
                    showAddGenre = false
                }) {
                    Text(stringResource(MR.strings.action_cancel))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    genre.add(addGenreText)
                    addGenreText = ""
                    showAddGenre = false
                }) {
                    Text(text = stringResource(MR.strings.action_ok))
                }
            },
            text = {
                OutlinedTextField(
                    value = addGenreText,
                    onValueChange = {
                        addGenreText = it
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default.copy(
                        imeAction = ImeAction.Done,
                    ),
                )
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = {
                    onPositiveClick(title, author, artist, description, genre, status)
                    onDismissRequest()
                },
            ) {
                Text(stringResource(MR.strings.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MaterialTheme.padding.small)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                AnimeCover.Book(
                    modifier = Modifier.height(150.dp),
                    data = anime.asAnimeCover(),
                    contentDescription = anime.title,
                )

                Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = MaterialTheme.padding.medium)
                            .clickable { expanded = true },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(selectedStatus),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                        )

                        Icon(MaterialSymbols.Rounded.ArrowDropDown, null)
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        statusOptions.forEach { s ->
                            RadioMenuItem(
                                text = { Text(stringResource(s)) },
                                isChecked = selectedStatus == s,
                                onClick = {
                                    selectedStatus = s
                                    status = statusMap[s]?.toLong() ?: 0L
                                    expanded = false
                                },
                            )
                        }
                    }
                }

                ClearableTextField(
                    value = title,
                    onValueChange = {
                        title = it
                    },
                    hint = stringResource(AMMR.strings.title_hint, anime.ogTitle),
                    modifier = Modifier.fillMaxWidth(),
                )

                ClearableTextField(
                    value = author,
                    onValueChange = {
                        author = it
                    },
                    hint = stringResource(AMMR.strings.author_hint, anime.ogAuthor.orEmpty()),
                    modifier = Modifier.fillMaxWidth(),
                )

                ClearableTextField(
                    value = artist,
                    onValueChange = {
                        artist = it
                    },
                    hint = stringResource(AMMR.strings.artist_hint, anime.ogArtist.orEmpty()),
                    modifier = Modifier.fillMaxWidth(),
                )

                ClearableTextField(
                    value = description,
                    onValueChange = {
                        description = it
                    },
                    singleLine = false,
                    hint = stringResource(AMMR.strings.description_hint, anime.description.orEmpty()),
                    modifier = Modifier.fillMaxWidth(),
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                ) {
                    genre.forEach {
                        AssistChip(
                            onClick = {},
                            trailingIcon = {
                                Icon(
                                    imageVector = MaterialSymbols.Rounded.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(AssistChipDefaults.IconSize)
                                        .clickable { genre.remove(it) },
                                )
                            },
                            label = { Text(it) },
                        )
                    }

                    AssistChip(
                        onClick = { showAddGenre = true },
                        label = { Text(stringResource(AMMR.strings.custom_info_add_genre)) },
                        leadingIcon = {
                            Icon(
                                imageVector = MaterialSymbols.Rounded.Add,
                                contentDescription = null,
                                modifier = Modifier.size(AssistChipDefaults.IconSize),
                            )
                        },
                    )
                }

                Button(onClick = {
                    genre.clear()
                    genre.addAll(anime.ogGenre.orEmpty())
                }) {
                    Text(stringResource(AMMR.strings.custom_info_reset_genre))
                }
            }
        },
    )
}

@Composable
private fun ClearableTextField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                text = hint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        singleLine = singleLine,
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(
                    onClick = { onValueChange("") },
                ) {
                    Icon(
                        MaterialSymbols.Rounded.Close,
                        contentDescription = stringResource(MR.strings.action_reset),
                    )
                }
            }
        },
        modifier = modifier,
    )
}

// <-- AM (CUSTOM_INFORMATION)
