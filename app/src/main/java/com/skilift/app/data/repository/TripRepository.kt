package com.skilift.app.data.repository

import com.skilift.app.domain.model.Itinerary
import com.skilift.app.domain.model.LatLng

interface TripRepository {
    suspend fun planTrip(
        origin: LatLng,
        destination: LatLng,
        bicycleReluctance: Double,
        bicycleBoardCost: Int,
        bicycleSpeed: Double,
        triangleTimeFactor: Double = 0.3,
        triangleSafetyFactor: Double = 0.4,
        triangleFlatnessFactor: Double = 0.3,
        hillReluctance: Double = 1.0
    ): Result<List<Itinerary>>
}
