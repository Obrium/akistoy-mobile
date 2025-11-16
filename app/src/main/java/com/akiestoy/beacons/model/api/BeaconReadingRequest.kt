package com.akiestoy.beacons.model.api

import com.google.gson.annotations.SerializedName

/**
 * Request para enviar lecturas de beacons al servidor
 */
data class BeaconReadingRequest(
    @SerializedName("userId") val userId: String,
    @SerializedName("userName") val userName: String,
    @SerializedName("userPhone") val userPhone: String?,
    @SerializedName("deviceId") val deviceId: String,
    @SerializedName("nombreDispositivo") val nombreDispositivo: String,
    @SerializedName("deviceManufacturer") val deviceManufacturer: String,
    @SerializedName("deviceModel") val deviceModel: String,
    @SerializedName("androidVersion") val androidVersion: String,
    @SerializedName("beaconMac") val beaconMac: String,
    @SerializedName("beaconUuid") val beaconUuid: String,
    @SerializedName("beaconMajor") val beaconMajor: Int,
    @SerializedName("beaconMinor") val beaconMinor: Int,
    @SerializedName("beaconName") val beaconName: String,
    @SerializedName("beaconId") val beaconId: String,
    @SerializedName("txPower") val txPower: Int,
    @SerializedName("rssi") val rssi: Int,
    @SerializedName("estimatedDistance") val estimatedDistance: Double,
    @SerializedName("proximityLevel") val proximityLevel: String,
    @SerializedName("zona") val zona: String,
    @SerializedName("empresaId") val empresaId: String,
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("eventType") val eventType: String = "READING",
    @SerializedName("coordenadas") val coordenadas: Coordenadas? = null
)

data class Coordenadas(
    @SerializedName("latitude") val latitude: Double?,
    @SerializedName("longitude") val longitude: Double?,
    @SerializedName("accuracy") val accuracy: Float?
)
