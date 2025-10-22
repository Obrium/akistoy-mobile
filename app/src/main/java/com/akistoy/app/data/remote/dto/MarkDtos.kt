package com.akistoy.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BeaconMarkRequest(
    @SerialName("device_id") val deviceId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("beacon_id") val beaconId: String,
    @SerialName("rssi") val rssi: Int,
    @SerialName("ts_client") val timestamp: String
)

@Serializable
data class BeaconMarkResponse(
    @SerialName("status") val status: String = "ok"
)
