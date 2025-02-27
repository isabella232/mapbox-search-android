package com.mapbox.search.sample.tools.matchers

import com.mapbox.search.sample.R

object SearchSdkMatchers {

    fun isEmptyRecentSearchItem() = SingleViewIdMatcher(
        viewId = R.id.history_not_items,
        matcherDescription = "Matcher for empty recent searches holder"
    )

    fun isEmptySearchResultsItem() = SingleViewIdMatcher(
        viewId = R.id.result_not_items,
        matcherDescription = "Matcher for empty search results holder"
    )

    fun isCategorySearchResultItem() = SingleViewIdMatcher(
        viewId = R.id.category_result_container_view,
        matcherDescription = "Matcher for category search result holder"
    )

    fun isSearchProgressItem() = SingleViewIdMatcher(
        viewId = R.id.search_progress_container,
        matcherDescription = "Matcher for search loading holder"
    )

    fun isUiErrorView(errorTitle: String, errorSubtitle: String) = MapboxSdkUiErrorViewMatcher(
        errorTitle = errorTitle,
        errorSubtitle = errorSubtitle
    )

    fun isSubmitMissingResultFeedbackView() = SingleViewIdMatcher(
        viewId = R.id.submit_missing_result_feedback,
        matcherDescription = "Matcher for `Missing result` item view holder"
    )
}
