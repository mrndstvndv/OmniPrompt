package com.mrndstvndv.search.ui.debug

import com.skydoves.compose.stability.runtime.DefaultRecompositionLogger
import com.skydoves.compose.stability.runtime.RecompositionEvent
import com.skydoves.compose.stability.runtime.RecompositionLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

internal data class RecompositionDebugEvent(
    val id: Long,
    val event: RecompositionEvent,
)

internal object RecompositionDebugState {
    private const val MAX_EVENTS = 60
    private val nextId = AtomicLong()
    private val _events = MutableStateFlow<List<RecompositionDebugEvent>>(emptyList())

    val events: StateFlow<List<RecompositionDebugEvent>> = _events.asStateFlow()

    fun record(event: RecompositionEvent) {
        val debugEvent = RecompositionDebugEvent(nextId.incrementAndGet(), event)
        _events.update { currentEvents ->
            listOf(debugEvent) + currentEvents.take(MAX_EVENTS - 1)
        }
    }
}

internal class RecompositionDebugLogger : RecompositionLogger {
    private val logcatLogger = DefaultRecompositionLogger()

    override fun log(event: RecompositionEvent) {
        logcatLogger.log(event)
        RecompositionDebugState.record(event)
    }
}
