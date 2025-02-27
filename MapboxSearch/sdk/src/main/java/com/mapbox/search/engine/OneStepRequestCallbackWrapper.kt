package com.mapbox.search.engine

import androidx.collection.SparseArrayCompat
import com.mapbox.search.AsyncOperationTask
import com.mapbox.search.ResponseInfo
import com.mapbox.search.SearchCallback
import com.mapbox.search.SearchRequestException
import com.mapbox.search.SearchRequestTaskImpl
import com.mapbox.search.common.reportRelease
import com.mapbox.search.common.throwDebug
import com.mapbox.search.core.CoreSearchCallback
import com.mapbox.search.core.CoreSearchResponse
import com.mapbox.search.core.http.HttpErrorsCache
import com.mapbox.search.internal.bindgen.SearchResponseError
import com.mapbox.search.mapToPlatform
import com.mapbox.search.markExecutedAndRunOnCallback
import com.mapbox.search.plusAssign
import com.mapbox.search.result.SearchRequestContext
import com.mapbox.search.result.SearchResult
import com.mapbox.search.result.SearchResultFactory
import com.mapbox.search.result.mapToPlatform
import java.util.concurrent.Executor

internal class OneStepRequestCallbackWrapper(
    private val httpErrorsCache: HttpErrorsCache,
    private val searchResultFactory: SearchResultFactory,
    private val callbackExecutor: Executor,
    private val workerExecutor: Executor,
    private val searchRequestTask: SearchRequestTaskImpl<SearchCallback>,
    private val searchRequestContext: SearchRequestContext,
    private val isOffline: Boolean
) : CoreSearchCallback {

    override fun run(response: CoreSearchResponse) {
        workerExecutor.execute {
            if (searchRequestTask.isCompleted) {
                return@execute
            }

            // Update context with Response UUID, which can be obtained only after successful request
            val newContext = searchRequestContext.copy(responseUuid = response.responseUUID)

            val tasks = mutableListOf<AsyncOperationTask>()

            try {
                if (response.results.isError) {
                    val coreError = response.results.error
                    if (coreError == null) {
                        reportRelease(IllegalStateException("CoreSearchResponse.isError == true but error is null"))
                        return@execute
                    }

                    when (coreError.typeInfo) {
                        SearchResponseError.Type.HTTP_ERROR -> {
                            val error = httpErrorsCache.getAndRemove(response.requestID) ?: SearchRequestException(
                                message = coreError.httpError.message,
                                code = coreError.httpError.httpCode
                            )

                            reportRelease(error)
                            searchRequestTask.markExecutedAndRunOnCallback(callbackExecutor) {
                                onError(error)
                            }
                        }
                        SearchResponseError.Type.INTERNAL_ERROR -> {
                            val error = Exception(
                                "Unable to perform search request: ${coreError.internalError.message}"
                            )

                            reportRelease(error)
                            searchRequestTask.markExecutedAndRunOnCallback(callbackExecutor) {
                                onError(error)
                            }
                        }
                        SearchResponseError.Type.REQUEST_CANCELLED -> {
                            searchRequestTask.cancel()
                        }
                        null -> {
                            val error = IllegalStateException("CoreSearchResponse.error.typeInfo is null")
                            reportRelease(error)
                            searchRequestTask.markExecutedAndRunOnCallback(callbackExecutor) {
                                onError(error)
                            }
                        }
                    }
                    return@execute
                }

                val responseResult = requireNotNull(response.results.value)

                val request = response.request.mapToPlatform(searchRequestContext = newContext)
                val responseInfo = ResponseInfo(request, response.mapToPlatform(), isReproducible = !isOffline)

                val results = SparseArrayCompat<Result<SearchResult>>()

                fun notifyCallbackIfNeeded() {
                    if (results.size() == responseResult.size) {
                        val searchResults = mutableListOf<SearchResult>()
                        responseResult.indices.forEach { resultIndex ->
                            with(results[resultIndex]) {
                                if (this != null && isSuccess) {
                                    searchResults.add(getOrThrow())
                                } else {
                                    throwDebug(this?.exceptionOrNull()) {
                                        "Can't parse data from backend: ${responseResult[resultIndex]}"
                                    }
                                }
                            }
                        }
                        searchRequestTask.markExecutedAndRunOnCallback(callbackExecutor) {
                            onResults(searchResults, responseInfo)
                        }
                    }
                }

                responseResult.forEachIndexed { index, coreSearchResult ->
                    val originalSearchResult = coreSearchResult.mapToPlatform()
                    when {
                        searchResultFactory.isResolvedSearchResult(originalSearchResult) -> {
                            val searchResult = searchResultFactory.createSearchResult(originalSearchResult, request)
                            val res = when {
                                searchResult != null -> Result.success(searchResult)
                                else -> Result.failure(Exception("Can't resolve search result: $originalSearchResult"))
                            }
                            results.append(index, res)
                        }
                        searchResultFactory.isUserRecord(originalSearchResult) -> {
                            val task = searchResultFactory.resolveIndexableRecordSearchResultAsync(
                                originalSearchResult,
                                workerExecutor,
                                request
                            ) { result ->
                                with(result) {
                                    results.append(index, this)
                                    notifyCallbackIfNeeded()
                                }
                            }
                            searchRequestTask += task
                            tasks.add(task)
                        }
                        else -> {
                            results.append(index, Result.failure(Exception("Can't resolve search result $originalSearchResult")))
                        }
                    }

                    notifyCallbackIfNeeded()
                }

                if (responseResult.isEmpty()) {
                    searchRequestTask.markExecutedAndRunOnCallback(callbackExecutor) {
                        onResults(emptyList(), responseInfo)
                    }
                }
            } catch (e: Exception) {
                tasks.forEach { it.cancel() }

                if (!searchRequestTask.callbackActionExecuted && !searchRequestTask.isCancelled) {
                    searchRequestTask.markExecutedAndRunOnCallback(callbackExecutor) {
                        onError(e)
                    }
                    reportRelease(e)
                } else {
                    throw e
                }
            }
        }
    }
}
