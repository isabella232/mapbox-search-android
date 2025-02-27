package com.mapbox.search.analytics

import com.mapbox.geojson.BoundingBox
import com.mapbox.geojson.Point
import com.mapbox.search.ApiType
import com.mapbox.search.BaseTest
import com.mapbox.search.Country
import com.mapbox.search.EtaType
import com.mapbox.search.Language
import com.mapbox.search.MapboxSearchSdk
import com.mapbox.search.QueryType
import com.mapbox.search.RouteOptions
import com.mapbox.search.SearchEngine
import com.mapbox.search.SearchEngineSettings
import com.mapbox.search.SearchNavigationOptions
import com.mapbox.search.SearchNavigationProfile
import com.mapbox.search.SearchOptions
import com.mapbox.search.common.FixedPointLocationEngine
import com.mapbox.search.tests_support.BlockingSearchSelectionCallback
import com.mapbox.search.utils.FormattedTimeProvider
import com.mapbox.search.utils.KeyboardLocaleProvider
import com.mapbox.search.utils.UUIDProvider
import com.mapbox.search.utils.orientation.ScreenOrientation
import com.mapbox.search.utils.orientation.ScreenOrientationProvider
import com.mapbox.search.utils.removeJsonPrettyPrinting
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.Locale
import java.util.concurrent.TimeUnit

internal class TelemetryServiceTest : BaseTest() {

    private lateinit var mockServer: MockWebServer
    private lateinit var searchEngine: SearchEngine
    private lateinit var analyticsService: AnalyticsService

    private val formattedTimeProvider: FormattedTimeProvider = FormattedTimeProvider { TEST_LOCAL_TIME_FORMATTED }
    private val uuidProvider: UUIDProvider = UUIDProvider { TEST_UUID }
    private val keyboardLocaleProvider: KeyboardLocaleProvider = KeyboardLocaleProvider { TEST_KEYBOARD_LOCALE }
    private val orientationProvider: ScreenOrientationProvider = ScreenOrientationProvider { TEST_ORIENTATION }
    private val noOpErrorsReporter: ErrorsReporter = ErrorsReporter {}

    @Before
    override fun setUp() {
        super.setUp()

        mockServer = MockWebServer()
        mockServer.start(TEST_WEB_SERVER_PORT)

        val searchEngineSettings = SearchEngineSettings(
            singleBoxSearchBaseUrl = mockServer.url("").toString(),
            geocodingEndpointBaseUrl = mockServer.url("").toString()
        )

        MapboxSearchSdk.initializeInternal(
            application = targetApplication,
            accessToken = TEST_ACCESS_TOKEN,
            locationEngine = FixedPointLocationEngine(TEST_USER_LOCATION),
            searchEngineSettings = searchEngineSettings,
            allowReinitialization = true,
            formattedTimeProvider = formattedTimeProvider,
            uuidProvider = uuidProvider,
            keyboardLocaleProvider = keyboardLocaleProvider,
            orientationProvider = orientationProvider,
            errorsReporter = noOpErrorsReporter,
        )

        searchEngine = MapboxSearchSdk.createSearchEngine(ApiType.SBS, searchEngineSettings, useSharedCoreEngine = true)

        analyticsService = MapboxSearchSdk.serviceProvider.analyticsService()
    }

    @Test
    fun testRawFeedbackCreationForSearchSuggestion() {
        mockServer.enqueue(createSuccessfulResponse("sbs_responses/suggestions-successful-for-minsk.json"))

        val callback = BlockingSearchSelectionCallback()
        val options = SearchOptions(
            origin = TEST_ORIGIN_LOCATION,
            navigationOptions = TEST_NAV_OPTIONS,
            boundingBox = BoundingBox.fromLngLats(0.0, 0.0, 90.0, 45.0),
            countries = listOf(Country.FRANCE, Country.GERMANY),
            languages = listOf(Language.ENGLISH),
            limit = 6,
            types = listOf(QueryType.POI, QueryType.ADDRESS, QueryType.POSTCODE),
            routeOptions = RouteOptions(
                listOf(Point.fromLngLat(2.0, -2.0), Point.fromLngLat(4.0, -4.0)),
                RouteOptions.Deviation.Time(
                    5,
                    TimeUnit.MINUTES,
                )
            ),
            unsafeParameters = mapOf("unsafe_key" to "unsafe_value"),
        )
        searchEngine.search(TEST_QUERY, options, callback)

        val res = callback.getResultBlocking()
        val (suggestions, responseInfo) = res as BlockingSearchSelectionCallback.SearchEngineResult.Suggestions
        val suggestion = suggestions.first()

        val rawEvent = analyticsService.createRawFeedbackEvent(suggestion, responseInfo)

        val expectedRawEvent = readBytesFromAssets("analytics/search-suggestion-raw-feedback.json")
            .let(::String)
            .removeJsonPrettyPrinting()

        Assert.assertEquals(expectedRawEvent, rawEvent)
    }

    @Test
    fun testRawFeedbackCreationForSearchResult() {
        mockServer.enqueue(createSuccessfulResponse("sbs_responses/suggestions-successful-for-minsk.json"))

        var callback = BlockingSearchSelectionCallback()
        val options = SearchOptions(
            origin = TEST_ORIGIN_LOCATION,
            navigationOptions = TEST_NAV_OPTIONS,
            boundingBox = BoundingBox.fromLngLats(0.0, 0.0, 90.0, 45.0),
            countries = listOf(Country.FRANCE, Country.GERMANY),
            languages = listOf(Language.ENGLISH),
            limit = 6,
            types = listOf(QueryType.POI, QueryType.ADDRESS, QueryType.POSTCODE),
            routeOptions = RouteOptions(
                listOf(Point.fromLngLat(2.0, -2.0), Point.fromLngLat(4.0, -4.0)),
                RouteOptions.Deviation.Time(
                    5,
                    TimeUnit.MINUTES,
                )
            ),
            unsafeParameters = mapOf("unsafe_key" to "unsafe_value"),
        )
        searchEngine.search(TEST_QUERY, options, callback)

        val res = callback.getResultBlocking()
        val suggestions = (res as BlockingSearchSelectionCallback.SearchEngineResult.Suggestions).suggestions

        mockServer.enqueue(createSuccessfulResponse("sbs_responses/retrieve-response-successful.json"))

        callback = BlockingSearchSelectionCallback()
        searchEngine.select(suggestions.first(), callback)

        val selectionResult = callback.getResultBlocking()
        val (searchResult, responseInfo) = selectionResult as BlockingSearchSelectionCallback.SearchEngineResult.Result

        val rawEvent = analyticsService.createRawFeedbackEvent(searchResult, responseInfo)

        val expectedRawEvent = readBytesFromAssets("analytics/search-result-raw-feedback.json")
            .let(::String)
            .removeJsonPrettyPrinting()

        Assert.assertEquals(expectedRawEvent, rawEvent)
    }

    @After
    override fun tearDown() {
        mockServer.shutdown()
    }

    private companion object {
        const val TEST_QUERY = "Starbucks"
        const val TEST_ACCESS_TOKEN = "pk.test"
        val TEST_USER_LOCATION: Point = Point.fromLngLat(10.1, 11.1234567)
        val TEST_ORIGIN_LOCATION: Point = Point.fromLngLat(10.1, 11.12345)
        val TEST_NAV_OPTIONS: SearchNavigationOptions = SearchNavigationOptions(
            navigationProfile = SearchNavigationProfile.DRIVING,
            etaType = EtaType.NAVIGATION
        )

        const val TEST_LOCAL_TIME_FORMATTED: String = "2021-02-03T20:52:06+0300"
        const val TEST_UUID = "test-generated-uuid"
        const val TEST_WEB_SERVER_PORT = 8679
        val TEST_KEYBOARD_LOCALE: Locale = Locale.ENGLISH
        val TEST_ORIENTATION = ScreenOrientation.PORTRAIT
    }
}
