package com.skilift.app.data.repository

import com.skilift.app.data.remote.OtpGraphQlClient
import com.skilift.app.data.remote.dto.LegDto
import com.skilift.app.data.remote.dto.PlaceDto
import com.skilift.app.domain.model.Itinerary
import com.skilift.app.domain.model.LatLng
import com.skilift.app.domain.model.Leg
import com.skilift.app.domain.model.Place
import com.skilift.app.domain.model.TransportMode
import com.skilift.app.util.PolylineDecoder
import javax.inject.Inject

class TripRepositoryImpl @Inject constructor(
    private val otpClient: OtpGraphQlClient
) : TripRepository {

    override suspend fun planTrip(
        origin: LatLng,
        destination: LatLng,
        bicycleReluctance: Double,
        bicycleBoardCost: Int,
        bicycleSpeed: Double
    ): Result<List<Itinerary>> = runCatching {
        val response = otpClient.planConnection(
            originLat = origin.latitude,
            originLon = origin.longitude,
            destLat = destination.latitude,
            destLon = destination.longitude,
            bicycleReluctance = bicycleReluctance,
            bicycleBoardCost = bicycleBoardCost,
            bicycleSpeed = bicycleSpeed
        )

        val errors = response.errors
        if (!errors.isNullOrEmpty()) {
            throw RuntimeException("OTP error: ${errors.first().message}")
        }

        val edges = response.data?.planConnection?.edges
            ?: throw RuntimeException("No trip data returned")

        edges.map { edge ->
            val node = edge.node
            Itinerary(
                durationSeconds = node.duration ?: 0,
                startTime = node.startTime ?: 0L,
                endTime = node.endTime ?: 0L,
                walkDistanceMeters = node.walkDistance ?: 0.0,
                legs = node.legs.map { it.toDomain() }
            )
        }
    }

    private fun LegDto.toDomain(): Leg = Leg(
        mode = TransportMode.fromOtpMode(mode),
        from = from.toDomain(),
        to = to.toDomain(),
        startTime = startTime ?: 0L,
        endTime = endTime ?: 0L,
        durationSeconds = duration ?: 0,
        distanceMeters = distance ?: 0.0,
        routeShortName = route?.shortName,
        routeLongName = route?.longName,
        headsign = headsign,
        geometry = legGeometry?.points?.let { PolylineDecoder.decode(it) } ?: emptyList()
    )

    private fun PlaceDto.toDomain(): Place = Place(
        name = name,
        location = LatLng(lat, lon),
        stopCode = stop?.code,
        platformCode = stop?.platformCode
    )
}
