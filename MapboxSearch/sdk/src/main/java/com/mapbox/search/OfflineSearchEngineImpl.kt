package com.mapbox.search

import com.mapbox.common.TileStore
import com.mapbox.common.TilesetDescriptor
import com.mapbox.geojson.Point
import com.mapbox.search.OfflineSearchEngine.EngineReadyCallback
import com.mapbox.search.OfflineSearchEngine.OnIndexChangeListener
import com.mapbox.search.common.logger.logd
import com.mapbox.search.common.printableName
import com.mapbox.search.core.CoreOfflineIndexObserver
import com.mapbox.search.core.CoreSearchEngine
import com.mapbox.search.core.CoreSearchEngineInterface
import com.mapbox.search.core.http.HttpErrorsCache
import com.mapbox.search.engine.BaseSearchEngine
import com.mapbox.search.engine.OneStepRequestCallbackWrapper
import com.mapbox.search.engine.TwoStepsRequestCallbackWrapper
import com.mapbox.search.internal.bindgen.OfflineIndexChangeEvent
import com.mapbox.search.internal.bindgen.OfflineIndexError
import com.mapbox.search.record.HistoryService
import com.mapbox.search.result.BaseSearchSuggestion
import com.mapbox.search.result.GeocodingCompatSearchSuggestion
import com.mapbox.search.result.IndexableRecordSearchSuggestion
import com.mapbox.search.result.SearchResultFactory
import com.mapbox.search.result.SearchSuggestion
import com.mapbox.search.result.ServerSearchSuggestion
import com.mapbox.search.result.mapToCore
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService

internal class OfflineSearchEngineImpl(
    private val coreEngine: CoreSearchEngineInterface,
    private val httpErrorsCache: HttpErrorsCache,
    private val historyService: HistoryService,
    private val requestContextProvider: SearchRequestContextProvider,
    private val searchResultFactory: SearchResultFactory,
    private val engineExecutorService: ExecutorService,
    override val tileStore: TileStore,
) : BaseSearchEngine(autoCancelPreviousRequest = true), OfflineSearchEngine {

    private val initializationLock = Any()
    private val engineReadyCallbacks = mutableMapOf<EngineReadyCallback, Executor>()

    private val onIndexChangeListenersLock = Any()
    private val onIndexChangeListeners = mutableMapOf<OnIndexChangeListener, CoreOfflineIndexObserver>()

    @Volatile
    private var isEngineReady: Boolean = true

    init {
        coreEngine.setTileStore(tileStore) {
            synchronized(initializationLock) {
                isEngineReady = true
            }
            logd("setTileStore done")
        }
    }

    override fun selectTileset(dataset: String?, version: String?) {
        coreEngine.selectTileset(dataset, version)
    }

    override fun createPlacesTilesetDescriptor(dataset: String, version: String): TilesetDescriptor {
        return CoreSearchEngine.createPlacesTilesetDescriptor(dataset, version)
    }

    override fun createTilesetDescriptor(dataset: String, version: String): TilesetDescriptor {
        return CoreSearchEngine.createTilesetDescriptor(dataset, version)
    }

    override fun search(
        query: String,
        options: OfflineSearchOptions,
        executor: Executor,
        callback: SearchSuggestionsCallback
    ): SearchRequestTask {
        logd("search($query, $options) called")

        return makeRequest(callback, engineExecutorService) { request ->
            coreEngine.searchOffline(
                query, emptyList(), options.mapToCore(),
                TwoStepsRequestCallbackWrapper(
                    coreEngine = coreEngine,
                    httpErrorsCache = httpErrorsCache,
                    historyService = historyService,
                    searchResultFactory = searchResultFactory,
                    callbackExecutor = executor,
                    workerExecutor = engineExecutorService,
                    searchRequestTask = request,
                    searchRequestContext = requestContextProvider.provide(ApiType.SBS),
                    suggestion = null,
                    selectOptions = null,
                    isOfflineSearch = true,
                )
            )
        }
    }

    override fun select(
        suggestion: SearchSuggestion,
        options: SelectOptions,
        executor: Executor,
        callback: SearchSelectionCallback
    ): SearchRequestTask {
        logd("select($suggestion) called")

        val coreRequestOptions = suggestion.requestOptions.mapToCore()

        return when (suggestion) {
            is ServerSearchSuggestion -> makeRequest<SearchSuggestionsCallback>(
                callback, engineExecutorService
            ) { request ->
                coreEngine.retrieveOffline(
                    coreRequestOptions,
                    suggestion.originalSearchResult.mapToCore(),
                    TwoStepsRequestCallbackWrapper(
                        coreEngine = coreEngine,
                        httpErrorsCache = httpErrorsCache,
                        historyService = historyService,
                        searchResultFactory = searchResultFactory,
                        callbackExecutor = executor,
                        workerExecutor = engineExecutorService,
                        searchRequestTask = request,
                        searchRequestContext = suggestion.requestOptions.requestContext,
                        suggestion = suggestion,
                        selectOptions = options,
                        isOfflineSearch = true,
                    )
                )
            }
            is GeocodingCompatSearchSuggestion,
            is IndexableRecordSearchSuggestion -> {
                executor.execute {
                    callback.onError(IllegalArgumentException("Unsupported suggestion type for offline search: ${suggestion.javaClass.printableName}"))
                }
                SearchRequestTaskImpl.completed()
            }
            is BaseSearchSuggestion -> {
                error("Unprocessed suggestion: $suggestion")
            }
        }
    }

    override fun reverseGeocoding(
        options: OfflineReverseGeoOptions,
        executor: Executor,
        callback: SearchCallback
    ): SearchRequestTask {
        return makeRequest(callback, engineExecutorService) { request: SearchRequestTaskImpl<SearchCallback> ->
            coreEngine.reverseGeocodingOffline(
                options.mapToCore(),
                OneStepRequestCallbackWrapper(
                    httpErrorsCache = httpErrorsCache,
                    searchResultFactory = searchResultFactory,
                    callbackExecutor = executor,
                    workerExecutor = engineExecutorService,
                    searchRequestTask = request,
                    searchRequestContext = requestContextProvider.provide(ApiType.SBS),
                    isOffline = true,
                )
            )
        }
    }

    override fun searchAddressesNearby(
        street: String,
        proximity: Point,
        radiusMeters: Double,
        executor: Executor,
        callback: SearchCallback
    ): SearchRequestTask {
        if (radiusMeters < 0.0) {
            executor.execute {
                callback.onError(IllegalArgumentException("Negative radius"))
            }
            return SearchRequestTaskImpl.completed()
        }

        return makeRequest(callback, engineExecutorService) { request: SearchRequestTaskImpl<SearchCallback> ->
            coreEngine.getAddressesOffline(
                street,
                proximity,
                radiusMeters,
                OneStepRequestCallbackWrapper(
                    httpErrorsCache = httpErrorsCache,
                    searchResultFactory = searchResultFactory,
                    callbackExecutor = executor,
                    workerExecutor = engineExecutorService,
                    searchRequestTask = request,
                    searchRequestContext = requestContextProvider.provide(ApiType.SBS),
                    isOffline = true,
                )
            )
        }
    }

    override fun addEngineReadyCallback(executor: Executor, callback: EngineReadyCallback) {
        synchronized(initializationLock) {
            val result = isEngineReady
            if (result) {
                executor.execute {
                    callback.onEngineReady()
                }
            } else {
                engineReadyCallbacks[callback] = executor
            }
        }
    }

    override fun removeEngineReadyCallback(callback: EngineReadyCallback) {
        synchronized(initializationLock) {
            engineReadyCallbacks.remove(callback)
        }
    }

    override fun addOnIndexChangeListener(executor: Executor, listener: OnIndexChangeListener) {
        synchronized(onIndexChangeListenersLock) {
            if (onIndexChangeListeners.contains(listener)) {
                return
            }
            val adapter = OnIndexChangeListenerAdapter(executor, listener)
            coreEngine.addOfflineIndexObserver(adapter)
            onIndexChangeListeners[listener] = adapter
        }
    }

    override fun removeOnIndexChangeListener(listener: OnIndexChangeListener) {
        synchronized(onIndexChangeListenersLock) {
            onIndexChangeListeners[listener]?.let { coreObserver ->
                coreEngine.removeOfflineIndexObserver(coreObserver)
            }
            onIndexChangeListeners.remove(listener)
        }
    }

    private class OnIndexChangeListenerAdapter(
        private val executor: Executor,
        private val listener: OnIndexChangeListener,
    ) : CoreOfflineIndexObserver {

        override fun onIndexChanged(e: OfflineIndexChangeEvent) {
            executor.execute {
                listener.onIndexChange(e.mapToPlatformType())
            }
        }

        override fun onError(e: OfflineIndexError) {
            executor.execute {
                listener.onError(e.mapToPlatformType())
            }
        }
    }
}
