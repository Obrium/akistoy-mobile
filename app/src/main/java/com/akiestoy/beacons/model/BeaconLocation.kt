package com.akiestoy.beacons.model

/**
 * Representa las ubicaciones físicas de los beacons ESP32
 * Mapea los valores Major a nombres descriptivos
 */
enum class BeaconLocation(val major: Int, val displayName: String) {
    BANO(100, "Baño"),
    SALA(101, "Sala"),
    UNKNOWN(-1, "Desconocido");

    companion object {
        fun fromMajor(major: Int): BeaconLocation {
            return entries.find { it.major == major } ?: UNKNOWN
        }
    }
}
