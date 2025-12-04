package com.mrndstvndv.search.provider

import com.mrndstvndv.search.provider.model.ProviderResult
import com.mrndstvndv.search.provider.model.Query
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

interface Provider {

    /** Unique provider identifier (e.g., "app-list"). */
    val id: String

    /** Human readable name shown in settings or logs. */
    val displayName: String

    /** 
     * Optional flow that emits when results should be refreshed.
     * Providers can emit to this flow to signal that the UI should re-query.
     */
    val refreshSignal: SharedFlow<Unit>
        get() = MutableSharedFlow() // Default: never emits

    /** Optional initialization hook for heavy setup. */
    fun initialize() {}

    /** Returns true when this provider is interested in the current query. */
    fun canHandle(query: Query): Boolean

    /** Resolves zero or more results for the current query. */
    suspend fun query(query: Query): List<ProviderResult>

    /** Cleanup hook for disposing resources, if needed. */
    fun dispose() {}
}
