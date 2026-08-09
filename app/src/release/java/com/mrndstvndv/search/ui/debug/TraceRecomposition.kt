package com.mrndstvndv.search.ui.debug

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class TraceRecomposition(
    val tag: String = "",
    val threshold: Int = 1,
)
