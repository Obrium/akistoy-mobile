package com.akiestoy.beacons.model.tracking

import com.google.gson.annotations.SerializedName

/**
 * Request para enviar heartbeat al backend
 */
data class HeartbeatRequest(
    @SerializedName("deviceId")
    val deviceId: String,

    @SerializedName("timestamp")
    val timestamp: Long,

    @SerializedName("tenantId")
    val tenantId: String
)

/**
 * Response del backend para heartbeat
 */
data class HeartbeatResponse(
    @SerializedName("status")
    val status: String,

    @SerializedName("timestamp")
    val timestamp: Long
)
