// AM (NAVIGATION_PILL) -->
package eu.kanade.tachiyomi.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateValue
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.ui.browse.BrowseTab
import eu.kanade.tachiyomi.ui.library.LibraryTab
import eu.kanade.tachiyomi.ui.updates.UpdatesTab
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import mihon.app.di.appGraph
import tachiyomi.i18n.MR
import tachiyomi.i18n.animiru.AMMR
import tachiyomi.presentation.core.i18n.pluralStringResource

private fun getOffsetX(numOfTabs: Int): Float = (0.5f * numOfTabs) - 0.5f

@Composable
fun NavigationPill(
    tabs: List<Tab>,
    labelFade: Int,
    modifier: Modifier = Modifier,
) {
    val tabNavigator = LocalTabNavigator.current
    val configuration = LocalConfiguration.current

    val pillItemWidth = (configuration.screenWidthDp / tabs.size).dp
    val pillItemHeight = 48.dp

    val currentTabIndex by remember {
        derivedStateOf { tabs.indexOfFirst { it::class == tabNavigator.current::class } }
    }
    val indexedTabs = tabs.mapIndexed { index, tab -> index to tab }
    var oldIndex by remember { mutableIntStateOf(currentTabIndex) }

    val updateTab: (Int) -> Unit = { index ->
        val tab = indexedTabs.getOrNull(index)?.second ?: tabs[0]
        if (tab != tabNavigator.current) {
            tabNavigator.current = tab
        }
    }

    val navigationOffsetX = animateDpAsState(
        targetValue = pillItemWidth * (currentTabIndex - getOffsetX(tabs.size)),
        animationSpec = tween(durationMillis = labelFade * 2),
    )

    BackHandler(
        enabled = tabNavigator.current != LibraryTab,
        onBack = { updateTab(0) },
    )

    LaunchedEffect(currentTabIndex) {
        oldIndex = currentTabIndex
    }

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        var flickOffsetX by remember { mutableFloatStateOf(0f) }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            flickOffsetX += dragAmount.x
                        },
                        onDragEnd = {
                            val newIndex = oldIndex + when {
                                flickOffsetX < 0F -> -1
                                flickOffsetX > 0F -> 1
                                else -> 0
                            }
                            flickOffsetX = 0F
                            updateTab(newIndex.coerceIn(0, tabs.size - 1))
                        },
                    )
                },
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 1.4.dp,
        ) {
            val cornerAnimationSpec: FiniteAnimationSpec<CornerSizes> = tween(durationMillis = labelFade * 2)
            val transition = updateTransition(targetState = currentTabIndex, label = "CornerTransition")

            val cornerSizeConverter = TwoWayConverter<CornerSizes, AnimationVector4D>(
                convertToVector = {
                    AnimationVector4D(it.topStart.value, it.topEnd.value, it.bottomStart.value, it.bottomEnd.value)
                },
                convertFromVector = { CornerSizes(it.v1.dp, it.v2.dp, it.v3.dp, it.v4.dp) },
            )

            val cornerSizes = transition.animateValue(
                transitionSpec = { cornerAnimationSpec },
                typeConverter = cornerSizeConverter,
                label = "CornerSizes",
            ) { state ->
                when (state) {
                    0 -> CornerSizes(0.dp, 28.dp, 0.dp, 28.dp)
                    tabs.size - 1 -> CornerSizes(28.dp, 0.dp, 28.dp, 0.dp)
                    else -> CornerSizes(28.dp, 28.dp, 28.dp, 28.dp)
                }
            }

            NavigationPillItemBackground(
                pillItemWidth = pillItemWidth,
                pillItemHeight = pillItemHeight,
                pillOffsetX = navigationOffsetX,
                cornerSizes = cornerSizes,
            )

            Row(
                modifier = Modifier.fillMaxWidth().windowInsetsPadding(NavigationBarDefaults.windowInsets)
                    .semantics { testTagsAsResourceId = true },
            ) {
                tabs.fastForEach {
                    NavigationPillItem(it, updateTab, pillItemWidth, pillItemHeight)
                }
            }
        }
    }
}

@Composable
private fun NavigationPillItem(
    tab: Tab,
    updateTab: (Int) -> Unit,
    pillItemWidth: Dp,
    pillItemHeight: Dp,
) {
    val tabNavigator = LocalTabNavigator.current
    val navigator = LocalNavigator.currentOrThrow

    val scope = rememberCoroutineScope()
    val selected = tabNavigator.current::class == tab::class
    val tabIndex = tab.options.index.toInt()
    val onClick: () -> Unit = {
        if (!selected) {
            updateTab(tabIndex)
        } else {
            scope.launch { tab.onReselect(navigator) }
        }
    }

    // AM (TAB_HOLD) -->
    val onLongClick: () -> Unit = {
        if (selected) {
            scope.launch { tab.onReselectHold(navigator) }
        }
    }
    // <-- AM (TAB_HOLD)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(width = pillItemWidth, height = pillItemHeight)
                .clip(MaterialTheme.shapes.extraLarge)
                // AM (TAB_HOLD) -->
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = true,
                    role = Role.Tab,
                    onLongClick = onLongClick,
                    onClick = onClick,
                )
                .semantics {
                    this.selected = selected
                }
                .testTag("tab_${tab.options.title}"),
            // <-- AM (TAB_HOLD)
            contentAlignment = Alignment.Center,
        ) {
            NavigationIconItem(tab)
        }

        Text(
            text = tab.options.title,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun NavigationIconItem(tab: Tab) {
    val context = LocalContext.current
    BadgedBox(
        badge = {
            when {
                UpdatesTab::class.isInstance(tab) -> {
                    val count by produceState(initialValue = 0) {
                        val pref = context.appGraph.libraryPreferences
                        pref.newUpdatesCount.changes()
                            .collectLatest { value = if (pref.newShowUpdatesCount.get()) it else 0 }
                    }
                    if (count > 0) {
                        Badge {
                            val desc = pluralStringResource(
                                AMMR.plurals.notification_episodes_generic,
                                count = count,
                                count,
                            )
                            Text(
                                text = count.toString(),
                                modifier = Modifier.semantics { contentDescription = desc },
                            )
                        }
                    }
                }
                BrowseTab::class.isInstance(tab) -> {
                    val count by produceState(initialValue = 0) {
                        val pref = context.appGraph.sourcePreferences
                        pref.extensionUpdatesCount.changes().collectLatest { value = it }
                    }
                    if (count > 0) {
                        Badge {
                            val desc = pluralStringResource(
                                MR.plurals.update_check_notification_ext_updates,
                                count = count,
                                count,
                            )
                            Text(
                                text = count.toString(),
                                modifier = Modifier.semantics { contentDescription = desc },
                            )
                        }
                    }
                }
            }
        },
    ) {
        Icon(
            painter = tab.options.icon!!,
            contentDescription = tab.options.title,
            tint = LocalContentColor.current,
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
private fun NavigationPillItemBackground(
    pillItemWidth: Dp,
    pillItemHeight: Dp,
    pillOffsetX: State<Dp>,
    cornerSizes: State<CornerSizes>,
) {
    Box(
        modifier = Modifier
            .requiredWidth(pillItemWidth)
            .height(pillItemHeight)
            .graphicsLayer {
                translationX = pillOffsetX.value.toPx()
            }
            .background(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(
                    topStart = cornerSizes.value.topStart,
                    topEnd = cornerSizes.value.topEnd,
                    bottomStart = cornerSizes.value.bottomStart,
                    bottomEnd = cornerSizes.value.bottomEnd,
                ),
            ),
    )
}

data class CornerSizes(
    val topStart: Dp,
    val topEnd: Dp,
    val bottomStart: Dp,
    val bottomEnd: Dp,
)
// <-- AM (NAVIGATION_PILL)
