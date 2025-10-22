package com.akistoy.app.domain.repo

import com.akistoy.app.domain.model.BeaconEvent
import kotlinx.coroutines.flow.Flow

interface BeaconRepository {
    val detections: Flow<List<BeaconEvent>>
    suspend fun startScanning(uuids: List<String>)
    suspend fun stopScanning()
}
