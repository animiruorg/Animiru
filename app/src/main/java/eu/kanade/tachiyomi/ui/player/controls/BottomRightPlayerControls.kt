/*
 * Copyright 2024 Abdallah Mehiz
 * https://github.com/abdallahmehiz/mpvKt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.kanade.tachiyomi.ui.player.controls

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.kanade.tachiyomi.ui.player.controls.components.ControlsButton
import eu.kanade.tachiyomi.util.system.castIncluded
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.AspectRatio
import mihon.icons.materialsymbols.rounded.PictureInPictureAlt
import tachiyomi.cast.CastButton
import tachiyomi.presentation.core.components.material.padding

@Composable
fun BottomRightPlayerControls(
    castEnabled: Boolean,
    castLoading: Boolean,
    castError: Boolean,
    isPipAvailable: Boolean,
    onAspectClick: () -> Unit,
    onPipClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier) {
        if (castIncluded && castEnabled) {
            CastButton(
                loading = castLoading,
                error = castError,
                verticalSpacing = MaterialTheme.padding.small,
            )
        }

        if (isPipAvailable) {
            ControlsButton(
                MaterialSymbols.Rounded.PictureInPictureAlt,
                onClick = onPipClick,
                verticalSpacing = MaterialTheme.padding.small,
            )
        }

        ControlsButton(
            MaterialSymbols.Rounded.AspectRatio,
            onClick = onAspectClick,
            verticalSpacing = MaterialTheme.padding.small,
        )
    }
}
