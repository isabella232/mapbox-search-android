package com.mapbox.search

import com.mapbox.android.core.location.LocationEngine
import com.mapbox.search.analytics.AnalyticsService
import com.mapbox.search.analytics.ErrorsReporter
import com.mapbox.search.analytics.InternalAnalyticsService
import com.mapbox.search.record.FavoritesDataProvider
import com.mapbox.search.record.HistoryDataProvider
import com.mapbox.search.record.HistoryService

/**
 * This class provides access to search-related services and utility classes.
 */
public interface ServiceProvider {

    /**
     * Provides entity to calculate distances between geographical points.
     * @param latitude the area in which fast distance calculation is performed. If second point's latitude far from latitude from constructor, better to use static [DistanceCalculator.distanceOnSphere] to minimize error level.
     * @return [DistanceCalculator] instance.
     */
    public fun distanceCalculator(latitude: Double): DistanceCalculator

    /**
     * Provides entity to calculate highlights ranges in search results.
     * @return [HighlightsCalculator] instance.
     */
    public fun highlightsCalculator(): HighlightsCalculator

    /**
     * Provides entity to work with [com.mapbox.search.record.FavoriteRecord].
     * @return [FavoritesDataProvider] instance.
     */
    public fun favoritesDataProvider(): FavoritesDataProvider

    /**
     * Provides entity to work with [com.mapbox.search.record.HistoryRecord].
     * @return [HistoryDataProvider] instance.
     */
    public fun historyDataProvider(): HistoryDataProvider

    /**
     * Provides entity to get current user location.
     * @return [LocationEngine] instance.
     */
    public fun locationEngine(): LocationEngine

    /**
     * Provides entity to track analytics events.
     * @return [AnalyticsService] instance.
     */
    public fun analyticsService(): AnalyticsService
}

internal interface InternalServiceProvider : ServiceProvider {
    fun internalAnalyticsService(): InternalAnalyticsService

    fun historyService(): HistoryService

    fun errorsReporter(): ErrorsReporter
}

internal class ServiceProviderImpl(
    private val analyticsSender: InternalAnalyticsService,
    private val locationEngine: LocationEngine,
    private val historyDataProvider: HistoryService,
    private val favoritesDataProvider: FavoritesDataProvider,
    private val errorsReporter: ErrorsReporter,
) : ServiceProvider, InternalServiceProvider {

    override fun distanceCalculator(latitude: Double): DistanceCalculator = DistanceCalculatorImpl(latitude)

    override fun highlightsCalculator(): HighlightsCalculator = HighlightsCalculatorImpl()

    override fun favoritesDataProvider(): FavoritesDataProvider = favoritesDataProvider

    override fun historyDataProvider(): HistoryDataProvider = historyDataProvider

    override fun historyService(): HistoryService = historyDataProvider

    override fun internalAnalyticsService(): InternalAnalyticsService = analyticsSender

    override fun locationEngine(): LocationEngine = locationEngine

    override fun analyticsService(): AnalyticsService = analyticsSender

    override fun errorsReporter(): ErrorsReporter = errorsReporter
}
