package com.skilift.app.util

import com.skilift.app.domain.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PolylineDecoderTest {

    @Test
    fun `decode empty string returns empty list`() {
        val result = PolylineDecoder.decode("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `decode known polyline returns correct coordinates`() {
        // Encoded polyline for: (38.5, -120.2), (40.7, -120.95), (43.252, -126.453)
        val encoded = "_p~iF~ps|U_ulLnnqC_mqNvxq`@"
        val result = PolylineDecoder.decode(encoded)

        assertEquals(3, result.size)
        assertLatLngEquals(LatLng(38.5, -120.2), result[0])
        assertLatLngEquals(LatLng(40.7, -120.95), result[1])
        assertLatLngEquals(LatLng(43.252, -126.453), result[2])
    }

    @Test
    fun `decode single point polyline`() {
        // A single-point polyline; verify it decodes to exactly one point
        val encoded = "_kniHnw`kV"
        val result = PolylineDecoder.decode(encoded)

        assertEquals(1, result.size)
        // Verify the decoded point is a valid coordinate
        assertTrue(result[0].latitude in -90.0..90.0)
        assertTrue(result[0].longitude in -180.0..180.0)
    }

    @Test
    fun `decode preserves point order`() {
        val encoded = "_p~iF~ps|U_ulLnnqC_mqNvxq`@"
        val result = PolylineDecoder.decode(encoded)

        // Points should go from south to north
        assertTrue(result[0].latitude < result[1].latitude)
        assertTrue(result[1].latitude < result[2].latitude)
    }

    @Test
    fun `decode handles negative deltas`() {
        // The second and third points in the known polyline require
        // negative longitude deltas
        val encoded = "_p~iF~ps|U_ulLnnqC_mqNvxq`@"
        val result = PolylineDecoder.decode(encoded)

        // All longitudes should be negative (western hemisphere)
        for (point in result) {
            assertTrue("Longitude should be negative", point.longitude < 0)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `decode truncated polyline throws IllegalArgumentException`() {
        // Full polyline: "_p~iF~ps|U_ulLnnqC_mqNvxq`@" decodes to 3 points.
        // Truncate mid-encoding so the last coordinate pair is incomplete.
        val truncated = "_p~iF~ps|U_ulLnnqC_mqN"
        PolylineDecoder.decode(truncated)
    }

    private fun assertLatLngEquals(expected: LatLng, actual: LatLng) {
        assertEquals(expected.latitude, actual.latitude, 0.00001)
        assertEquals(expected.longitude, actual.longitude, 0.00001)
    }
}
