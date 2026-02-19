package com.skilift.app.domain.model

data class Place(
    val name: String?,
    val location: LatLng,
    val stopCode: String? = null,
    val platformCode: String? = null
)
