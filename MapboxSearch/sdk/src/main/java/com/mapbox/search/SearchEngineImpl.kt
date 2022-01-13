package com.mapbox.search

import com.mapbox.search.common.assertDebug
import com.mapbox.search.common.logger.logd
import com.mapbox.search.common.printableName
import com.mapbox.search.common.reportRelease
import com.mapbox.search.core.CoreSearchEngineInterface
import com.mapbox.search.core.http.HttpErrorsCache
import com.mapbox.search.engine.BaseSearchEngine
import com.mapbox.search.engine.TwoStepsBatchRequestCallbackWrapper
import com.mapbox.search.engine.TwoStepsRequestCallbackWrapper
import com.mapbox.search.record.HistoryService
import com.mapbox.search.result.CoreResponseProvider
import com.mapbox.search.result.GeocodingCompatSearchSuggestion
import com.mapbox.search.result.IndexableRecordSearchResultImpl
import com.mapbox.search.result.IndexableRecordSearchSuggestion
import com.mapbox.search.result.SearchResult
import com.mapbox.search.result.SearchResultFactory
import com.mapbox.search.result.SearchSuggestion
import com.mapbox.search.result.ServerSearchResultImpl
import com.mapbox.search.result.ServerSearchSuggestion
import com.mapbox.search.result.mapToCore
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService

internal class SearchEngineImpl(
    override val apiType: ApiType,
    private val coreEngine: CoreSearchEngineInterface,
    private val httpErrorsCache: HttpErrorsCache,
    private val historyService: HistoryService,
    private val requestContextProvider: SearchRequestContextProvider,
    private val searchResultFactory: SearchResultFactory,
    private val engineExecutorService: ExecutorService,
) : BaseSearchEngine(), SearchEngine {

    override fun search(
        query: String,
        options: SearchOptions,
        executor: Executor,
        callback: SearchSuggestionsCallback
    ): SearchRequestTask {
        logd("search($query, $options) called")

        return makeRequest(callback, engineExecutorService) { request ->
            val requestContext = requestContextProvider.provide(apiType)
            coreEngine.search(query, emptyList(), options.mapToCore(),
                TwoStepsRequestCallbackWrapper(
                    apiType = apiType,
                    coreEngine = coreEngine,
                    httpErrorsCache = httpErrorsCache,
                    historyService = historyService,
                    searchResultFactory = searchResultFactory,
                    callbackExecutor = executor,
                    workerExecutor = engineExecutorService,
                    searchRequestTask = request,
                    searchRequestContext = requestContext,
                    suggestion = null,
                    selectOptions = null,
                    isOfflineSearch = false,
                )
            )
        }
    }

    override fun select(
        suggestions: List<SearchSuggestion>,
        executor: Executor,
        callback: SearchMultipleSelectionCallback
    ): SearchRequestTask {
        require(suggestions.isNotEmpty()) {
            "No suggestions were provided! Please, provide at least 1 suggestion."
        }

        if (suggestions.distinctBy { it.requestOptions }.size != 1) {
            executor.execute {
                callback.onError(
                    IllegalArgumentException("All provided suggestions must originate from the same search result!")
                )
            }
            return SearchRequestTaskImpl.completed()
        }

        logd("batch select($suggestions) called")

        val searchResponseInfo = ResponseInfo(suggestions.first().requestOptions, null, isReproducible = false)

        val filtered: List<SearchSuggestion> = suggestions.filter { it.isBatchResolveSupported }
        if (filtered.isEmpty()) {
            executor.execute { callback.onResult(filtered, emptyList(), searchResponseInfo) }
            return SearchRequestTaskImpl.completed()
        }

        logd("Batch retrieve. ${suggestions.size} requested, ${filtered.size} took for processing")

        val alreadyResolved = HashMap<Int, SearchResult>(filtered.size)
        val toResolve = ArrayList<SearchSuggestion>(filtered.size)

        filtered.forEachIndexed { index, suggestion ->
            when (suggestion) {
                !is CoreResponseProvider -> {
                    executor.execute {
                        callback.onError(IllegalArgumentException("SearchSuggestion must provide original response"))
                    }
                    return SearchRequestTaskImpl.completed()
                }
                is GeocodingCompatSearchSuggestion -> {
                    val result = ServerSearchResultImpl(
                        listOf(suggestion.searchResultType),
                        suggestion.originalSearchResult,
                        suggestion.requestOptions
                    )
                    alreadyResolved[index] = result
                }
                is IndexableRecordSearchSuggestion -> {
                    val resolved = IndexableRecordSearchResultImpl(
                        suggestion.record,
                        suggestion.originalSearchResult,
                        suggestion.requestOptions
                    )

                    alreadyResolved[index] = resolved
                }
                is ServerSearchSuggestion -> {
                    toResolve.add(suggestion)
                }
                else -> {
                    val error = IllegalArgumentException("Unknown suggestion type ${suggestion.javaClass.printableName}")
                    executor.execute {
                        reportRelease(error)
                        callback.onError(error)
                    }
                    return SearchRequestTaskImpl.completed()
                }
            }
        }

        return when (alreadyResolved.size) {
            filtered.size -> {
                val result = filtered.indices.mapNotNull { alreadyResolved[it] }
                executor.execute {
                    callback.onResult(filtered, result, searchResponseInfo)
                }
                SearchRequestTaskImpl.completed()
            }
            else -> {
                val coreSearchResults = toResolve.map {
                    (it as CoreResponseProvider).originalSearchResult.mapToCore()
                }

                val resultingFunction: (List<SearchResult>) -> List<SearchResult> = { remoteResults ->
                    assertDebug(remoteResults.size == toResolve.size) {
                        "Not all items has been resolved. " +
                                "To resolve: ${toResolve.map { it.id to it.type }}, " +
                                "actual: ${remoteResults.map { it.id to it.types }}"
                    }
                    if (remoteResults.size != toResolve.size) {
                        alreadyResolved.values + remoteResults
                    } else {
                        val finalSize = alreadyResolved.size + remoteResults.size
                        val result = ArrayList<SearchResult>(finalSize)

                        var remoteResultsIndex = 0
                        (0 until finalSize).map { index ->
                            val element = if (alreadyResolved[index] != null) {
                                alreadyResolved[index]!!
                            } else {
                                remoteResults[remoteResultsIndex++]
                            }
                            result.add(element)
                        }
                        result
                    }
                }

                makeRequest(callback, engineExecutorService) { searchRequestTask ->
                    val requestOptions = toResolve.first().requestOptions
                    val requestContext = requestOptions.requestContext
                    coreEngine.retrieveBucket(
                        requestOptions.mapToCore(),
                        coreSearchResults,
                        TwoStepsBatchRequestCallbackWrapper(
                            suggestions = filtered,
                            httpErrorsCache = httpErrorsCache,
                            searchResultFactory = searchResultFactory,
                            callbackExecutor = executor,
                            workerExecutor = engineExecutorService,
                            searchRequestTask = searchRequestTask,
                            resultingFunction = resultingFunction,
                            searchRequestContext = requestContext
                        )
                    )
                }
            }
        }
    }

    @Suppress("ReturnCount")
    override fun select(
        suggestion: SearchSuggestion,
        options: SelectOptions,
        executor: Executor,
        callback: SearchSelectionCallback
    ): SearchRequestTask {
        logd("select($suggestion, $options) called")

        val coreRequestOptions = suggestion.requestOptions.mapToCore()

        fun completeSearchResultSelection(resolved: SearchResult): SearchRequestTask {
            val searchRequestTask = SearchRequestTaskImpl(callback)

            searchRequestTask += engineExecutorService.submit {
                if (suggestion is CoreResponseProvider) {
                    coreEngine.onSelected(coreRequestOptions, suggestion.originalSearchResult.mapToCore())
                }

                val responseInfo = ResponseInfo(
                    requestOptions = suggestion.requestOptions,
                    coreSearchResponse = null,
                    isReproducible = false
                )

                if (!options.addResultToHistory) {
                    searchRequestTask.markExecutedAndRunOnCallback(executor) {
                        onResult(suggestion, resolved, responseInfo)
                    }
                    return@submit
                }

                if (!searchRequestTask.isCompleted) {
                    searchRequestTask += historyService.addToHistoryIfNeeded(resolved, engineExecutorService, object : CompletionCallback<Boolean> {
                        override fun onComplete(result: Boolean) {
                            searchRequestTask.markExecutedAndRunOnCallback(executor) {
                                onResult(suggestion, resolved, responseInfo)
                            }
                        }

                        override fun onError(e: Exception) {
                            searchRequestTask.markExecutedAndRunOnCallback(executor) {
                                onError(e)
                            }
                        }
                    })
                }
            }

            return searchRequestTask
        }

        return when (suggestion) {
            !is CoreResponseProvider -> {
                executor.execute {
                    callback.onError(IllegalArgumentException("SearchSuggestion must provide original response"))
                }
                SearchRequestTaskImpl.completed()
            }
            is GeocodingCompatSearchSuggestion -> {
                val searchResult = ServerSearchResultImpl(
                    listOf(suggestion.searchResultType),
                    suggestion.originalSearchResult,
                    suggestion.requestOptions
                )
                completeSearchResultSelection(searchResult)
            }
            is ServerSearchSuggestion -> makeRequest<SearchSuggestionsCallback>(callback, engineExecutorService) { request ->
                val requestContext = suggestion.requestOptions.requestContext
                coreEngine.retrieve(
                    coreRequestOptions,
                    suggestion.originalSearchResult.mapToCore(),
                    TwoStepsRequestCallbackWrapper(
                        apiType = apiType,
                        coreEngine = coreEngine,
                        httpErrorsCache = httpErrorsCache,
                        historyService = historyService,
                        searchResultFactory = searchResultFactory,
                        callbackExecutor = executor,
                        workerExecutor = engineExecutorService,
                        searchRequestTask = request,
                        searchRequestContext = requestContext,
                        suggestion = suggestion,
                        selectOptions = options,
                        isOfflineSearch = false,
                    )
                )
            }
            is IndexableRecordSearchSuggestion -> {
                val resolved = IndexableRecordSearchResultImpl(
                    suggestion.record,
                    suggestion.originalSearchResult,
                    suggestion.requestOptions
                )
                completeSearchResultSelection(resolved)
            }
            else -> {
                val error = IllegalArgumentException("Unknown suggestion type ${suggestion.javaClass.printableName}")
                executor.execute {
                    reportRelease(error)
                    callback.onError(error)
                }
                return SearchRequestTaskImpl.completed()
            }
        }
    }
}