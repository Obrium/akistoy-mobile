package com.akiestoy.beacons.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidad de Room para eventos de zona pendientes de envío
 * Usado para cola offline cuando no hay conexión a internet
 *
 * Los eventos se guardan en SQLite y se envían cuando hay conexión
 */
@Entity(tableName = "pending_zone_events")
data class PendingZoneEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Tipo de evento: COMPANY_ENTRY, COMPANY_EXIT, ZONE_CHANGE
    val eventType: String,

    // Datos del beacon
    val beaconId: String,
    val beaconMac: String?,
    val zoneName: String,
    val fromZone: String?, // Solo para ZONE_CHANGE

    // Datos del dispositivo
    val deviceId: String,
    val tenantId: String,

    // Datos del usuario
    val userRut: String?,
    val userName: String?,

    // Metadata
    val rssi: Int?,
    val timestamp: Long, // Timestamp original del evento

    // Control de reintentos
    val retryCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val lastRetryAt: Long? = null
)
