package eu.kanade.tachiyomi.ui.player.controls

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeGestures
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds

private enum class ActiveGesture {
    None,
    LongPress,
    PanX,
    PanY,
    Transform,
}

enum class GesturePosition {
    Left,
    Middle,
    Right,
}

sealed interface GestureEvent {
    data class DoubleTap(val gesturePosition: GesturePosition) : GestureEvent
    data class LongPressChange(val start: Boolean) : GestureEvent
    data class LongPressSwipe(val deltaX: Float) : GestureEvent
    data class PanXChange(val start: Boolean) : GestureEvent
    data class PanX(val deltaX: Float) : GestureEvent
    data class PanYChange(val left: Boolean, val start: Boolean) : GestureEvent
    data class PanY(val left: Boolean, val deltaY: Float) : GestureEvent
    data object Tap : GestureEvent
    data class Transform(
        val zoom: Float,
        val pos: Offset,
    ) : GestureEvent
}

@Composable
fun GestureHandler(
    modifier: Modifier,
    areControlsLocked: Boolean,
    interactionSource: MutableInteractionSource,
    onGestureEvent: (GestureEvent) -> Unit,
) {
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeGestures)
            .pointerInput(areControlsLocked) {
                if (areControlsLocked) {
                    return@pointerInput
                }

                val touchSlop = viewConfiguration.touchSlop
                val doubleTapTimeout = viewConfiguration.doubleTapTimeoutMillis
                var lastUpTime = 0L
                var isDoubletapping = false
                var doubleTapJob: Job? = null
                var singleTapJob: Job? = null

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downTime = down.uptimeMillis

                    var totalPanX = 0f
                    var totalPanY = 0f

                    val startingX = down.position.x
                    val startingY = down.position.y

                    val isLeft = startingX < size.width / 2
                    var activeGesture = ActiveGesture.None
                    var isLongPress = false

                    singleTapJob?.cancel()

                    val longPressJob = scope.launch {
                        delay(viewConfiguration.longPressTimeoutMillis)
                        isLongPress = true
                        activeGesture = ActiveGesture.LongPress
                        onGestureEvent(GestureEvent.LongPressChange(start = true))
                    }

                    // Check for drags and such
                    var upTime = 0L
                    do {
                        val event = awaitPointerEvent()
                        val pointerInputs = event.changes

                        val panChange = event.calculatePan()
                        if (pointerInputs.size > 1 &&
                            (activeGesture == ActiveGesture.None || activeGesture == ActiveGesture.Transform)
                        ) {
                            longPressJob.cancel()
                            activeGesture = ActiveGesture.Transform

                            val zoomChange = event.calculateZoom()
                            if (zoomChange != 1f || panChange != Offset.Zero) {
                                onGestureEvent(
                                    GestureEvent.Transform(zoomChange, panChange),
                                )
                            }
                        }

                        totalPanX += panChange.x
                        totalPanY += panChange.y

                        if (!isLongPress && (abs(totalPanX) > touchSlop || abs(totalPanY) > touchSlop)) {
                            longPressJob.cancel()
                            if (activeGesture == ActiveGesture.None) {
                                activeGesture = if (abs(totalPanX) > abs(totalPanY)) {
                                    onGestureEvent(GestureEvent.PanXChange(start = true))
                                    ActiveGesture.PanX
                                } else {
                                    onGestureEvent(GestureEvent.PanYChange(left = isLeft, start = true))
                                    ActiveGesture.PanY
                                }
                            }
                        }

                        when (activeGesture) {
                            ActiveGesture.LongPress -> {
                                onGestureEvent(GestureEvent.LongPressSwipe(panChange.x))
                            }
                            ActiveGesture.PanX -> {
                                onGestureEvent(GestureEvent.PanX(panChange.x))
                            }
                            ActiveGesture.PanY -> {
                                onGestureEvent(
                                    GestureEvent.PanY(isLeft, panChange.y),
                                )
                            }
                            ActiveGesture.None -> {
                                // Ignore
                            }
                            ActiveGesture.Transform -> {
                                // Ignore
                            }
                        }

                        upTime = if (activeGesture == ActiveGesture.None) pointerInputs.first().uptimeMillis else 0
                        pointerInputs.forEach { it.consume() }
                    } while (pointerInputs.any { it.pressed } && scope.isActive)

                    // Finger(s) released
                    scope.ensureActive()
                    longPressJob.cancel()

                    when (activeGesture) {
                        ActiveGesture.None -> {
                            val tapTimeDelta = downTime - lastUpTime
                            if (isDoubletapping || (lastUpTime > 0 && tapTimeDelta < doubleTapTimeout)) {
                                val position = if (startingX > size.width * 3 / 5) {
                                    GesturePosition.Right
                                } else if (startingX < size.width * 2 / 5) {
                                    GesturePosition.Left
                                } else {
                                    GesturePosition.Middle
                                }

                                onGestureEvent(GestureEvent.DoubleTap(position))
                                isDoubletapping = true
                                doubleTapJob?.cancel()
                                doubleTapJob = scope.launch {
                                    delay(0.8.seconds)
                                    isDoubletapping = false
                                }
                            } else {
                                singleTapJob = scope.launch {
                                    delay(doubleTapTimeout)
                                    onGestureEvent(GestureEvent.Tap)
                                }
                            }
                        }
                        ActiveGesture.LongPress -> {
                            onGestureEvent(GestureEvent.LongPressChange(start = false))
                        }
                        ActiveGesture.PanX -> {
                            onGestureEvent(GestureEvent.PanXChange(start = false))
                        }
                        ActiveGesture.PanY -> {
                            onGestureEvent(GestureEvent.PanYChange(left = isLeft, start = false))
                        }
                        ActiveGesture.Transform -> {}
                    }

                    lastUpTime = upTime
                }
            },
    )
}
