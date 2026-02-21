package com.skilift.app.util

import com.skilift.app.domain.model.LatLng
import kotlin.math.cos
import kotlin.math.sqrt

private const val EARTH_RADIUS_METERS = 6_371_000.0

object GeometryUtils {

    /**
     * Interpolate a position along a geometry polyline at the given [fraction] (0.0–1.0).
     */
    fun interpolateOnGeometry(geometry: List<LatLng>, fraction: Double): LatLng? {
        if (geometry.isEmpty()) return null
        if (geometry.size == 1) return geometry[0]

        val clamped = fraction.coerceIn(0.0, 1.0)

        // Compute cumulative segment distances along the geometry
        val segDistances = DoubleArray(geometry.size - 1)
        var geomTotal = 0.0
        for (i in 1 until geometry.size) {
            val d = approxDistanceMeters(geometry[i - 1], geometry[i])
            segDistances[i - 1] = d
            geomTotal += d
        }

        val targetDist = clamped * geomTotal

        var cumulative = 0.0
        for (i in segDistances.indices) {
            val segLen = segDistances[i]
            if (cumulative + segLen >= targetDist) {
                val remaining = targetDist - cumulative
                val t = if (segLen > 0) remaining / segLen else 0.0
                return LatLng(
                    latitude = geometry[i].latitude + t * (geometry[i + 1].latitude - geometry[i].latitude),
                    longitude = geometry[i].longitude + t * (geometry[i + 1].longitude - geometry[i].longitude)
                )
            }
            cumulative += segLen
        }
        return geometry.last()
    }

    fun approxDistanceMeters(a: LatLng, b: LatLng): Double {
        val latMid = Math.toRadians((a.latitude + b.latitude) / 2.0)
        val dx = Math.toRadians(b.longitude - a.longitude) * cos(latMid) * EARTH_RADIUS_METERS
        val dy = Math.toRadians(b.latitude - a.latitude) * EARTH_RADIUS_METERS
        return sqrt(dx * dx + dy * dy)
    }
}
