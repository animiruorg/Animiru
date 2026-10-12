package tachiyomi.cast

import android.view.View
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import kotlinx.coroutines.delay
import tachiyomi.presentation.core.components.material.padding
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun CastButton(
    loading: Boolean,
    error: Boolean,
    modifier: Modifier = Modifier,
    horizontalSpacing: Dp = MaterialTheme.padding.medium,
    verticalSpacing: Dp = MaterialTheme.padding.medium,
    iconSize: Dp = 20.dp,
) {
    val context = LocalContext.current

    val interactionSource = remember { MutableInteractionSource() }

    var index by remember { mutableIntStateOf(0) }
    val icons = remember {
        listOf(
            R.drawable.ic_cast_1_24dp,
            R.drawable.ic_cast_2_24dp,
            R.drawable.ic_cast_3_24dp,
            R.drawable.ic_cast_2_24dp,
        )
    }

    LaunchedEffect(loading, error) {
        if (loading && !error) {
            while (true) {
                delay(650.milliseconds)
                index = (index + 1) % icons.size
            }
        } else {
            index = 0
        }
    }

    val mediaRouteButton = remember {
        MediaRouteButton(context).also { button ->
            CastButtonFactory.setUpMediaRouteButton(
                context,
                button,
            )
            button.visibility = View.GONE
        }
    }

    AndroidView(factory = { mediaRouteButton })

    Box(
        modifier = modifier
            .combinedClickable(
                enabled = true,
                onClick = { mediaRouteButton.performClick() },
                interactionSource = interactionSource,
                indication = null,
            )
            .clip(CircleShape)
            .indication(
                interactionSource,
                ripple(),
            )
            .padding(
                vertical = verticalSpacing,
                horizontal = horizontalSpacing,
            ),
    ) {
        val icon = when {
            loading -> ImageVector.vectorResource(icons[index])
            error -> ImageVector.vectorResource(R.drawable.cast_warning_24dp)
            else -> ImageVector.vectorResource(R.drawable.ic_cast_24dp)
        }

        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = Color.White,
        )
    }
}
