package com.akistoy.app.domain.model

import kotlinx.datetime.Instant

data class BeaconEvent(
    val beaconId: String,
    val namespace: String?,
    val rssi: Int,
    val timestamp: Instant,
    val proximity: BeaconProximity = BeaconProximity.Unknown
)

enum class BeaconProximity {
    Immediate, Near, Far, Unknown
}
