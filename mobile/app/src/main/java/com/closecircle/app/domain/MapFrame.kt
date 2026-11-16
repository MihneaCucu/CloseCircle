package com.closecircle.app.domain

data class GeoPoint(val lat: Double, val lon: Double)

/** What the camera should show so that every member with a position is visible. */
sealed interface MapFrame {
    /** One point: centre on it at a street-level zoom. */
    data class Center(val point: GeoPoint, val zoom: Double) : MapFrame

    /** Several points: the smallest box around them, never tighter than [MIN_SPAN_DEG]. */
    data class Bounds(val south: Double, val west: Double, val north: Double, val east: Double) : MapFrame
}

private const val SINGLE_POINT_ZOOM = 15.0

/** About 330 m: two members standing next to each other should not zoom the map to the rooftops. */
const val MIN_SPAN_DEG = 0.003

fun frameOf(points: List<GeoPoint>): MapFrame? {
    if (points.isEmpty()) return null
    if (points.size == 1) return MapFrame.Center(points.single(), SINGLE_POINT_ZOOM)
    fun span(lo: Double, hi: Double): Pair<Double, Double> {
        if (hi - lo >= MIN_SPAN_DEG) return lo to hi
        val mid = (lo + hi) / 2
        return (mid - MIN_SPAN_DEG / 2) to (mid + MIN_SPAN_DEG / 2)
    }
    val (south, north) = span(points.minOf { it.lat }, points.maxOf { it.lat })
    val (west, east) = span(points.minOf { it.lon }, points.maxOf { it.lon })
    return MapFrame.Bounds(south, west, north, east)
}
