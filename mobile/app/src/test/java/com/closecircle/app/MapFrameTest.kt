package com.closecircle.app

import com.closecircle.app.domain.GeoPoint
import com.closecircle.app.domain.MIN_SPAN_DEG
import com.closecircle.app.domain.MapFrame
import com.closecircle.app.domain.frameOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapFrameTest {
    @Test
    fun nobodyOnTheMapMeansNoFrame() {
        assertNull(frameOf(emptyList()))
    }

    @Test
    fun aSinglePointIsCenteredAtStreetLevel() {
        val frame = frameOf(listOf(GeoPoint(44.4268, 26.1025))) as MapFrame.Center
        assertEquals(44.4268, frame.point.lat, 0.0)
        assertEquals(26.1025, frame.point.lon, 0.0)
        assertEquals(15.0, frame.zoom, 0.0)
    }

    @Test
    fun farApartPointsGetTheirExactBox() {
        val frame = frameOf(listOf(GeoPoint(44.40, 26.00), GeoPoint(44.50, 26.20), GeoPoint(44.45, 26.10))) as MapFrame.Bounds
        assertEquals(44.40, frame.south, 1e-9)
        assertEquals(44.50, frame.north, 1e-9)
        assertEquals(26.00, frame.west, 1e-9)
        assertEquals(26.20, frame.east, 1e-9)
    }

    @Test
    fun pointsThatAreTooCloseAreNotZoomedPastTheMinimumSpan() {
        val frame = frameOf(listOf(GeoPoint(44.4268, 26.1025), GeoPoint(44.4269, 26.1026))) as MapFrame.Bounds
        assertEquals(MIN_SPAN_DEG, frame.north - frame.south, 1e-9)
        assertEquals(MIN_SPAN_DEG, frame.east - frame.west, 1e-9)
        // still centred on the two points
        assertEquals((44.4268 + 44.4269) / 2, (frame.north + frame.south) / 2, 1e-9)
    }

    @Test
    fun onlyTheTightAxisIsWidened() {
        // same longitude, 0.1 degree apart in latitude
        val frame = frameOf(listOf(GeoPoint(44.40, 26.10), GeoPoint(44.50, 26.10))) as MapFrame.Bounds
        assertEquals(0.10, frame.north - frame.south, 1e-9)
        assertTrue(frame.east - frame.west >= MIN_SPAN_DEG - 1e-12)
    }
}
