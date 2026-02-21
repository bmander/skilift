package com.skilift.app.ui.map

import com.skilift.app.domain.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapViewModelCompanionTest {

    // --- mapSliderToOtpPreferences ---

    @Test
    fun `mapSliderToOtpPreferences at 0 returns max reluctance and board cost`() {
        val (reluctance, boardCost) = MapViewModel.mapSliderToOtpPreferences(0.0f)
        assertEquals(5.0, reluctance, 0.001)
        assertEquals(1200, boardCost)
    }

    @Test
    fun `mapSliderToOtpPreferences at 1 returns min reluctance and board cost`() {
        val (reluctance, boardCost) = MapViewModel.mapSliderToOtpPreferences(1.0f)
        assertEquals(0.5, reluctance, 0.001)
        assertEquals(60, boardCost)
    }

    @Test
    fun `mapSliderToOtpPreferences at 0_5 returns midpoint values`() {
        val (reluctance, boardCost) = MapViewModel.mapSliderToOtpPreferences(0.5f)
        assertEquals(2.75, reluctance, 0.001)
        assertEquals(630, boardCost)
    }

    @Test
    fun `mapSliderToOtpPreferences reluctance decreases with balance`() {
        val (relLow, _) = MapViewModel.mapSliderToOtpPreferences(0.2f)
        val (relHigh, _) = MapViewModel.mapSliderToOtpPreferences(0.8f)
        assertTrue("Reluctance should decrease as balance increases", relLow > relHigh)
    }

    @Test
    fun `mapSliderToOtpPreferences board cost decreases with balance`() {
        val (_, costLow) = MapViewModel.mapSliderToOtpPreferences(0.2f)
        val (_, costHigh) = MapViewModel.mapSliderToOtpPreferences(0.8f)
        assertTrue("Board cost should decrease as balance increases", costLow > costHigh)
    }

    // --- isInCoverageArea ---

    @Test
    fun `isInCoverageArea Seattle downtown returns true`() {
        // Seattle downtown is well within coverage
        assertTrue(MapViewModel.isInCoverageArea(LatLng(47.6062, -122.3321)))
    }

    @Test
    fun `isInCoverageArea far away point returns false`() {
        // New York City
        assertFalse(MapViewModel.isInCoverageArea(LatLng(40.7128, -74.0060)))
    }

    @Test
    fun `isInCoverageArea center of coverage area returns true`() {
        val center = LatLng(MapViewModel.CENTER_LATITUDE, MapViewModel.CENTER_LONGITUDE)
        assertTrue(MapViewModel.isInCoverageArea(center))
    }

    // --- formatEpochMillis ---

    @Test
    fun `formatEpochMillis returns ISO offset date time`() {
        val result = MapViewModel.formatEpochMillis(1700000000000L)
        // Should contain date, time, and offset components
        assertTrue("Should contain T separator", result.contains("T"))
        // ISO offset format has + or - for timezone offset (or Z)
        assertTrue(
            "Should contain timezone offset",
            result.contains("+") || result.contains("-") || result.contains("Z")
        )
    }

    @Test
    fun `formatEpochMillis produces parseable output`() {
        val millis = 1700000000000L
        val result = MapViewModel.formatEpochMillis(millis)
        // Should not throw when parsed back
        java.time.OffsetDateTime.parse(result)
    }
}
