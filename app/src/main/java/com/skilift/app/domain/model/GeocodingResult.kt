package com.skilift.app.domain.model

data class GeocodingResult(
    val name: String,
    val address: String?,
    val location: LatLng
)
