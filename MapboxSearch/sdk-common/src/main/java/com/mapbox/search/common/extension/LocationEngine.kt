package com.mapbox.search.common.extension

import android.annotation.SuppressLint
import android.content.Context
import com.mapbox.common.location.compat.LocationEngine
import com.mapbox.common.location.compat.LocationEngineCallback
import com.mapbox.common.location.compat.LocationEngineResult
import com.mapbox.android.core.permissions.PermissionsManager
import com.mapbox.geojson.Point
import com.mapbox.search.common.SearchCommonAsyncOperationTask
import com.mapbox.search.common.SearchCommonAsyncOperationTaskImpl

@SuppressLint("MissingPermission")
fun LocationEngine.lastKnownLocationOrNull(context: Context, callback: (Point?) -> Unit): SearchCommonAsyncOperationTask {
    if (!PermissionsManager.areLocationPermissionsGranted(context)) {
        callback(null)
        return SearchCommonAsyncOperationTaskImpl().apply {
            onComplete()
        }
    }

    val task = SearchCommonAsyncOperationTaskImpl()
    val locationCallback = object : LocationEngineCallback<LocationEngineResult> {
        override fun onSuccess(result: LocationEngineResult) {
            val location = (result.locations?.lastOrNull() ?: result.lastLocation)?.let { location ->
                Point.fromLngLat(location.longitude, location.latitude)
            }
            if (!task.isCancelled) {
                callback(location)
                task.onComplete()
            }
        }

        override fun onFailure(exception: Exception) {
            if (!task.isCancelled) {
                callback(null)
                task.onComplete()
            }
        }
    }
    task.onCancelCallback = {
        removeLocationUpdates(locationCallback)
    }
    getLastLocation(locationCallback)
    return task
}
