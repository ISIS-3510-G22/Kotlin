package com.example.plansync.service

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocationService(private val context: Context) {

    @SuppressLint("MissingPermission")
    suspend fun currentLocation(): Location? = withContext(Dispatchers.IO) {
        val granted = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        if (!granted) return@withContext null

        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        runCatching {
            manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        }.getOrNull()
    }

    @Suppress("DEPRECATION")
    suspend fun coordinatesFor(address: String): Location? = withContext(Dispatchers.IO) {
        runCatching { Geocoder(context).getFromLocationName(address, 1)?.firstOrNull() }
            .getOrNull()
            ?.let { Location("").apply { latitude = it.latitude; longitude = it.longitude } }
    }

    fun distanceKm(from: Location, lat: Double, lng: Double): Float {
        val result = FloatArray(1)
        Location.distanceBetween(from.latitude, from.longitude, lat, lng, result)
        return result[0] / 1000f
    }
}
