package com.mapbox.search.metadata

import com.mapbox.search.BuildConfig
import com.mapbox.search.TestConstants.ASSERTIONS_KT_CLASS_NAME
import com.mapbox.search.common.reportError
import com.mapbox.search.tests_support.catchThrowable
import com.mapbox.test.dsl.TestCase
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestFactory

private typealias WeekTimestampParams = Triple<Byte, Int, Int>

internal class WeekTimestampTest {

    @TestFactory
    fun `Check validness of WeekTimestamp data`() = TestCase {
        Given("WeekTimestamp constructor") {
            WEEK_TIMESTAMP_VALIDNESS_CHECKS.forEach { (params, isValid) ->
                When("Creating WeekTimestamp$params") {
                    val failedOnAssertion = catchThrowable<Exception> {
                        params.toTimestamp()
                    } != null
                    Then(
                        "Should complete without errors: ${isValid || !BuildConfig.DEBUG}",
                        isValid || !BuildConfig.DEBUG,
                        !failedOnAssertion
                    )
                }
            }
        }
    }

    private fun WeekTimestampParams.toTimestamp(): WeekTimestamp {
        return WeekTimestamp(WeekDay.fromCore(first), second, third)
    }

    companion object {
        val WEEK_TIMESTAMP_VALIDNESS_CHECKS = mapOf(
            WeekTimestampParams(0, 9, 30) to true,
            WeekTimestampParams(0, 0, 0) to true,
            WeekTimestampParams(6, 24, 0) to true,
            WeekTimestampParams(5, 0, 0) to true,
            WeekTimestampParams(0, 25, 30) to false,
            WeekTimestampParams(2, 5, 60) to false,
        )

        @BeforeAll
        @JvmStatic
        fun setUpAll() {
            mockkStatic(ASSERTIONS_KT_CLASS_NAME)
            @Suppress("DEPRECATION")
            every { reportError(any()) } returns Unit
        }

        @AfterAll
        @JvmStatic
        fun tearDownAll() {
            unmockkStatic(ASSERTIONS_KT_CLASS_NAME)
        }
    }
}
