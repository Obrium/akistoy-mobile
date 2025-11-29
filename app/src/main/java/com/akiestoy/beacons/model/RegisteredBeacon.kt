package com.akiestoy.beacons.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidad de beacon registrado en el sistema
 * Almacena los beacons que vienen del servicio de zonas
 */
@Entity(tableName = "registered_beacons")
data class RegisteredBeacon(
    @PrimaryKey
    val id: String,

    val tenantId: String,
    val companyId: String,
    val advUuid: String,      // UUID del beacon para escaneo
    val mac: String? = null,  // MAC address del beacon (opcional)
    val beaconName: String? = null,  // Nombre del beacon (opcional)
    val major: Int,
    val minor: Int,
    val txPower: Int,
    val model: String,
    val beaconType: String,   // "tracking", "entrance", etc.
    val status: String,       // "active", "inactive"
    val zoneName: String,
    val zoneId: String,

    // Metadatos
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
