package com.skilift.app.ui.common.format

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {

    // --- formatDurationMinutes ---

    @Test
    fun `formatDurationMinutes zero seconds`() {
        assertEquals("0 min", formatDurationMinutes(0))
    }

    @Test
    fun `formatDurationMinutes under one minute`() {
        assertEquals("0 min", formatDurationMinutes(30))
    }

    @Test
    fun `formatDurationMinutes exactly one minute`() {
        assertEquals("1 min", formatDurationMinutes(60))
    }

    @Test
    fun `formatDurationMinutes several minutes`() {
        assertEquals("25 min", formatDurationMinutes(1500))
    }

    @Test
    fun `formatDurationMinutes exactly one hour`() {
        assertEquals("1 hr 0 min", formatDurationMinutes(3600))
    }

    @Test
    fun `formatDurationMinutes hours and minutes`() {
        assertEquals("2 hr 30 min", formatDurationMinutes(9000))
    }

    @Test
    fun `formatDurationMinutes just under one hour`() {
        assertEquals("59 min", formatDurationMinutes(3540))
    }

    // --- formatDistanceKm ---

    @Test
    fun `formatDistanceKm zero meters`() {
        assertEquals("0.0 km", formatDistanceKm(0.0))
    }

    @Test
    fun `formatDistanceKm one kilometer`() {
        assertEquals("1.0 km", formatDistanceKm(1000.0))
    }

    @Test
    fun `formatDistanceKm fractional kilometers`() {
        assertEquals("1.5 km", formatDistanceKm(1500.0))
    }

    @Test
    fun `formatDistanceKm rounds to one decimal`() {
        assertEquals("2.3 km", formatDistanceKm(2345.0))
    }

    @Test
    fun `formatDistanceKm small distance`() {
        assertEquals("0.1 km", formatDistanceKm(100.0))
    }

    @Test
    fun `formatDistanceKm large distance`() {
        assertEquals("15.0 km", formatDistanceKm(15000.0))
    }

    // --- formatTime ---

    @Test
    fun `formatTime formats epoch millis to time string`() {
        // Just verify it produces a non-empty string with expected format pattern
        val result = formatTime(1700000000000L) // Nov 14, 2023 ~9:13 AM UTC
        // The exact output depends on the locale/timezone, but it should contain AM or PM
        assert(result.contains("AM") || result.contains("PM")) {
            "Expected AM/PM format, got: $result"
        }
    }

    @Test
    fun `formatTime includes colon separator`() {
        val result = formatTime(1700000000000L)
        assert(result.contains(":")) {
            "Expected colon in time format, got: $result"
        }
    }
}
