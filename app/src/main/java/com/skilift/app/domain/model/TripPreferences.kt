package com.skilift.app.domain.model

data class TripPreferences(
    val bikeTransitBalance: Float = 0.5f,
    val triangleTimeFactor: Float = 0.3f,
    val triangleSafetyFactor: Float = 0.4f,
    val triangleFlatnessFactor: Float = 0.3f,
    val maxBikeSpeedMps: Float = 5.0f
)
