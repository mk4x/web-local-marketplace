package com.g7.database.types

import org.locationtech.jts.geom.Geometry

data class Geography(
    val geometry: Geometry,
    val srid: Int = 4326,
) {
    init {
        require(srid > 0) { "SRID must be positive" }
        require(geometry.srid == 0 || geometry.srid == srid) {
            "Geometry SRID (${geometry.srid}) does not match Geography SRID ($srid)"
        }
    }
}
