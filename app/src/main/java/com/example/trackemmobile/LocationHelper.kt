package com.veteranop.trackem.utils

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.LocationServices

object LocationHelper {
    private var cachedLocation: Location? = null

    @SuppressLint("MissingPermission")
    fun getLastLocation(context: Context): Location? {
        if (cachedLocation != null) {
            return cachedLocation
        }

        try {
            LocationServices.getFusedLocationProviderClient(context)
                .lastLocation
                .addOnSuccessListener { location: Location? ->
                    location?.let { cachedLocation = it }
                }
        } catch (e: SecurityException) {
            // Permission denied, just return null
        } catch (e: Exception) {
            // Any other error
        }

        return cachedLocation
    }

    fun updateLocation(location: Location) {
        cachedLocation = location
    }
}