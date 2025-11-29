package com.akiestoy.beacons.model.tracking

/**
 * Request para eventos de zona optimizados
 * Reduce drásticamente los eventos enviados (~115,200/día → ~10-20/día por dispositivo)
 *
 * Tipos de eventos:
 * - COMPANY_ENTRY: Usuario detectado en beacon entrada_salida (entrada a empresa)
 * - COMPANY_EXIT: Usuario detectado en beacon entrada_salida + 1min sin tracking beacons
 * - ZONE_CHANGE: Usuario cambió de zona tracking
 */
data class ZoneEventRequest(
    val deviceId: String,
    val tenantId: String,
    val eventType: String, // "COMPANY_ENTRY", "COMPANY_EXIT", "ZONE_CHANGE"
    val beaconId: String,
    val beaconMac: String? = null,
    val zoneName: String,
    val zoneId: String? = null,
    val fromZone: String? = null, // Solo para ZONE_CHANGE
    val rssi: Int? = null,
    val timestamp: Long,
    val userRut: String? = null,
    val userName: String? = null
)

/**
 * Respuesta del backend al enviar un evento de zona
 */
data class ZoneEventResponse(
    val status: String,
    val eventType: String,
    val timestamp: Long
)
