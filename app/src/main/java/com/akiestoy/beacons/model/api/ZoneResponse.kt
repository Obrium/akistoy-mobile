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
    val rssiThresholdFar: Int,

    @SerializedName("beacons")
    val beacons: List<BeaconResponse> = emptyList()
)

/**
 * Respuesta de un beacon dentro de una zona
 */
data class BeaconResponse(
    @SerializedName("id")
    val id: String,

    @SerializedName("tenantId")
    val tenantId: String,

    @SerializedName("companyId")
    val companyId: String,

    @SerializedName("advUuid")
    val advUuid: String,

    @SerializedName("major")
    val major: Int,

    @SerializedName("minor")
    val minor: Int,

    @SerializedName("txPower")
    val txPower: Int,

    @SerializedName("model")
    val model: String,

    @SerializedName("beaconType")
    val beaconType: String,

    @SerializedName("status")
    val status: String,

    @SerializedName("zoneName")
    val zoneName: String,

    @SerializedName("zoneId")
    val zoneId: String
)
