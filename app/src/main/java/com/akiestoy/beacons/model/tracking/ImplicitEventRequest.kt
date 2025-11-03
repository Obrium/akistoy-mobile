package com.akiestoy.beacons.model.tracking

import com.google.gson.annotations.SerializedName

/**
 * Request para enviar eventos implícitos de entrada/salida
 */
data class ImplicitEventRequest(
    @SerializedName("deviceId")
    val deviceId: String,

    @SerializedName("eventType")
    val eventType: String, // "IMPLICIT_ENTRY" o "IMPLICIT_EXIT"

    @SerializedName("timestamp")
    val timestamp: Long,

    @SerializedName("tenantId")
    val tenantId: String,

    @SerializedName("lastBeaconId")
    val lastBeaconId: String? = null
)

/**
 * Response del backend para implicit events
 */
data class ImplicitEventResponse(
    @SerializedName("status")
    val status: String,

    @SerializedName("eventType")
    val eventType: String,

    @SerializedName("timestamp")
    val timestamp: Long
)
