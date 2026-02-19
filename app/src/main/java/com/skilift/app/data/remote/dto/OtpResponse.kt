package com.skilift.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class OtpPlanConnectionResponse(
    val data: OtpData? = null,
    val errors: List<OtpError>? = null
)

@Serializable
data class OtpData(
    val planConnection: PlanConnectionDto
)

@Serializable
data class OtpError(
    val message: String
)

@Serializable
data class PlanConnectionDto(
    val edges: List<EdgeDto>
)

@Serializable
data class EdgeDto(
    val node: TripPatternDto
)

@Serializable
data class TripPatternDto(
    val duration: Int? = null,
    val startTime: Long? = null,
    val endTime: Long? = null,
    val walkDistance: Double? = null,
    val legs: List<LegDto>
)

@Serializable
data class LegDto(
    val mode: String,
    val startTime: Long? = null,
    val endTime: Long? = null,
    val duration: Double? = null,
    val distance: Double? = null,
    val from: PlaceDto,
    val to: PlaceDto,
    val route: RouteDto? = null,
    val headsign: String? = null,
    val legGeometry: LegGeometryDto? = null
)

@Serializable
data class PlaceDto(
    val name: String? = null,
    val lat: Double,
    val lon: Double,
    val stop: StopDto? = null
)

@Serializable
data class StopDto(
    val code: String? = null,
    val platformCode: String? = null
)

@Serializable
data class RouteDto(
    val shortName: String? = null,
    val longName: String? = null
)

@Serializable
data class LegGeometryDto(
    val points: String? = null
)
