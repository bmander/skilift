package com.skilift.app.domain.model

enum class TransportMode {
    BICYCLE,
    BUS,
    RAIL,
    TRAM,
    FERRY,
    WALK;

    companion object {
        fun fromOtpMode(mode: String): TransportMode = when (mode.uppercase()) {
            "BICYCLE" -> BICYCLE
            "BUS" -> BUS
            "RAIL" -> RAIL
            "TRAM" -> TRAM
            "FERRY" -> FERRY
            "WALK" -> WALK
            else -> WALK
        }
    }
}
