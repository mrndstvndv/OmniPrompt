package com.mrndstvndv.search

import com.skydoves.compose.stability.runtime.ComposeStabilityAnalyzer
import com.mrndstvndv.search.ui.debug.RecompositionDebugLogger

internal fun configureRecompositionDebugging() {
    ComposeStabilityAnalyzer.setLogger(RecompositionDebugLogger())
    ComposeStabilityAnalyzer.setEnabled(true)
}
