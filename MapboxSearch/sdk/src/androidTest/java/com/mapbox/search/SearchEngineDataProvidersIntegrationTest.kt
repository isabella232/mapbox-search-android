package com.mapbox.search

import com.mapbox.search.common.FixedPointLocationEngine
import com.mapbox.search.record.FavoritesDataProvider
import com.mapbox.search.record.HistoryDataProvider
import com.mapbox.search.result.SearchSuggestionType
import com.mapbox.search.tests_support.createTestFavoriteRecord
import com.mapbox.search.tests_support.createTestHistoryRecord
import com.mapbox.search.tests_support.record.addAllBlocking
import com.mapbox.search.tests_support.record.clearBlocking
import com.mapbox.search.tests_support.registerDataProviderBlocking
import com.mapbox.search.tests_support.searchBlocking
import com.mapbox.search.tests_support.unregisterDataProviderBlocking
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class SearchEngineDataProvidersIntegrationTest : BaseTest() {

    private lateinit var mockServer: MockWebServer
    private lateinit var historyDataProvider: HistoryDataProvider
    private lateinit var favoritesDataProvider: FavoritesDataProvider
    private lateinit var searchEngineSettings: SearchEngineSettings

    @Before
    override fun setUp() {
        super.setUp()

        mockServer = MockWebServer()

        searchEngineSettings = SearchEngineSettings(
            singleBoxSearchBaseUrl = mockServer.url("").toString(),
            geocodingEndpointBaseUrl = mockServer.url("").toString()
        )

        MapboxSearchSdk.initializeInternal(
            application = targetApplication,
            accessToken = DEFAULT_TEST_ACCESS_TOKEN,
            locationEngine = FixedPointLocationEngine(DEFAULT_TEST_USER_LOCATION),
            searchEngineSettings = searchEngineSettings,
            allowReinitialization = true,
        )

        historyDataProvider = MapboxSearchSdk.serviceProvider.historyDataProvider()
        historyDataProvider.clearBlocking()

        favoritesDataProvider = MapboxSearchSdk.serviceProvider.favoritesDataProvider()
        favoritesDataProvider.clearBlocking()

        addTestHistoryAndFavorites()
    }

    /**
     * The shared [SearchEngine] (acquired from [MapboxSearchSdk.getSearchEngine]) should have
     * [HistoryDataProvider] and [FavoritesDataProvider] registered.
     */
    @Test
    fun testSharedSearchEngineDefaultDataProviders() {
        val sharedSearchEngine = MapboxSearchSdk.createSearchEngine(
            apiType = ApiType.SBS,
            searchEngineSettings = searchEngineSettings,
            useSharedCoreEngine = true
        )
        sharedSearchEngine.assertHistoryAndFavoritesInSearchResults()
    }

    /**
     * [HistoryDataProvider] and [FavoritesDataProvider] can be unregistered from the shared [SearchEngine].
     */
    @Test
    fun testSharedSearchEngineDefaultDataProvidersUnregister() {
        val sharedSearchEngine = MapboxSearchSdk.createSearchEngine(
            apiType = ApiType.SBS,
            searchEngineSettings = searchEngineSettings,
            useSharedCoreEngine = true
        )
        sharedSearchEngine.unregisterDataProviderBlocking(historyDataProvider)
        sharedSearchEngine.unregisterDataProviderBlocking(favoritesDataProvider)
        sharedSearchEngine.assertNoIndexableRecordsInSearchResults()
    }

    /**
     * A new created [SearchEngine] (acquired from [MapboxSearchSdk.createSearchEngine]) should not have
     * [HistoryDataProvider] and [FavoritesDataProvider] registered.
     */
    @Test
    fun testCreatedSearchEngineNoDefaultDataProviders() {
        val searchEngine = MapboxSearchSdk.createSearchEngine(
            apiType = ApiType.SBS,
            searchEngineSettings = searchEngineSettings,
            useSharedCoreEngine = false
        )
        searchEngine.assertNoIndexableRecordsInSearchResults()
    }

    /**
     * [HistoryDataProvider] and [FavoritesDataProvider] can be registered and unregistered in a created [SearchEngine].
     */
    @Test
    fun testCreatedSearchEngineDataProvidersRegistration() {
        val searchEngine = MapboxSearchSdk.createSearchEngine(
            apiType = ApiType.SBS,
            searchEngineSettings = searchEngineSettings,
            useSharedCoreEngine = false
        )

        searchEngine.registerDataProviderBlocking(historyDataProvider)
        searchEngine.registerDataProviderBlocking(favoritesDataProvider)
        searchEngine.assertHistoryAndFavoritesInSearchResults()

        searchEngine.unregisterDataProviderBlocking(historyDataProvider)
        searchEngine.unregisterDataProviderBlocking(favoritesDataProvider)
        searchEngine.assertNoIndexableRecordsInSearchResults()
    }

    /**
     * Several created [SearchEngine]s can have different registered data providers
     */
    @Test
    fun testCreatedSearchEngineDataProvidersIndependence() {
        val searchEngine1 = MapboxSearchSdk.createSearchEngine(
            apiType = ApiType.SBS,
            searchEngineSettings = searchEngineSettings,
            useSharedCoreEngine = false
        )

        searchEngine1.registerDataProviderBlocking(historyDataProvider)
        searchEngine1.registerDataProviderBlocking(favoritesDataProvider)
        searchEngine1.assertHistoryAndFavoritesInSearchResults()

        val searchEngine2 = MapboxSearchSdk.createSearchEngine(
            apiType = ApiType.SBS,
            searchEngineSettings = searchEngineSettings,
            useSharedCoreEngine = false
        )
        searchEngine2.assertNoIndexableRecordsInSearchResults()
    }

    @After
    override fun tearDown() {
        mockServer.shutdown()
        super.tearDown()
    }

    private fun SearchEngine.assertHistoryAndFavoritesInSearchResults() {
        mockServer.enqueue(createSuccessfulResponse("sbs_responses/suggestions-successful-for-minsk.json"))

        val suggestions = searchBlocking(TEST_QUERY).requireSuggestions()
        assertTrue(suggestions.isNotEmpty())
        assertTrue(
            suggestions.any {
                (it.type as? SearchSuggestionType.IndexableRecordItem)?.isFavoriteRecord == true
            }
        )
        assertTrue(
            suggestions.any {
                (it.type as? SearchSuggestionType.IndexableRecordItem)?.isHistoryRecord == true
            }
        )
    }

    private fun SearchEngine.assertNoIndexableRecordsInSearchResults() {
        mockServer.enqueue(createSuccessfulResponse("sbs_responses/suggestions-successful-for-minsk.json"))

        val suggestions = searchBlocking(TEST_QUERY).requireSuggestions()
        assertTrue(suggestions.isNotEmpty())
        assertFalse(
            suggestions.any {
                it.type is SearchSuggestionType.IndexableRecordItem
            }
        )
    }

    private fun addTestHistoryAndFavorites(numberOfRecords: Int = 3) {
        historyDataProvider.addAllBlocking(
            (1..numberOfRecords).map { createTestHistoryRecord(id = "id-history-$it", name = "$TEST_QUERY $it") }
        )

        favoritesDataProvider.addAllBlocking(
            (1..numberOfRecords).map { createTestFavoriteRecord(id = "id-favorite-$it", name = "$TEST_QUERY $it") }
        )
    }

    private companion object {
        const val TEST_QUERY = "Minsk"
    }
}
