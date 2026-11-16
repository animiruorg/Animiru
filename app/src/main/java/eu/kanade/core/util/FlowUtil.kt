// AM -->
package eu.kanade.core.util

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration

inline fun <T1, T2, T3, T4, T5, T6, R> combine(
    flow: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    flow4: Flow<T4>,
    flow5: Flow<T5>,
    flow6: Flow<T6>,
    crossinline transform: suspend (T1, T2, T3, T4, T5, T6) -> R,
): Flow<R> {
    return kotlinx.coroutines.flow.combine(flow, flow2, flow3, flow4, flow5, flow6) { args: Array<*> ->
        @Suppress("UNCHECKED_CAST")
        transform(
            args[0] as T1,
            args[1] as T2,
            args[2] as T3,
            args[3] as T4,
            args[4] as T5,
            args[5] as T6,
        )
    }
}

/**
 * Throttles emits to [throttle] duration, but if no values has been emitted for [idle] duration,
 * emit the last value.
 */
fun <T> Flow<T>.throttleWithReset(
    throttle: Duration,
    idle: Duration,
): Flow<T> = channelFlow {
    var lastEmitTime = -(throttle.inWholeMilliseconds + 1)
    var lastEmitted: T? = null
    var idleJob: Job? = null
    var pending: T? = null

    fun emit(value: T) {
        if (value != lastEmitted) {
            lastEmitTime = Clock.System.now().toEpochMilliseconds()
            trySend(value)
            lastEmitted = value
            pending = null
        }
    }

    collect { value ->
        idleJob?.cancel()
        idleJob = launch {
            delay(idle)
            pending?.let {
                emit(it)
            }
        }

        val now = Clock.System.now().toEpochMilliseconds()
        val elapsed = now - lastEmitTime

        if (elapsed >= throttle.inWholeMilliseconds) {
            emit(value)
        } else {
            pending = value
        }
    }
}
// <-- AM
