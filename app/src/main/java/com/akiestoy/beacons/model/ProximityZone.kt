package com.akiestoy.beacons.model

/**
 * Zonas de proximidad basadas en la distancia calculada al beacon
 */
enum class ProximityZone(val description: String, val emoji: String) {
    IMMEDIATE("Muy cerca", "🔴"),
    NEAR("Cerca", "🟡"),
    FAR("Lejos", "🟢"),
    UNKNOWN("Fuera de rango", "⚪");

    companion object {
        fun fromDistance(distance: Double): ProximityZone {
            return when {
                distance < 0 -> UNKNOWN
                distance < 0.5 -> IMMEDIATE  // < 0.5m
                distance < 3.0 -> NEAR       // 0.5m - 3m
                distance < 10.0 -> FAR       // 3m - 10m
                else -> UNKNOWN              // > 10m
            }
        }
    }
}
