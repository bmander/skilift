package com.bmander.skilift

import android.content.Context
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.model.Place
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class AddressSuggestion(
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val placeId: String? = null
)

class AddressResolverService(context: Context) {

    private val placesClient: PlacesClient

    init {
        if (!Places.isInitialized()) {
            Places.initialize(context, GOOGLE_PLACES_API_KEY)
        }
        placesClient = Places.createClient(context)
    }

    /**
     * Suspends until autocomplete predictions are fetched.
     *
     * If the query is empty, returns an empty list.
     * Otherwise, queries the Places API and maps the predictions to a list
     * of AddressSuggestion objects (with placeholder lat/lng values).
     *
     * Throws an exception if the Places API call fails.
     */
    suspend fun getSuggestions(query: String): List<AddressSuggestion> =
        suspendCancellableCoroutine { cont ->
            if (query.isEmpty()) {
                cont.resume(emptyList())
                return@suspendCancellableCoroutine
            }
            val request = FindAutocompletePredictionsRequest.builder()
                .setQuery(query)
                .build()

            placesClient.findAutocompletePredictions(request)
                .addOnSuccessListener { response ->
                    val suggestions = response.autocompletePredictions.map { prediction ->
                        AddressSuggestion(
                            address = prediction.getFullText(null).toString(),
                            latitude = 0.0,  // Placeholder value.
                            longitude = 0.0, // Placeholder value.
                            placeId = prediction.placeId
                        )
                    }
                    cont.resume(suggestions)
                }
                .addOnFailureListener { exception ->
                    cont.resumeWithException(exception)
                }
        }

    /**
     * Suspends until full place details are fetched for the given [placeId].
     *
     * Returns an AddressSuggestion containing the address and the actual
     * latitude/longitude. Throws an exception if the fetch fails.
     */
    suspend fun fetchPlaceDetails(placeId: String): AddressSuggestion =
        suspendCancellableCoroutine { cont ->
            val placeFields = listOf(Place.Field.ADDRESS, Place.Field.LAT_LNG)
            val request = FetchPlaceRequest.builder(placeId, placeFields).build()

            placesClient.fetchPlace(request)
                .addOnSuccessListener { response ->
                    val place = response.place
                    val suggestion = AddressSuggestion(
                        address = place.address ?: "",
                        latitude = place.latLng?.latitude ?: 0.0,
                        longitude = place.latLng?.longitude ?: 0.0,
                        placeId = placeId
                    )
                    cont.resume(suggestion)
                }
                .addOnFailureListener { exception ->
                    cont.resumeWithException(exception)
                }
        }
}
