package com.skilift.app.data.repository

import com.skilift.app.data.remote.OtpGraphQlClient
import com.skilift.app.data.remote.dto.*
import com.skilift.app.domain.model.LatLng
import com.skilift.app.domain.model.TransportMode
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TripRepositoryImplTest {

    private lateinit var otpClient: OtpGraphQlClient
    private lateinit var repository: TripRepositoryImpl

    @Before
    fun setUp() {
        otpClient = mockk()
        repository = TripRepositoryImpl(otpClient)
    }

    @Test
    fun `planTrip returns itineraries on success`() = runTest {
        val response = OtpPlanConnectionResponse(
            data = OtpData(
                planConnection = PlanConnectionDto(
                    edges = listOf(
                        EdgeDto(
                            node = TripPatternDto(
                                duration = 1800,
                                startTime = 1700000000000L,
                                endTime = 1700001800000L,
                                walkDistance = 500.0,
                                legs = listOf(
                                    LegDto(
                                        mode = "BICYCLE",
                                        startTime = 1700000000000L,
                                        endTime = 1700000600000L,
                                        duration = 600.0,
                                        distance = 2000.0,
                                        from = PlaceDto(name = "Origin", lat = 47.6, lon = -122.3),
                                        to = PlaceDto(name = "Stop A", lat = 47.61, lon = -122.31)
                                    ),
                                    LegDto(
                                        mode = "BUS",
                                        startTime = 1700000600000L,
                                        endTime = 1700001800000L,
                                        duration = 1200.0,
                                        distance = 5000.0,
                                        from = PlaceDto(name = "Stop A", lat = 47.61, lon = -122.31),
                                        to = PlaceDto(
                                            name = "Stop B",
                                            lat = 47.65,
                                            lon = -122.35,
                                            stop = StopDto(code = "1234", platformCode = "B")
                                        ),
                                        route = RouteDto(shortName = "40", longName = "Ballard"),
                                        headsign = "Downtown Seattle"
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        coEvery { otpClient.planConnection(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns response

        val result = repository.planTrip(
            origin = LatLng(47.6, -122.3),
            destination = LatLng(47.65, -122.35),
            bicycleReluctance = 2.0,
            bicycleBoardCost = 600,
            bicycleSpeed = 5.0
        )

        assertTrue(result.isSuccess)
        val itineraries = result.getOrThrow()
        assertEquals(1, itineraries.size)

        val itinerary = itineraries[0]
        assertEquals(1800, itinerary.durationSeconds)
        assertEquals(1700000000000L, itinerary.startTime)
        assertEquals(1700001800000L, itinerary.endTime)
        assertEquals(500.0, itinerary.walkDistanceMeters, 0.001)
        assertEquals(2, itinerary.legs.size)

        // Verify first leg (bicycle)
        val bikeLeg = itinerary.legs[0]
        assertEquals(TransportMode.BICYCLE, bikeLeg.mode)
        assertEquals("Origin", bikeLeg.from.name)
        assertEquals(600, bikeLeg.durationSeconds)
        assertEquals(2000.0, bikeLeg.distanceMeters, 0.001)

        // Verify second leg (bus)
        val busLeg = itinerary.legs[1]
        assertEquals(TransportMode.BUS, busLeg.mode)
        assertEquals("40", busLeg.routeShortName)
        assertEquals("Ballard", busLeg.routeLongName)
        assertEquals("Downtown Seattle", busLeg.headsign)
        assertEquals("1234", busLeg.to.stopCode)
        assertEquals("B", busLeg.to.platformCode)
    }

    @Test
    fun `planTrip returns failure on OTP error`() = runTest {
        val response = OtpPlanConnectionResponse(
            errors = listOf(OtpError("No trip found"))
        )

        coEvery { otpClient.planConnection(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns response

        val result = repository.planTrip(
            origin = LatLng(47.6, -122.3),
            destination = LatLng(47.65, -122.35),
            bicycleReluctance = 2.0,
            bicycleBoardCost = 600,
            bicycleSpeed = 5.0
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("No trip found"))
    }

    @Test
    fun `planTrip returns failure when no data returned`() = runTest {
        val response = OtpPlanConnectionResponse(data = null)

        coEvery { otpClient.planConnection(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns response

        val result = repository.planTrip(
            origin = LatLng(47.6, -122.3),
            destination = LatLng(47.65, -122.35),
            bicycleReluctance = 2.0,
            bicycleBoardCost = 600,
            bicycleSpeed = 5.0
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun `planTrip returns failure when client throws`() = runTest {
        coEvery { otpClient.planConnection(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } throws RuntimeException("Network error")

        val result = repository.planTrip(
            origin = LatLng(47.6, -122.3),
            destination = LatLng(47.65, -122.35),
            bicycleReluctance = 2.0,
            bicycleBoardCost = 600,
            bicycleSpeed = 5.0
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("Network error"))
    }

    @Test
    fun `planTrip handles null optional fields gracefully`() = runTest {
        val response = OtpPlanConnectionResponse(
            data = OtpData(
                planConnection = PlanConnectionDto(
                    edges = listOf(
                        EdgeDto(
                            node = TripPatternDto(
                                duration = null,
                                startTime = null,
                                endTime = null,
                                walkDistance = null,
                                legs = listOf(
                                    LegDto(
                                        mode = "WALK",
                                        startTime = null,
                                        endTime = null,
                                        duration = null,
                                        distance = null,
                                        from = PlaceDto(name = null, lat = 47.6, lon = -122.3),
                                        to = PlaceDto(name = null, lat = 47.61, lon = -122.31),
                                        route = null,
                                        headsign = null,
                                        legGeometry = null,
                                        steps = null
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        coEvery { otpClient.planConnection(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns response

        val result = repository.planTrip(
            origin = LatLng(47.6, -122.3),
            destination = LatLng(47.65, -122.35),
            bicycleReluctance = 2.0,
            bicycleBoardCost = 600,
            bicycleSpeed = 5.0
        )

        assertTrue(result.isSuccess)
        val itinerary = result.getOrThrow()[0]
        assertEquals(0, itinerary.durationSeconds)
        assertEquals(0L, itinerary.startTime)
        assertEquals(0.0, itinerary.walkDistanceMeters, 0.001)

        val leg = itinerary.legs[0]
        assertEquals(TransportMode.WALK, leg.mode)
        assertEquals(0, leg.durationSeconds)
        assertEquals(0.0, leg.distanceMeters, 0.001)
        assertTrue(leg.geometry.isEmpty())
        assertTrue(leg.elevationProfile.isEmpty())
    }

    @Test
    fun `planTrip decodes leg geometry`() = runTest {
        val response = OtpPlanConnectionResponse(
            data = OtpData(
                planConnection = PlanConnectionDto(
                    edges = listOf(
                        EdgeDto(
                            node = TripPatternDto(
                                legs = listOf(
                                    LegDto(
                                        mode = "BICYCLE",
                                        from = PlaceDto(lat = 38.5, lon = -120.2),
                                        to = PlaceDto(lat = 43.252, lon = -126.453),
                                        legGeometry = LegGeometryDto(
                                            points = "_p~iF~ps|U_ulLnnqC_mqNvxq`@"
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        coEvery { otpClient.planConnection(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns response

        val result = repository.planTrip(
            origin = LatLng(38.5, -120.2),
            destination = LatLng(43.252, -126.453),
            bicycleReluctance = 2.0,
            bicycleBoardCost = 600,
            bicycleSpeed = 5.0
        )

        val geometry = result.getOrThrow()[0].legs[0].geometry
        assertEquals(3, geometry.size)
        assertEquals(38.5, geometry[0].latitude, 0.001)
        assertEquals(-120.2, geometry[0].longitude, 0.001)
    }

    @Test
    fun `planTrip builds elevation profile from steps`() = runTest {
        val response = OtpPlanConnectionResponse(
            data = OtpData(
                planConnection = PlanConnectionDto(
                    edges = listOf(
                        EdgeDto(
                            node = TripPatternDto(
                                legs = listOf(
                                    LegDto(
                                        mode = "BICYCLE",
                                        from = PlaceDto(lat = 47.6, lon = -122.3),
                                        to = PlaceDto(lat = 47.61, lon = -122.31),
                                        steps = listOf(
                                            StepDto(
                                                distance = 100.0,
                                                elevationProfile = listOf(
                                                    ElevationProfileComponentDto(distance = 0.0, elevation = 50.0),
                                                    ElevationProfileComponentDto(distance = 50.0, elevation = 55.0),
                                                    ElevationProfileComponentDto(distance = 100.0, elevation = 60.0)
                                                )
                                            ),
                                            StepDto(
                                                distance = 80.0,
                                                elevationProfile = listOf(
                                                    ElevationProfileComponentDto(distance = 0.0, elevation = 60.0),
                                                    ElevationProfileComponentDto(distance = 80.0, elevation = 65.0)
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        coEvery { otpClient.planConnection(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns response

        val result = repository.planTrip(
            origin = LatLng(47.6, -122.3),
            destination = LatLng(47.61, -122.31),
            bicycleReluctance = 2.0,
            bicycleBoardCost = 600,
            bicycleSpeed = 5.0
        )

        val profile = result.getOrThrow()[0].legs[0].elevationProfile
        assertTrue(profile.isNotEmpty())
        // First point should be at distance 0
        assertEquals(0.0, profile[0].distanceMeters, 0.001)
        assertEquals(50.0, profile[0].elevationMeters, 0.001)
        // Distances should be monotonically increasing
        for (i in 1 until profile.size) {
            assertTrue(
                "Distances should increase: ${profile[i - 1].distanceMeters} < ${profile[i].distanceMeters}",
                profile[i].distanceMeters > profile[i - 1].distanceMeters
            )
        }
    }
}
