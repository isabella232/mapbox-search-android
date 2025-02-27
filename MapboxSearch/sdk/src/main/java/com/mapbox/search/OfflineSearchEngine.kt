package com.mapbox.search

import com.mapbox.common.TileStore
import com.mapbox.common.TilesetDescriptor
import com.mapbox.geojson.Point
import com.mapbox.search.result.SearchResult
import com.mapbox.search.result.SearchSuggestion
import com.mapbox.search.utils.concurrent.SearchSdkMainThreadWorker
import java.util.concurrent.Executor

/**
 * The [OfflineSearchEngine] interface provides forward and reverse geocoding search that works offline.
 * An instance of the [OfflineSearchEngine] can be obtained with [MapboxSearchSdk.getOfflineSearchEngine].
 *
 * Offline forward geocoding search works in a two-step manner, see [SearchEngine] for more details.
 *
 * The API of this class is temporary and subject to change.
 * Tiles loading functionality is available to selected customers only. Contact our team, to get early preview.
 */
public interface OfflineSearchEngine {

    /**
     * Interface definition for a callback to be invoked when the [OfflineSearchEngine] is ready for use.
     * With the current implementation the callback is invoked when previously downloaded offline data has been prepared
     * and available for search.
     *
     * If the callback returns an error, you can continue to use [OfflineSearchEngine] functions,
     * but previously downloaded offline data won't be available for search.
     */
    public interface EngineReadyCallback {

        /**
         * Called when the engine is ready for use.
         */
        public fun onEngineReady()

        // TODO this function is never called
        /**
         * Called if an error occurred.
         * @param e Exception, occurred during the request.
         */
        public fun onError(e: Exception)
    }

    /**
     * Interface for a listener to be invoked when index data is changed in the [OfflineSearchEngine].
     * This interface notifies users when tiles changes made with [com.mapbox.common.TileStore]
     * have been processed and visible in the [OfflineSearchEngine].
     */
    public interface OnIndexChangeListener {

        /**
         * Called when tiles changes made with [com.mapbox.common.TileStore]
         * have been processed and visible in the [OfflineSearchEngine].
         *
         * @param event Information about changes in the offline search index.
         */
        public fun onIndexChange(event: OfflineIndexChangeEvent)

        /**
         * Called when tiles changes made with [com.mapbox.common.TileStore] couldn't be processed in the [OfflineSearchEngine].
         *
         * @param event Information about error.
         */
        public fun onError(event: OfflineIndexErrorEvent)
    }

    /**
     * [TileStore] object used for offline tiles management.
     */
    public val tileStore: TileStore

    /**
     * Selects preferable tileset for offline search. If dataset or version is set, [OfflineSearchEngine] will try to
     * match appropriate tileset and use it. If several tilesets are available, the latest registered will be used.
     *
     * By default, if multiple tilesets are registered at once
     * (e.g. one offline region is loaded for several tilesets in single call),
     * the biggest one (tilesets are compared as pair of strings {dataset, version}) is considered as latest.
     *
     * @param dataset Preferable dataset.
     * @param version Preferable version.
     */
    public fun selectTileset(dataset: String?, version: String?)

    /**
     * Creates TilesetDescriptor for offline search index data using the specified dataset and version.
     * Downloaded data will include addresses and places.
     *
     * @param dataset Tiles dataset.
     * @param version Tiles version, chosen automatically if empty.
     */
    public fun createTilesetDescriptor(dataset: String, version: String): TilesetDescriptor

    /**
     * Creates TilesetDescriptor for offline search index data using default dataset and version.
     * Downloaded data will include addresses and places.
     */
    public fun createTilesetDescriptor(): TilesetDescriptor = createTilesetDescriptor(
        dataset = OfflineSearchEngineSettings.DEFAULT_DATASET,
        version = OfflineSearchEngineSettings.DEFAULT_VERSION,
    )

    /**
     * Creates TilesetDescriptor for offline search using the specified dataset and version.
     * Downloaded data will include only places.
     *
     * @param dataset Tiles dataset.
     * @param version Tiles version, chosen automatically if empty.
     */
    public fun createPlacesTilesetDescriptor(dataset: String, version: String): TilesetDescriptor

    /**
     * Creates TilesetDescriptor for offline search using the specified dataset and version.
     * Downloaded data will include only places.
     */
    public fun createPlacesTilesetDescriptor(): TilesetDescriptor = createPlacesTilesetDescriptor(
        dataset = OfflineSearchEngineSettings.DEFAULT_DATASET,
        version = OfflineSearchEngineSettings.DEFAULT_VERSION,
    )

    /**
     * The first step of the offline forward geocoding. Returns a list of [SearchSuggestion] without coordinates.
     *
     * @param query Search query.
     * @param options Search options.
     * @param executor Executor used for events dispatching. By default events are dispatched on the main thread.
     * @param callback The callback to handle search result.
     * @return [SearchRequestTask] object which allows to cancel the request.
     *
     * @see [SearchEngine.search].
     */
    public fun search(
        query: String,
        options: OfflineSearchOptions,
        executor: Executor,
        callback: SearchSuggestionsCallback,
    ): SearchRequestTask

    /**
     * The first step of the offline forward geocoding. Returns a list of [SearchSuggestion] without coordinates.
     *
     * @param query Search query.
     * @param options Search options.
     * @param callback The callback to handle search result on the main thread.
     * @return [SearchRequestTask] object which allows to cancel the request.
     *
     * @see [SearchEngine.search].
     */
    public fun search(
        query: String,
        options: OfflineSearchOptions,
        callback: SearchSuggestionsCallback,
    ): SearchRequestTask = search(
        query = query,
        options = options,
        executor = SearchSdkMainThreadWorker.mainExecutor,
        callback = callback,
    )

    /**
     * The second step of the offline forward geocoding. Call this function to get a [SearchResult] (with coordinates) from a [SearchSuggestion].
     *
     * @param suggestion Search suggestion to resolve and get the final result with address and coordinates.
     * @param options Options used for controlling internal "select" operation logic.
     * @param executor Executor used for events dispatching. By default events are dispatched on the main thread.
     * @param callback Callback to retrieve [SearchResult] with resolved coordinates.
     * @return [SearchRequestTask] object which allows to cancel the request.
     *
     * @see [SearchEngine.select].
     */
    public fun select(
        suggestion: SearchSuggestion,
        options: SelectOptions,
        executor: Executor,
        callback: SearchSelectionCallback,
    ): SearchRequestTask

    /**
     * The second step of the offline forward geocoding. Call this function to get a [SearchResult] (with coordinates) from a [SearchSuggestion].
     *
     * @param suggestion Search suggestion to resolve and get the final result with address and coordinates.
     * @param options Options used for controlling internal "select" operation logic.
     * @param callback Callback to retrieve [SearchResult] with resolved coordinates.
     * @return [SearchRequestTask] object which allows to cancel the request.
     *
     * @see [SearchEngine.select].
     */
    public fun select(
        suggestion: SearchSuggestion,
        options: SelectOptions,
        callback: SearchSelectionCallback,
    ): SearchRequestTask = select(
        suggestion = suggestion,
        options = options,
        executor = SearchSdkMainThreadWorker.mainExecutor,
        callback = callback,
    )

    /**
     * The second step of the offline forward geocoding. Call this function to get a [SearchResult] (with coordinates) from a [SearchSuggestion].
     *
     * @param suggestion Search suggestion to resolve and get the final result with address and coordinates.
     * @param callback Callback to retrieve [SearchResult] with resolved coordinates.
     * @return [SearchRequestTask] object which allows to cancel the request.
     *
     * @see [SearchEngine.select].
     */
    public fun select(
        suggestion: SearchSuggestion,
        callback: SearchSelectionCallback,
    ): SearchRequestTask = select(
        suggestion = suggestion,
        options = SelectOptions(),
        executor = SearchSdkMainThreadWorker.mainExecutor,
        callback = callback,
    )

    /**
     * Performs reverse geocoding.
     *
     * @param options Reverse geocoding options.
     * @param executor Executor used for events dispatching. By default events are dispatched on the main thread.
     * @param callback Search result callback.
     * @return [SearchRequestTask] object which allows to cancel the request.
     */
    public fun reverseGeocoding(
        options: OfflineReverseGeoOptions,
        executor: Executor,
        callback: SearchCallback,
    ): SearchRequestTask

    /**
     * Performs reverse geocoding.
     *
     * @param options Reverse geocoding options.
     * @param callback Search result callback, delivers results on the main thread.
     * @return [SearchRequestTask] object which allows to cancel the request.
     */
    public fun reverseGeocoding(
        options: OfflineReverseGeoOptions,
        callback: SearchCallback,
    ): SearchRequestTask = reverseGeocoding(
        options = options,
        executor = SearchSdkMainThreadWorker.mainExecutor,
        callback = callback,
    )

    /**
     * Searches for addresses nearby (around [proximity] point), matched with specified [street] name.
     *
     * @param street Street name to match.
     * @param proximity Coordinate to search in its vicinity.
     * @param radiusMeters Radius (in meters) around [proximity].
     * @param executor Executor used for events dispatching. By default events are dispatched on the main thread.
     * @param callback Search result callback.
     * @return [SearchRequestTask] object which allows to cancel the request.
     */
    public fun searchAddressesNearby(
        street: String,
        proximity: Point,
        radiusMeters: Double,
        executor: Executor,
        callback: SearchCallback,
    ): SearchRequestTask

    /**
     * Searches for addresses nearby (around [proximity] point), matched with specified [street] name.
     *
     * @param street Street name to match.
     * @param proximity Coordinate to search in its vicinity.
     * @param radiusMeters Radius (in meters) around [proximity].
     * @param callback Search result callback, delivers results on the main thread.
     * @return [SearchRequestTask] object which allows to cancel the request.
     */
    public fun searchAddressesNearby(
        street: String,
        proximity: Point,
        radiusMeters: Double,
        callback: SearchCallback,
    ): SearchRequestTask = searchAddressesNearby(
        street = street,
        proximity = proximity,
        radiusMeters = radiusMeters,
        executor = SearchSdkMainThreadWorker.mainExecutor,
        callback = callback,
    )

    /**
     * Adds a callback to be notified when engine is ready.
     * If the engine is already ready, callback will be notified immediately.
     *
     * @param executor Executor used for events dispatching. By default events are dispatched on the main thread.
     * @param callback The callback to notify when engine is ready.
     */
    public fun addEngineReadyCallback(executor: Executor, callback: EngineReadyCallback)

    /**
     * Adds a callback to be notified when engine is ready.
     * If the engine is already ready, callback will be notified immediately.
     *
     * @param callback The callback to notify when engine is ready. Events are dispatched on the main thread.
     */
    public fun addEngineReadyCallback(callback: EngineReadyCallback): Unit = addEngineReadyCallback(
        executor = SearchSdkMainThreadWorker.mainExecutor,
        callback = callback,
    )

    /**
     * Removes a previously added callback.
     *
     * @param callback The callback to remove.
     */
    public fun removeEngineReadyCallback(callback: EngineReadyCallback)

    /**
     * Adds a listener to be notified of index change events.
     *
     * @param executor Executor used for events dispatching. By default events are dispatched on the main thread.
     * @param listener The listener to notify when an event happens.
     */
    public fun addOnIndexChangeListener(executor: Executor, listener: OnIndexChangeListener)

    /**
     * Adds a listener to be notified of index change events.
     *
     * @param listener The listener to notify when an event happens. Events are dispatched on the main thread.
     */
    public fun addOnIndexChangeListener(listener: OnIndexChangeListener): Unit = addOnIndexChangeListener(
        executor = SearchSdkMainThreadWorker.mainExecutor,
        listener = listener,
    )

    /**
     * Removes a previously added listener.
     *
     * @param listener The listener to remove.
     */
    public fun removeOnIndexChangeListener(listener: OnIndexChangeListener)
}
