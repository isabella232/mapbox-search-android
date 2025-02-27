package com.mapbox.search

import com.mapbox.geojson.Point
import com.mapbox.search.common.FixedPointLocationEngine
import com.mapbox.search.record.FavoritesDataProvider
import com.mapbox.search.record.HistoryDataProvider
import com.mapbox.search.record.HistoryRecord
import com.mapbox.search.result.IndexableRecordSearchResult
import com.mapbox.search.result.IndexableRecordSearchSuggestion
import com.mapbox.search.result.OriginalResultType
import com.mapbox.search.result.SearchAddress
import com.mapbox.search.result.SearchRequestContext
import com.mapbox.search.tests_support.BlockingCompletionCallback
import com.mapbox.search.tests_support.BlockingSearchSelectionCallback
import com.mapbox.search.tests_support.BlockingSearchSelectionCallback.SearchEngineResult
import com.mapbox.search.tests_support.createTestOriginalSearchResult
import com.mapbox.search.tests_support.equalsTo
import com.mapbox.search.tests_support.record.CustomRecord
import com.mapbox.search.tests_support.record.StubRecordsStorage
import com.mapbox.search.tests_support.record.TestDataProvider
import com.mapbox.search.tests_support.record.clearBlocking
import com.mapbox.search.tests_support.record.getBlocking
import com.mapbox.search.utils.CaptureErrorsReporter
import com.mapbox.search.utils.KeyboardLocaleProvider
import com.mapbox.search.utils.TimeProvider
import com.mapbox.search.utils.UUIDProvider
import com.mapbox.search.utils.concurrent.SearchSdkMainThreadWorker
import com.mapbox.search.utils.orientation.ScreenOrientation
import com.mapbox.search.utils.orientation.ScreenOrientationProvider
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale
import java.util.concurrent.Executor

internal class CustomDataProviderTest : BaseTest() {

    private lateinit var mockServer: MockWebServer
    private lateinit var searchEngine: SearchEngine
    private lateinit var historyDataProvider: HistoryDataProvider
    private lateinit var favoritesDataProvider: FavoritesDataProvider
    private val timeProvider: TimeProvider = TimeProvider { TEST_LOCAL_TIME_MILLIS }
    private val uuidProvider: UUIDProvider = UUIDProvider { TEST_UUID }
    private val keyboardLocaleProvider: KeyboardLocaleProvider = KeyboardLocaleProvider { TEST_KEYBOARD_LOCALE }
    private val orientationProvider: ScreenOrientationProvider = ScreenOrientationProvider { TEST_ORIENTATION }
    private val errorsReporter: CaptureErrorsReporter = CaptureErrorsReporter()
    private val callbacksExecutor: Executor = SearchSdkMainThreadWorker.mainExecutor

    @Before
    override fun setUp() {
        super.setUp()

        mockServer = MockWebServer()

        val searchEngineSettings = SearchEngineSettings(singleBoxSearchBaseUrl = mockServer.url("").toString())

        MapboxSearchSdk.initializeInternal(
            application = targetApplication,
            accessToken = TEST_ACCESS_TOKEN,
            locationEngine = FixedPointLocationEngine(TEST_USER_LOCATION),
            searchEngineSettings = searchEngineSettings,
            allowReinitialization = true,
            timeProvider = timeProvider,
            uuidProvider = uuidProvider,
            keyboardLocaleProvider = keyboardLocaleProvider,
            orientationProvider = orientationProvider,
            errorsReporter = errorsReporter,
        )

        searchEngine = MapboxSearchSdk.createSearchEngine(ApiType.SBS, searchEngineSettings, useSharedCoreEngine = true)

        historyDataProvider = MapboxSearchSdk.serviceProvider.historyDataProvider()
        historyDataProvider.clearBlocking(callbacksExecutor)

        favoritesDataProvider = MapboxSearchSdk.serviceProvider.favoritesDataProvider()
        favoritesDataProvider.clearBlocking(callbacksExecutor)
    }

    @Test
    fun testSuccessfulCustomProviderRegistration() {
        mockServer.enqueue(createSuccessfulResponse("sbs_responses/suggestions-successful-for-minsk.json"))

        val customDataProvider = TestDataProvider(
            stubStorage = StubRecordsStorage(TEST_CUSTOM_USER_RECORDS.toMutableList())
        )
        val secondCustomRecord = TEST_CUSTOM_USER_RECORDS[1]

        val blockingCallback = BlockingCompletionCallback<Unit>()

        searchEngine.registerDataProvider(
            dataProvider = customDataProvider,
            executor = callbacksExecutor,
            callback = blockingCallback,
        )

        assertTrue(blockingCallback.getResultBlocking().isResult)

        val callback = BlockingSearchSelectionCallback()
        val options = SearchOptions(origin = secondCustomRecord.coordinate)
        searchEngine.search(secondCustomRecord.name, options, callback)

        var searchResult = callback.getResultBlocking()
        assertTrue(searchResult is SearchEngineResult.Suggestions)
        val suggestions = (searchResult as SearchEngineResult.Suggestions).suggestions

        assertEquals(
            IndexableRecordSearchSuggestion(
                record = secondCustomRecord,
                originalSearchResult = createTestOriginalSearchResult(
                    id = secondCustomRecord.id,
                    layerId = customDataProvider.dataProviderName,
                    types = listOf(OriginalResultType.USER_RECORD),
                    names = listOf(secondCustomRecord.name),
                    center = secondCustomRecord.coordinate,
                    addresses = listOf(SearchAddress()),
                    distanceMeters = 0.0,
                    serverIndex = null,
                ),
                requestOptions = TEST_REQUEST_OPTIONS.copy(
                    query = secondCustomRecord.name,
                    options = options.copy(
                        proximity = TEST_USER_LOCATION,
                        origin = secondCustomRecord.coordinate
                    ),
                    proximityRewritten = true,
                    requestContext = TEST_REQUEST_OPTIONS.requestContext.copy(
                        responseUuid = "bf62f6f4-92db-11eb-a8b3-0242ac130003"
                    )
                )
            ),
            suggestions[0]
        )

        callback.reset()
        searchEngine.select(suggestions[0], callback)

        searchResult = callback.getResultBlocking()
        assertTrue(searchResult is SearchEngineResult.Result)
        val result = (searchResult as SearchEngineResult.Result).result

        assertTrue(result is IndexableRecordSearchResult)
        result as IndexableRecordSearchResult

        assertEquals(secondCustomRecord, result.record)

        val historyFromCustomRecord = historyDataProvider.getBlocking(secondCustomRecord.id, callbacksExecutor)

        assertEquals(
            HistoryRecord(
                id = secondCustomRecord.id,
                name = secondCustomRecord.name,
                descriptionText = secondCustomRecord.descriptionText,
                address = secondCustomRecord.address ?: SearchAddress(),
                routablePoints = secondCustomRecord.routablePoints,
                categories = secondCustomRecord.categories ?: emptyList(),
                makiIcon = secondCustomRecord.makiIcon,
                coordinate = secondCustomRecord.coordinate,
                type = secondCustomRecord.type,
                metadata = secondCustomRecord.metadata,
                timestamp = TEST_LOCAL_TIME_MILLIS,
            ),
            historyFromCustomRecord
        )
    }

    @Test
    fun testFailCustomProviderRegistration() {
        mockServer.enqueue(createSuccessfulResponse("sbs_responses/suggestions-successful-for-minsk.json"))

        val testException = RuntimeException("Test exception")
        val customDataProvider = TestDataProvider(
            stubStorage = StubRecordsStorage(TEST_CUSTOM_USER_RECORDS.toMutableList())
        ).apply {
            mode = TestDataProvider.Mode.Fail(testException)
        }
        val secondCustomRecord = TEST_CUSTOM_USER_RECORDS[1]

        val blockingCallback = BlockingCompletionCallback<Unit>()

        searchEngine.registerDataProvider(
            dataProvider = customDataProvider,
            executor = callbacksExecutor,
            callback = blockingCallback,
        )

        val res = blockingCallback.getResultBlocking()
        assertTrue(blockingCallback.getResultBlocking().isError)
        assertTrue(testException.equalsTo(res.requireError()))

        val callback = BlockingSearchSelectionCallback()
        val options = SearchOptions(origin = secondCustomRecord.coordinate)
        searchEngine.search(secondCustomRecord.name, options, callback)

        val searchResult = callback.getResultBlocking()
        assertTrue(searchResult is SearchEngineResult.Suggestions)
        val suggestions = (searchResult as SearchEngineResult.Suggestions).suggestions

        assertTrue(
            suggestions.none {
                it is IndexableRecordSearchSuggestion && it.id == secondCustomRecord.id
            }
        )
    }

    @Test
    fun testSuccessfulCustomProviderUnregistration() {
        mockServer.enqueue(createSuccessfulResponse("sbs_responses/suggestions-successful-for-minsk.json"))

        val customDataProvider = TestDataProvider(
            stubStorage = StubRecordsStorage(TEST_CUSTOM_USER_RECORDS.toMutableList())
        )
        val secondCustomRecord = TEST_CUSTOM_USER_RECORDS[1]

        val blockingCallback = BlockingCompletionCallback<Unit>()

        searchEngine.registerDataProvider(
            dataProvider = customDataProvider,
            executor = callbacksExecutor,
            callback = blockingCallback,
        )

        assertTrue(blockingCallback.getResultBlocking().isResult)

        blockingCallback.reset()

        searchEngine.unregisterDataProvider(
            dataProvider = customDataProvider,
            executor = callbacksExecutor,
            callback = blockingCallback,
        )
        assertTrue(blockingCallback.getResultBlocking().isResult)

        val callback = BlockingSearchSelectionCallback()
        val options = SearchOptions(origin = secondCustomRecord.coordinate)
        searchEngine.search(secondCustomRecord.name, options, callback)

        val searchResult = callback.getResultBlocking()
        assertTrue(searchResult is SearchEngineResult.Suggestions)
        val suggestions = (searchResult as SearchEngineResult.Suggestions).suggestions

        assertTrue(
            suggestions.none {
                it is IndexableRecordSearchSuggestion && it.id == secondCustomRecord.id
            }
        )
    }

    @After
    override fun tearDown() {
        errorsReporter.reset()
        mockServer.shutdown()
        super.tearDown()
    }

    private companion object {

        val TEST_CUSTOM_USER_RECORDS = listOf(
            CustomRecord.create(
                name = "Let it be",
                coordinate = Point.fromLngLat(27.575321258282806, 53.89025545661358),
                provider = CustomRecord.Provider.CLOUD,
            ),
            CustomRecord.create(
                name = "Laŭka",
                coordinate = Point.fromLngLat(27.574862357961212, 53.88998973246244),
                provider = CustomRecord.Provider.CLOUD,
            ),
            CustomRecord.create(
                name = "Underdog",
                coordinate = Point.fromLngLat(27.57573285942709, 53.89020312748444),
                provider = CustomRecord.Provider.LOCAL,
            ),
        )

        const val TEST_ACCESS_TOKEN = "pk.test"
        val TEST_USER_LOCATION: Point = Point.fromLngLat(10.1, 11.1234567)

        const val TEST_LOCAL_TIME_MILLIS = 12345L
        const val TEST_UUID = "test-generated-uuid"
        val TEST_KEYBOARD_LOCALE: Locale = Locale.ENGLISH
        val TEST_ORIENTATION = ScreenOrientation.PORTRAIT

        val TEST_REQUEST_OPTIONS = RequestOptions(
            query = "",
            endpoint = "suggest",
            options = SearchOptions.Builder().build(),
            proximityRewritten = false,
            originRewritten = false,
            sessionID = TEST_UUID,
            requestContext = SearchRequestContext(
                apiType = ApiType.SBS,
                keyboardLocale = TEST_KEYBOARD_LOCALE,
                screenOrientation = TEST_ORIENTATION,
                responseUuid = ""
            )
        )
    }
}
