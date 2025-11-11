package com.akiestoy.beacons.model.api

import com.google.gson.annotations.SerializedName

/**
 * Respuesta del servicio de zonas
 */
data class ZoneResponse(
    @SerializedName("id")
    val id: String,

    @SerializedName("tenantId")
    val tenantId: String,

    @SerializedName("companyId")
    val companyId: String,

    @SerializedName("name")
    val name: String,

    @SerializedName("type")
    val type: String, // "warehouse", "entrance", "office", etc.

    @SerializedName("rssiThresholdNear")
    val rssiThresholdNear: Int,

    @SerializedName("rssiThresholdFar")
    val rssiThresholdFar: Int
)
