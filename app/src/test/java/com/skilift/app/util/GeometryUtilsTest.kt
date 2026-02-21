package com.skilift.app.util

import com.skilift.app.domain.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeometryUtilsTest {

    // --- interpolateOnGeometry ---

    @Test
    fun `interpolateOnGeometry with empty list returns null`() {
        assertNull(GeometryUtils.interpolateOnGeometry(emptyList(), 0.5))
    }

    @Test
    fun `interpolateOnGeometry with single point returns that point`() {
        val point = LatLng(47.6, -122.3)
        val result = GeometryUtils.interpolateOnGeometry(listOf(point), 0.5)
        assertEquals(point, result)
    }

    @Test
    fun `interpolateOnGeometry at fraction 0 returns first point`() {
        val points = listOf(LatLng(47.6, -122.3), LatLng(47.7, -122.4))
        val result = GeometryUtils.interpolateOnGeometry(points, 0.0)!!
        assertEquals(47.6, result.latitude, 0.0001)
        assertEquals(-122.3, result.longitude, 0.0001)
    }

    @Test
    fun `interpolateOnGeometry at fraction 1 returns last point`() {
        val points = listOf(LatLng(47.6, -122.3), LatLng(47.7, -122.4))
        val result = GeometryUtils.interpolateOnGeometry(points, 1.0)!!
        assertEquals(47.7, result.latitude, 0.0001)
        assertEquals(-122.4, result.longitude, 0.0001)
    }

    @Test
    fun `interpolateOnGeometry at midpoint of two-point line`() {
        val points = listOf(LatLng(47.0, -122.0), LatLng(48.0, -122.0))
        val result = GeometryUtils.interpolateOnGeometry(points, 0.5)!!
        assertEquals(47.5, result.latitude, 0.01)
        assertEquals(-122.0, result.longitude, 0.01)
    }

    @Test
    fun `interpolateOnGeometry clamps fraction below 0`() {
        val points = listOf(LatLng(47.6, -122.3), LatLng(47.7, -122.4))
        val result = GeometryUtils.interpolateOnGeometry(points, -0.5)!!
        assertEquals(47.6, result.latitude, 0.0001)
        assertEquals(-122.3, result.longitude, 0.0001)
    }

    @Test
    fun `interpolateOnGeometry clamps fraction above 1`() {
        val points = listOf(LatLng(47.6, -122.3), LatLng(47.7, -122.4))
        val result = GeometryUtils.interpolateOnGeometry(points, 1.5)!!
        assertEquals(47.7, result.latitude, 0.0001)
        assertEquals(-122.4, result.longitude, 0.0001)
    }

    @Test
    fun `interpolateOnGeometry on multi-segment path`() {
        // Three points forming two equal-length segments (roughly)
        val points = listOf(
            LatLng(47.0, -122.0),
            LatLng(47.1, -122.0),
            LatLng(47.2, -122.0)
        )
        // At 0.25, should be roughly at the midpoint of the first segment
        val result = GeometryUtils.interpolateOnGeometry(points, 0.25)!!
        assertEquals(47.05, result.latitude, 0.01)
    }

    // --- approxDistanceMeters ---

    @Test
    fun `approxDistanceMeters same point returns zero`() {
        val point = LatLng(47.6062, -122.3321)
        assertEquals(0.0, GeometryUtils.approxDistanceMeters(point, point), 0.001)
    }

    @Test
    fun `approxDistanceMeters known distance Seattle to Bellevue`() {
        val seattle = LatLng(47.6062, -122.3321)
        val bellevue = LatLng(47.6101, -122.2015)
        val distance = GeometryUtils.approxDistanceMeters(seattle, bellevue)
        // Roughly 10 km
        assertEquals(10_000.0, distance, 2000.0)
    }

    @Test
    fun `approxDistanceMeters is symmetric`() {
        val a = LatLng(47.6062, -122.3321)
        val b = LatLng(47.6101, -122.2015)
        val distAB = GeometryUtils.approxDistanceMeters(a, b)
        val distBA = GeometryUtils.approxDistanceMeters(b, a)
        assertEquals(distAB, distBA, 0.001)
    }

    @Test
    fun `approxDistanceMeters short distance is reasonable`() {
        // Two points about 100 meters apart
        val a = LatLng(47.6062, -122.3321)
        val b = LatLng(47.6071, -122.3321) // ~100m north
        val distance = GeometryUtils.approxDistanceMeters(a, b)
        assertEquals(100.0, distance, 20.0)
    }

    // --- pointToSegmentDistSq ---

    @Test
    fun `pointToSegmentDistSq point on segment returns zero`() {
        val distSq = GeometryUtils.pointToSegmentDistSq(
            px = 0.5, py = 0.0,
            x1 = 0.0, y1 = 0.0,
            x2 = 1.0, y2 = 0.0
        )
        assertEquals(0.0, distSq, 0.0001)
    }

    @Test
    fun `pointToSegmentDistSq point perpendicular to segment`() {
        val distSq = GeometryUtils.pointToSegmentDistSq(
            px = 0.5, py = 1.0,
            x1 = 0.0, y1 = 0.0,
            x2 = 1.0, y2 = 0.0
        )
        assertEquals(1.0, distSq, 0.0001) // distance = 1, distSq = 1
    }

    @Test
    fun `pointToSegmentDistSq point nearest to segment start`() {
        val distSq = GeometryUtils.pointToSegmentDistSq(
            px = -1.0, py = 0.0,
            x1 = 0.0, y1 = 0.0,
            x2 = 1.0, y2 = 0.0
        )
        assertEquals(1.0, distSq, 0.0001) // clamped to start, dist = 1
    }

    @Test
    fun `pointToSegmentDistSq point nearest to segment end`() {
        val distSq = GeometryUtils.pointToSegmentDistSq(
            px = 2.0, py = 0.0,
            x1 = 0.0, y1 = 0.0,
            x2 = 1.0, y2 = 0.0
        )
        assertEquals(1.0, distSq, 0.0001) // clamped to end, dist = 1
    }

    @Test
    fun `pointToSegmentDistSq degenerate segment (zero length)`() {
        val distSq = GeometryUtils.pointToSegmentDistSq(
            px = 3.0, py = 4.0,
            x1 = 0.0, y1 = 0.0,
            x2 = 0.0, y2 = 0.0
        )
        assertEquals(25.0, distSq, 0.0001) // 3^2 + 4^2 = 25
    }

    @Test
    fun `pointToSegmentDistSq diagonal segment`() {
        // Point at (1, 0), segment from (0, 0) to (0, 2)
        val distSq = GeometryUtils.pointToSegmentDistSq(
            px = 1.0, py = 1.0,
            x1 = 0.0, y1 = 0.0,
            x2 = 0.0, y2 = 2.0
        )
        assertEquals(1.0, distSq, 0.0001) // perpendicular distance = 1
    }
}
