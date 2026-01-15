package com.akiestoy.beacons.model.tracking

import com.google.gson.annotations.SerializedName

/**
 * Request para enviar heartbeat al backend
 * Se envía cada 5 minutos para indicar que la app está activa
 */
data class HeartbeatRequest(
    @SerializedName("deviceId")
    val deviceId: String,

    @SerializedName("timestamp")
    val timestamp: Long,

    @SerializedName("tenantId")
    val tenantId: String,

    @SerializedName("employeeRut")
    val employeeRut: String? = null,

    @SerializedName("employeeName")
    val employeeName: String? = null,

    @SerializedName("appVersion")
    val appVersion: String? = null
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
