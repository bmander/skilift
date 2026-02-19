package com.skilift.app.domain.model

data class TripPreferences(
    val bikeTransitBalance: Float = 0.5f,
    val cyclingOptimization: CyclingOptimization = CyclingOptimization.SAFE_STREETS,
    val maxBikeSpeedMps: Float = 5.0f
)

enum class CyclingOptimization {
    QUICK,
    SAFE_STREETS,
    FLAT_STREETS
}
