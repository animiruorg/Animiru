// AM (STORAGE_SCREEN) -->
package eu.kanade.presentation.more.storage.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.anime.components.AnimeCover
import eu.kanade.presentation.more.storage.data.StorageData
import eu.kanade.tachiyomi.util.storage.toSize
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Delete
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.model.asAnimeCover
import tachiyomi.i18n.MR
import tachiyomi.i18n.animiru.AMMR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.source.local.isLocal

@Composable
fun StorageItem(
    item: StorageData,
    modifier: Modifier = Modifier,
    onDelete: (Boolean) -> Unit,
    onClickCover: () -> Unit,
) {
    var showDeleteDialog by remember {
        mutableStateOf(false)
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
        verticalAlignment = Alignment.CenterVertically,
        content = {
            AnimeCover.Square(
                modifier = Modifier.height(48.dp),
                data = item.anime.asAnimeCover(),
                contentDescription = item.anime.title,
                onClick = onClickCover,
            )
            Column(
                modifier = Modifier.weight(1f),
                content = {
                    Text(
                        text = item.anime.title,
                        style = MaterialTheme.typography.bodyMedium,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.W700,
                        maxLines = 1,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        content = {
                            Box(
                                modifier = Modifier
                                    .background(item.color, CircleShape)
                                    .size(12.dp),
                            )
                            Spacer(Modifier.width(MaterialTheme.padding.small))
                            Text(
                                text = item.size.toSize(),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = MaterialTheme.padding.small / 2)
                                    .background(MaterialTheme.colorScheme.onSurface, CircleShape)
                                    .size(MaterialTheme.padding.small / 2),
                            )
                            Text(
                                text = pluralStringResource(
                                    AYMR.plurals.anime_num_episodes,
                                    count = item.episodeCount,
                                    item.episodeCount,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                    )
                },
            )
            IconButton(
                onClick = {
                    showDeleteDialog = true
                },
                content = {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Delete,
                        contentDescription = stringResource(MR.strings.action_delete),
                    )
                },
            )
        },
    )

    if (showDeleteDialog) {
        ItemDeleteDialog(
            anime = item.anime,
            onDismissRequest = { showDeleteDialog = false },
            onDelete = onDelete,
        )
    }
}

@Composable
private fun ItemDeleteDialog(
    anime: Anime,
    onDismissRequest: () -> Unit,
    onDelete: (Boolean) -> Unit,
) {
    var removeFromLibrary by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = {
                    onDelete(removeFromLibrary)
                    onDismissRequest()
                },
                content = {
                    Text(text = stringResource(MR.strings.action_ok))
                },
            )
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
                content = {
                    Text(text = stringResource(MR.strings.action_cancel))
                },
            )
        },
        title = {
            Text(
                text = stringResource(
                    if (anime.isLocal()) AMMR.strings.delete_local_anime else AYMR.strings.delete_downloads_for_anime,
                ),
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                Text(text = stringResource(AMMR.strings.delete_anime_confirm, anime.title))

                LabeledCheckbox(
                    label = stringResource(MR.strings.remove_from_library),
                    checked = removeFromLibrary,
                    onCheckedChange = { removeFromLibrary = it },
                )
            }
        },
    )
}
// <-- AM (STORAGE_SCREEN)
