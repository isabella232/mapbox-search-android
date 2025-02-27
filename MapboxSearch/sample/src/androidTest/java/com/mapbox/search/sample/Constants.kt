package com.mapbox.search.sample

import com.mapbox.geojson.Point

internal object Constants {
    const val SINGLE_ANDROID_ANIMATION_DELAY_MILLIS = 400L
    const val DOUBLE_ANDROID_ANIMATION_DELAY_MILLIS = 700L
    const val TEXT_INPUT_DELAY_MILLIS = 3000L

    const val CHANGE_CARDS_NAVIGATION_ANIMATION_TIME_MILLIS = 2000L
    const val MAX_NETWORK_REQUEST_TIMEOUT_MILLIS = 10_000L

    val TEST_USER_LOCATION: Point = Point.fromLngLat(-122.084000, 37.421998)

    object Assets {
        const val MINSK_SUGGESTIONS_ASSET: String = "suggestions-successful-minsk.json"
        const val MINSK_REGION_SUGGESTIONS_ASSET: String = "suggestions-successful-minsk-region.json"
        const val EMPTY_SUGGESTIONS_ASSET: String = "suggestions-successful-empty-results.json"
        const val RANELAGH_SUGGESTIONS_ASSET: String = "suggestions-successful-ranelagh.json"
        const val CLEMENS_SUGGESTIONS_ASSET: String = "suggestions-successful-clemens.json"
        const val CAFE_SUGGESTIONS_ASSET: String = "suggestions-successful-cafe.json"

        const val RANELAGH_ROYAL_SPA_RESULT_ASSET: String = "retrieve-successful-ranelagh-royal-spa.json"
        const val CLEMENS_MOUNT_CLEMENS_RESULT_ASSET: String = "retrieve-successful-clemens-mount-clemens.json"

        const val CATEGORY_CAFE_RESULTS_ASSET: String = "category-cafe-successful-results.json"
    }
}
