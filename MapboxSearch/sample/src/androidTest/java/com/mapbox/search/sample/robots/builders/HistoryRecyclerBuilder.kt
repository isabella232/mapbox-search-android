package com.mapbox.search.sample.robots.builders

import androidx.annotation.IdRes
import androidx.test.espresso.assertion.ViewAssertions
import com.mapbox.search.sample.robots.RobotDsl
import com.mapbox.search.sample.tools.matchers.HistoryItemMatcher
import com.mapbox.search.sample.tools.matchers.RecentSearchesMatcher
import com.mapbox.search.sample.tools.matchers.SearchSdkMatchers.isEmptyRecentSearchItem
import com.schibsted.spain.barista.assertion.BaristaListAssertions

@RobotDsl
class HistoryRecyclerBuilder(@IdRes val recyclerId: Int) {

    var itemPosition: Int = 0
        private set

    fun recentSearchesTitle() {
        BaristaListAssertions.assertCustomAssertionAtPosition(
            recyclerId,
            itemPosition++,
            viewAssertion = ViewAssertions.matches(RecentSearchesMatcher())
        )
    }

    fun noRecentSearches() {
        BaristaListAssertions.assertCustomAssertionAtPosition(
            recyclerId,
            itemPosition++,
            viewAssertion = ViewAssertions.matches(isEmptyRecentSearchItem())
        )
    }

    fun historyResult(historyName: String) {
        BaristaListAssertions.assertCustomAssertionAtPosition(
            recyclerId,
            itemPosition++,
            viewAssertion = ViewAssertions.matches(HistoryItemMatcher(historyName))
        )
    }
}
