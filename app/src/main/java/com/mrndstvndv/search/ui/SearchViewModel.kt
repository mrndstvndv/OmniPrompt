package com.mrndstvndv.search.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mrndstvndv.search.alias.AliasCreationCandidate
import com.mrndstvndv.search.alias.AliasEntry
import com.mrndstvndv.search.alias.AliasRepository
import com.mrndstvndv.search.di.AppContainer
import com.mrndstvndv.search.provider.Provider
import com.mrndstvndv.search.provider.model.ProviderResult
import com.mrndstvndv.search.provider.model.Query
import com.mrndstvndv.search.provider.model.SearchTrigger
import com.mrndstvndv.search.provider.model.TriggerParser
import com.mrndstvndv.search.provider.model.TriggerResultPolicy
import com.mrndstvndv.search.provider.model.dynamicTriggerFrequencyQuery
import com.mrndstvndv.search.ui.components.ContactActionData
import com.mrndstvndv.search.ui.components.TriggerState
import com.mrndstvndv.search.ui.components.findTriggerMatch
import com.mrndstvndv.search.util.PerformanceLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

class SearchViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val settingsRepository = container.settingsRepository
    private val aliasRepository = container.aliasRepository
    private val rankingRepository = container.rankingRepository

    private var providers = emptyList<Provider>()
    private var providersById = emptyMap<String, Provider>()
    private var availableTriggers = emptyList<SearchTrigger>()

    // UI state flows
    private val _textState = MutableStateFlow(TextFieldValue(""))
    val textState: StateFlow<TextFieldValue> = _textState.asStateFlow()

    data class SearchUiState(
        val providerResults: List<ProviderResult> = emptyList(),
        val shouldShowResults: Boolean = false,
        val triggerState: TriggerState? = null,
        val currentNormalizedQuery: String = "",
        val showLoadingOverlay: Boolean = false,
        val aliasDialogCandidate: AliasCreationCandidate? = null,
        val aliasDialogValue: String = "",
        val aliasDialogError: String? = null,
        val contactActionData: ContactActionData? = null,
        val matchedAlias: Pair<AliasEntry, String>? = null,
        val isPerformingAction: Boolean = false,
    )

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var pendingQueryJob: Job? = null
    private var pendingActivationEchoToken: String? = null
    private var suppressedTriggerMatch: SuppressedTriggerMatch? = null

    private data class SuppressedTriggerMatch(
        val triggerId: String,
        val matchedToken: String,
    )

    private data class ResultSortMetadata(
        val result: ProviderResult,
        val providerRank: Int,
        val frequencyScore: Float,
    ) {
        val hasFrequency: Boolean
            get() = frequencyScore > 0f
    }

    init {
        val providerList = container.providers
        providers = providerList
        providersById = providerList.associateBy { it.id }
        updateAvailableTriggers()
        observeRefreshSignals()

        // Pre-initialize heavy providers off the main thread
        viewModelScope.launch(Dispatchers.Default) {
            providers.forEach { it.initialize() }
            container.appListRepository.initialize()
        }
    }

    fun updateAvailableTriggers() {
        val enabledProviders = settingsRepository.enabledProviders.value
        val activeProviders = providers.filter { enabledProviders[it.id] ?: true }
        availableTriggers = activeProviders.flatMap { it.triggers }
    }

    private fun observeRefreshSignals() {
        viewModelScope.launch {
            merge(*providers.map { it.refreshSignal }.toTypedArray())
                .collect {
                    PerformanceLogger.log(container.context, "ISSUE_4_EXECUTE_SEARCH_STORM", "observeRefreshSignals: provider refreshSignal triggered executeSearch()")
                    executeSearch()
                }
        }
        viewModelScope.launch {
            aliasRepository.aliases.drop(1).collect {
                PerformanceLogger.log(container.context, "ISSUE_4_EXECUTE_SEARCH_STORM", "observeRefreshSignals: aliasRepository emitted, triggering executeSearch()")
                executeSearch()
            }
        }
        viewModelScope.launch {
            settingsRepository.enabledProviders.drop(1).collect {
                updateAvailableTriggers()
                PerformanceLogger.log(container.context, "ISSUE_4_EXECUTE_SEARCH_STORM", "observeRefreshSignals: enabledProviders emitted, triggering executeSearch()")
                executeSearch()
            }
        }
        viewModelScope.launch {
            rankingRepository.useFrequencyRanking.drop(1).collect {
                PerformanceLogger.log(container.context, "ISSUE_4_EXECUTE_SEARCH_STORM", "observeRefreshSignals: useFrequencyRanking emitted, triggering executeSearch()")
                executeSearch()
            }
        }
    }

    fun executeSearch() {
        pendingQueryJob?.cancel()

        val currentQueryText = _textState.value.text
        val currentTriggerState = _uiState.value.triggerState
        val enabledProviders = settingsRepository.enabledProviders.value
        val activeProviders = providers.filter { enabledProviders[it.id] ?: true }
        val useFrequencyRanking = rankingRepository.useFrequencyRanking.value

        PerformanceLogger.log(container.context, "ISSUE_4_EXECUTE_SEARCH_STORM", "executeSearch launched! Query: '$currentQueryText', ActiveProviders: ${activeProviders.size}")

        pendingQueryJob =
            viewModelScope.launch {
                delay(80)

                if (currentTriggerState != null) {
                    val triggerFrequencyQuery =
                        dynamicTriggerFrequencyQuery(currentTriggerState.matchedToken)
                    _uiState.update { it.copy(currentNormalizedQuery = triggerFrequencyQuery) }
                    try {
                        val triggerResults =
                            withContext(Dispatchers.IO) {
                                currentTriggerState.trigger.execute(currentTriggerState.matchedToken, currentTriggerState.payload)
                            }
                        val payloadQuery =
                            Query(
                                text = currentTriggerState.payload,
                                originalText = buildTriggerText(currentTriggerState.matchedToken, currentTriggerState.payload),
                            )
                        val supplementalProviders =
                            when (currentTriggerState.trigger.resultPolicy) {
                                TriggerResultPolicy.EXCLUSIVE -> emptyList()
                                TriggerResultPolicy.INCLUDE_OWNER_RESULTS -> {
                                    val ownerProvider = providersById[currentTriggerState.trigger.ownerProviderId]
                                    if (ownerProvider != null && (enabledProviders[ownerProvider.id] ?: true) && ownerProvider.canHandle(payloadQuery)) {
                                        listOf(ownerProvider)
                                    } else {
                                        emptyList()
                                    }
                                }
                                TriggerResultPolicy.INCLUDE_ALL_RESULTS -> {
                                    activeProviders.filter {
                                            provider ->
                                        provider.canHandle(payloadQuery)
                                    }
                                }
                            }
                        val supplementalResults =
                            sortResults(queryProviders(payloadQuery, supplementalProviders), triggerFrequencyQuery, useFrequencyRanking)
                        val mergedResults = deduplicateResults(triggerResults + supplementalResults)
                        _uiState.update {
                            it.copy(
                                providerResults = mergedResults,
                                shouldShowResults = mergedResults.isNotEmpty(),
                                matchedAlias = null,
                            )
                        }
                    } catch (e: Exception) {
                        _uiState.update {
                            it.copy(
                                providerResults = emptyList(),
                                shouldShowResults = false,
                                matchedAlias = null,
                            )
                        }
                    }
                    return@launch
                }

                val match = aliasRepository.matchAlias(currentQueryText)
                val normalizedText = match?.remainingQuery ?: currentQueryText
                _uiState.update { it.copy(currentNormalizedQuery = normalizedText) }

                val query = Query(normalizedText, originalText = currentQueryText)
                val matchingProviders =
                    activeProviders.filter {
                            provider ->
                        provider.canHandle(query)
                    }
                val aggregated = queryProviders(query, matchingProviders)
                val filtered =
                    match?.entry?.target?.let { aliasTarget ->
                        aggregated.filterNot { it.aliasTarget == aliasTarget }
                    } ?: aggregated
                val sortedResults = sortResults(filtered, normalizedText, useFrequencyRanking)

                _uiState.update {
                    it.copy(
                        providerResults = sortedResults,
                        shouldShowResults = normalizedText.isNotBlank() || match != null,
                        matchedAlias = match?.let { Pair(it.entry, normalizedText) },
                    )
                }
            }
    }

    fun onSearchChange(newValue: TextFieldValue) {
        val activeTrigger = _uiState.value.triggerState
        if (activeTrigger != null) {
            val activationEchoToken = pendingActivationEchoToken
            val normalizedValue =
                when {
                    activationEchoToken == null -> newValue
                    newValue.text == activationEchoToken || newValue.text == "$activationEchoToken " -> {
                        pendingActivationEchoToken = null
                        newValue.copy(text = "", selection = TextRange.Zero)
                    }
                    else -> {
                        pendingActivationEchoToken = null
                        newValue
                    }
                }

            _uiState.update {
                it.copy(
                    triggerState = activeTrigger.copy(payload = normalizedValue.text),
                )
            }
            _textState.value = normalizedValue
            executeSearch()
            return
        }

        val text = newValue.text
        if (text.isBlank()) {
            suppressedTriggerMatch = null
        }

        val parsedTrigger = TriggerParser.parse(text)
        if (parsedTrigger.hasPayloadSeparator && parsedTrigger.firstToken.isNotBlank()) {
            val firstToken = parsedTrigger.firstToken
            val payload = parsedTrigger.payload
            val match = findTriggerMatch(firstToken, availableTriggers)
            if (match != null) {
                val suppressed = suppressedTriggerMatch
                val isSuppressed =
                    suppressed?.triggerId == match.trigger.id &&
                        suppressed?.matchedToken?.equals(firstToken, ignoreCase = true) == true
                if (!isSuppressed) {
                    suppressedTriggerMatch = null
                    pendingActivationEchoToken = firstToken
                    _uiState.update {
                        it.copy(
                            triggerState =
                                TriggerState(
                                    trigger = match.trigger,
                                    matchedToken = firstToken,
                                    payload = payload,
                                ),
                        )
                    }
                    _textState.value = textFieldValueAtEnd(payload)
                    executeSearch()
                    return
                }
            }
        }

        pendingActivationEchoToken = null
        _textState.value = newValue
        executeSearch()
    }

    fun dismissTrigger() {
        val activeTrigger = _uiState.value.triggerState ?: return
        val restoredText = buildTriggerText(activeTrigger.matchedToken, activeTrigger.payload)

        suppressedTriggerMatch =
            SuppressedTriggerMatch(
                triggerId = activeTrigger.trigger.id,
                matchedToken = activeTrigger.matchedToken,
            )
        pendingActivationEchoToken = null
        _uiState.update { it.copy(triggerState = null) }
        _textState.value = textFieldValueAtEnd(restoredText)
        executeSearch()
    }

    fun applyPrefillQuery(prefillQuery: String) {
        val completedPrefill = ensureTrailingSpace(prefillQuery)
        val parsedTrigger = TriggerParser.parse(completedPrefill)
        val match =
            if (parsedTrigger.hasPayloadSeparator && parsedTrigger.firstToken.isNotBlank()) {
                findTriggerMatch(parsedTrigger.firstToken, availableTriggers)
            } else {
                null
            }

        if (match != null) {
            suppressedTriggerMatch = null
            pendingActivationEchoToken = parsedTrigger.firstToken
            val newTriggerState =
                TriggerState(
                    trigger = match.trigger,
                    matchedToken = parsedTrigger.firstToken,
                    payload = parsedTrigger.payload,
                )
            _uiState.update { it.copy(triggerState = newTriggerState) }
            _textState.value = textFieldValueAtEnd(parsedTrigger.payload)
            executeSearch()
            return
        }

        pendingActivationEchoToken = null
        _textState.value = textFieldValueAtEnd(completedPrefill)
        executeSearch()
    }

    fun recordResultUsage(result: ProviderResult) {
        if (result.excludeFromFrequencyRanking) return
        val freqId = result.frequencyKey
        val freqQuery = result.frequencyQuery ?: _uiState.value.currentNormalizedQuery
        rankingRepository.incrementResultUsage(freqId, freqQuery)
    }

    fun showAliasDialog(candidate: AliasCreationCandidate) {
        _uiState.update {
            it.copy(
                aliasDialogCandidate = candidate,
                aliasDialogValue = candidate.suggestion,
                aliasDialogError = null,
            )
        }
    }

    fun onAliasDialogValueChange(newValue: String) {
        _uiState.update { it.copy(aliasDialogValue = newValue, aliasDialogError = null) }
    }

    fun dismissAliasDialog() {
        _uiState.update {
            it.copy(
                aliasDialogCandidate = null,
                aliasDialogValue = "",
                aliasDialogError = null,
            )
        }
    }

    fun confirmAliasCreation(): Boolean {
        val candidate = _uiState.value.aliasDialogCandidate ?: return false
        val alias = _uiState.value.aliasDialogValue.trim()
        if (alias.isEmpty()) {
            _uiState.update { it.copy(aliasDialogError = "Alias cannot be empty") }
            return false
        }

        val result = aliasRepository.addAlias(alias, candidate.target)
        return when (result) {
            AliasRepository.SaveResult.SUCCESS -> {
                dismissAliasDialog()
                executeSearch()
                true
            }
            AliasRepository.SaveResult.DUPLICATE -> {
                _uiState.update { it.copy(aliasDialogError = "This alias already exists") }
                false
            }
            AliasRepository.SaveResult.INVALID_ALIAS -> {
                _uiState.update { it.copy(aliasDialogError = "Invalid alias format") }
                false
            }
        }
    }

    fun showContactActionSheet(data: ContactActionData) {
        _uiState.update { it.copy(contactActionData = data) }
    }

    fun dismissContactActionSheet() {
        _uiState.update { it.copy(contactActionData = null) }
    }

    fun setShowLoadingOverlay(show: Boolean) {
        _uiState.update { it.copy(showLoadingOverlay = show) }
    }

    fun setIsPerformingAction(performing: Boolean) {
        _uiState.update { it.copy(isPerformingAction = performing) }
    }

    fun setContactActionData(data: ContactActionData?) {
        _uiState.update { it.copy(contactActionData = data) }
    }

    fun clearState() {
        pendingQueryJob?.cancel()
        suppressedTriggerMatch = null
        pendingActivationEchoToken = null
        _textState.value = TextFieldValue("")
        _uiState.value = SearchUiState()
    }

    private fun ensureTrailingSpace(input: String): String {
        val trimmed = input.trimEnd()
        return if (trimmed.isEmpty()) " " else "$trimmed "
    }

    private fun textFieldValueAtEnd(text: String): TextFieldValue =
        TextFieldValue(
            text = text,
            selection = TextRange(text.length),
        )

    private fun buildTriggerText(
        matchedToken: String,
        payload: String,
    ): String =
        when {
            matchedToken.isBlank() -> payload
            payload.isBlank() -> "$matchedToken "
            else -> "$matchedToken $payload"
        }

    private fun deduplicateResults(results: List<ProviderResult>): List<ProviderResult> {
        val seenIds = mutableSetOf<String>()
        return results.filter { seenIds.add(it.id) }
    }

    private suspend fun queryProviders(
        query: Query,
        providersToQuery: List<Provider>,
    ): List<ProviderResult> {
        if (providersToQuery.isEmpty()) return emptyList()
        val allResults =
            supervisorScope {
                providersToQuery.map { provider ->
                    async {
                        try {
                            PerformanceLogger.log(
                                container.context,
                                "ISSUE_6_CONTEXT_SWITCH",
                                "queryProviders: Querying '${provider.id}' inside async { withContext(Dispatchers.IO) } on thread: ${Thread.currentThread().name}"
                            )
                            withContext(Dispatchers.IO) { provider.query(query) }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            e.printStackTrace()
                            emptyList()
                        }
                    }
                }.awaitAll()
            }
        return deduplicateResults(allResults.flatten())
    }

    private fun sortResults(
        results: List<ProviderResult>,
        normalizedText: String,
        useFrequencyRanking: Boolean,
    ): List<ProviderResult> {
        val startNano = System.nanoTime()
        val sortMetadata =
            results.map { result ->
                val providerRank = rankingRepository.getProviderRank(result.providerId)
                val frequencyQuery = result.frequencyQuery ?: normalizedText
                val frequencyScore =
                    if (useFrequencyRanking) {
                        rankingRepository.getResultFrequency(result.frequencyKey, frequencyQuery)
                    } else {
                        0f
                    }
                ResultSortMetadata(
                    result = result,
                    providerRank = providerRank,
                    frequencyScore = frequencyScore,
                )
            }
        val durationMs = (System.nanoTime() - startNano) / 1_000_000.0
        PerformanceLogger.log(
            container.context,
            "ISSUE_5_LINEAR_RANK_LOOKUP",
            "sortResults: Sorted ${results.size} items performing ${results.size} linear indexOf scans on provider order. Time: ${String.format("%.3f", durationMs)} ms"
        )

        if (!useFrequencyRanking) {
            return sortMetadata
                .sortedBy { it.providerRank }
                .map { it.result }
        }

        return sortMetadata
            .sortedWith(
                compareBy<ResultSortMetadata>(
                    { if (it.hasFrequency) 0 else 1 },
                    { if (it.hasFrequency) -it.frequencyScore else it.providerRank.toFloat() },
                ),
            ).map { it.result }
    }
}

class SearchViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SearchViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SearchViewModel(container) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
