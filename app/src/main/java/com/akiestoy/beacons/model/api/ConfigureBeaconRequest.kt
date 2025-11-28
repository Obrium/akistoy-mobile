package com.akiestoy.beacons.model.api

/**
 * Request para configurar un beacon en el servidor
 */
data class ConfigureBeaconRequest(
    val mac: String,
    val uuid: String,
    val major: Int,
    val minor: Int,
    val beaconName: String,
    val zoneName: String,
    val beaconType: String = "tracking",
    val tenantId: String,
    val companyId: String,
    val txPower: Int = -59,
    val model: String = "ESP32"
)

/**
 * Response de la configuración de beacon
 */
data class ConfigureBeaconResponse(
    val message: String,
    val beacon: BeaconData?,
    val zone: ZoneData?
)

data class BeaconData(
    val id: String,
    val advUuid: String,
    val major: Int,
    val minor: Int,
    val name: String?,
    val beaconType: String,
    val status: String
)

data class ZoneData(
    val id: String,
    val name: String,
    val type: String
)
