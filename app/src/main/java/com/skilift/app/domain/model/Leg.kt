package com.skilift.app.domain.model

data class Leg(
    val mode: TransportMode,
    val from: Place,
    val to: Place,
    val startTime: Long,
    val endTime: Long,
    val durationSeconds: Int,
    val distanceMeters: Double,
    val routeShortName: String?,
    val routeLongName: String?,
    val headsign: String?,
    val geometry: List<LatLng>
)
