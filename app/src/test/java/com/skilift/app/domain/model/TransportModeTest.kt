package com.skilift.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TransportModeTest {

    @Test
    fun `fromOtpMode BICYCLE returns BICYCLE`() {
        assertEquals(TransportMode.BICYCLE, TransportMode.fromOtpMode("BICYCLE"))
    }

    @Test
    fun `fromOtpMode BUS returns BUS`() {
        assertEquals(TransportMode.BUS, TransportMode.fromOtpMode("BUS"))
    }

    @Test
    fun `fromOtpMode RAIL returns RAIL`() {
        assertEquals(TransportMode.RAIL, TransportMode.fromOtpMode("RAIL"))
    }

    @Test
    fun `fromOtpMode TRAM returns TRAM`() {
        assertEquals(TransportMode.TRAM, TransportMode.fromOtpMode("TRAM"))
    }

    @Test
    fun `fromOtpMode FERRY returns FERRY`() {
        assertEquals(TransportMode.FERRY, TransportMode.fromOtpMode("FERRY"))
    }

    @Test
    fun `fromOtpMode WALK returns WALK`() {
        assertEquals(TransportMode.WALK, TransportMode.fromOtpMode("WALK"))
    }

    @Test
    fun `fromOtpMode is case insensitive`() {
        assertEquals(TransportMode.BICYCLE, TransportMode.fromOtpMode("bicycle"))
        assertEquals(TransportMode.BUS, TransportMode.fromOtpMode("bus"))
        assertEquals(TransportMode.RAIL, TransportMode.fromOtpMode("Rail"))
        assertEquals(TransportMode.TRAM, TransportMode.fromOtpMode("tram"))
        assertEquals(TransportMode.FERRY, TransportMode.fromOtpMode("Ferry"))
        assertEquals(TransportMode.WALK, TransportMode.fromOtpMode("walk"))
    }

    @Test
    fun `fromOtpMode unknown mode defaults to WALK`() {
        assertEquals(TransportMode.WALK, TransportMode.fromOtpMode("SUBWAY"))
        assertEquals(TransportMode.WALK, TransportMode.fromOtpMode("CAR"))
        assertEquals(TransportMode.WALK, TransportMode.fromOtpMode(""))
        assertEquals(TransportMode.WALK, TransportMode.fromOtpMode("UNKNOWN"))
    }
}
