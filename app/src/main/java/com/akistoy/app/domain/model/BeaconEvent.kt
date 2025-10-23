package com.akistoy.app.domain.model

import kotlinx.datetime.Instant

data class BeaconEvent(
    val beaconId: String,
    val namespace: String?,
    val rssi: Int,
    val timestamp: Instant,
    val proximity: BeaconProximity = BeaconProximity.Unknown,
    val eventType: BeaconEventType = BeaconEventType.DETECTION
)

enum class BeaconProximity {
    Immediate, Near, Far, Unknown
}

enum class BeaconEventType {
    ENTRY,      // Primera vez detectado o re-entrada después de salida
    EXIT,       // Beacon perdió señal (timeout)
    DETECTION   // Detección continua (usado internamente para dedup)
}
