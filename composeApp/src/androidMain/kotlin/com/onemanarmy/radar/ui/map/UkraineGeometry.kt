package com.onemanarmy.radar.ui.map

import org.osmdroid.util.GeoPoint

/**
 * Simplified vector outline of Ukraine (mainland + Crimea) for instant map context
 * even before tiles load or when completely offline.
 */
object UkraineGeometry {

    val borderPolygon: List<GeoPoint> = listOf(
        GeoPoint(51.50, 23.62), // NW Volyn / Belarus / Poland tripoint
        GeoPoint(51.90, 24.55),
        GeoPoint(51.85, 26.20),
        GeoPoint(51.60, 28.05),
        GeoPoint(51.45, 29.55),
        GeoPoint(51.38, 30.65),
        GeoPoint(52.10, 31.78), // Chernihiv north
        GeoPoint(52.36, 33.20),
        GeoPoint(51.80, 34.15),
        GeoPoint(51.18, 35.12), // Sumy east
        GeoPoint(50.45, 36.02),
        GeoPoint(50.30, 37.35), // Kharkiv north-east
        GeoPoint(49.85, 38.45),
        GeoPoint(49.55, 40.05), // Luhansk east
        GeoPoint(48.62, 39.95),
        GeoPoint(47.92, 39.65),
        GeoPoint(47.25, 38.22), // Sea of Azov coast
        GeoPoint(46.75, 36.80),
        GeoPoint(46.30, 35.35),
        GeoPoint(45.35, 36.45), // Kerch peninsula
        GeoPoint(44.40, 33.75), // Crimea south
        GeoPoint(45.35, 32.50), // Tarkhankut
        GeoPoint(46.10, 33.65),
        GeoPoint(46.15, 32.20), // Kherson coast
        GeoPoint(46.55, 31.30),
        GeoPoint(46.40, 30.72), // Odesa
        GeoPoint(45.85, 30.15),
        GeoPoint(45.35, 29.60), // Danube delta
        GeoPoint(45.45, 28.25),
        GeoPoint(46.30, 29.00),
        GeoPoint(47.10, 29.85),
        GeoPoint(48.10, 28.30),
        GeoPoint(48.42, 26.62), // Chernivtsi / Moldova / Romania
        GeoPoint(47.90, 25.00),
        GeoPoint(47.95, 23.60), // Zakarpattia
        GeoPoint(48.42, 22.15),
        GeoPoint(49.08, 22.56), // Poland border
        GeoPoint(49.65, 22.85),
        GeoPoint(50.45, 23.95),
        GeoPoint(51.50, 23.62)
    )
}
