package com.mapbox.search.common

import android.app.PendingIntent
import android.location.Location
import android.os.Handler
import android.os.Looper
import com.mapbox.common.location.compat.LocationEngine
import com.mapbox.common.location.compat.LocationEngineCallback
import com.mapbox.common.location.compat.LocationEngineRequest
import com.mapbox.common.location.compat.LocationEngineResult
import com.mapbox.geojson.Point

class FixedPointLocationEngine(val location: Location) : LocationEngine {

    constructor(point: Point) : this(point.toLocation())

    override fun getLastLocation(callback: LocationEngineCallback<LocationEngineResult>) {
        callback.onSuccess(LocationEngineResult.create(location))
    }

    override fun requestLocationUpdates(
        request: LocationEngineRequest,
        callback: LocationEngineCallback<LocationEngineResult>,
        looper: Looper?
    ) {
        val callbackRunnable = Runnable {
            callback.onSuccess(LocationEngineResult.create(location))
        }

        if (looper != null) {
            Handler(looper).post(callbackRunnable)
        } else {
            callbackRunnable.run()
        }
    }

    override fun requestLocationUpdates(request: LocationEngineRequest, pendingIntent: PendingIntent?) {
        throw NotImplementedError()
    }

    override fun removeLocationUpdates(callback: LocationEngineCallback<LocationEngineResult>) {
        // Do nothing
    }

    override fun removeLocationUpdates(pendingIntent: PendingIntent?) {
        // Do nothing
    }

    private companion object {
        fun Point.toLocation(): Location {
            val location = Location("")
            location.latitude = latitude()
            location.longitude = longitude()
            return location
        }
    }
}
