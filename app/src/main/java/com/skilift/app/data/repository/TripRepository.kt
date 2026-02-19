package com.skilift.app.data.repository

import com.skilift.app.domain.model.Itinerary
import com.skilift.app.domain.model.LatLng

interface TripRepository {
    suspend fun planTrip(
        origin: LatLng,
        destination: LatLng,
        bicycleReluctance: Double,
        bicycleBoardCost: Int,
        bicycleSpeed: Double
    ): Result<List<Itinerary>>
}
