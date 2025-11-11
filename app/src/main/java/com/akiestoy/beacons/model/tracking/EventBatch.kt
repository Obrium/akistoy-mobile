package com.akiestoy.beacons.model.tracking

import com.akiestoy.beacons.model.proximity.BeaconProximityRequest

/**
 * Batch de eventos para enviar al backend
 */
data class EventBatch(
    val events: List<BeaconProximityRequest>,
    val timestamp: Long = System.currentTimeMillis()
)
