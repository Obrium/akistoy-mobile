package com.akistoy.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trusted_beacons")
data class TrustedBeaconEntity(
    @PrimaryKey
    val beaconId: String,           // MAC address o identificador único
    val name: String,                // Nombre descriptivo del beacon
    val uuid: String?,               // UUID del servicio (si es un beacon BLE)
    val addedAt: Long = System.currentTimeMillis(),  // Timestamp cuando se agregó
    val isEnabled: Boolean = true    // Permite desactivar temporalmente sin eliminar
)
