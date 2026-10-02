package com.example.plansync.data

import android.content.Context
import android.location.Location
import com.example.plansync.BuildConfig
import com.example.plansync.model.PlaceResult
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import kotlinx.coroutines.tasks.await

class PlacesRepository(context: Context) {

    private val client = run {
        if (!Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(context.applicationContext, BuildConfig.PLACES_API_KEY)
        }
        Places.createClient(context.applicationContext)
    }
    private var sessionToken = AutocompleteSessionToken.newInstance()

    suspend fun search(query: String, near: Location?): Result<List<PlaceResult>> = runCatching {
        val request = FindAutocompletePredictionsRequest.builder()
            .setQuery(query)
            .setSessionToken(sessionToken)
            .apply {
                near?.let { setLocationBias(CircularBounds.newInstance(LatLng(it.latitude, it.longitude), 5000.0)) }
            }
            .build()
        client.findAutocompletePredictions(request).await().autocompletePredictions.map {
            PlaceResult(
                id = it.placeId,
                name = it.getPrimaryText(null).toString(),
                address = it.getSecondaryText(null).toString()
            )
        }
    }

    suspend fun details(placeId: String): Result<PlaceResult> = runCatching {
        val fields = listOf(Place.Field.ID, Place.Field.DISPLAY_NAME, Place.Field.FORMATTED_ADDRESS, Place.Field.LOCATION)
        val request = FetchPlaceRequest.builder(placeId, fields).setSessionToken(sessionToken).build()
        val place = client.fetchPlace(request).await().place
        sessionToken = AutocompleteSessionToken.newInstance()
        PlaceResult(
            id = placeId,
            name = place.displayName.orEmpty(),
            address = place.formattedAddress.orEmpty(),
            lat = place.location?.latitude,
            lng = place.location?.longitude
        )
    }
}
