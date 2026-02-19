package com.skilift.app.domain.model

data class Itinerary(
    val durationSeconds: Int,
    val startTime: Long,
    val endTime: Long,
    val walkDistanceMeters: Double,
    val legs: List<Leg>
)
