package com.akiestoy.beacons.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidad de Room para eventos pendientes de envío
 * Usado para cola offline cuando no hay conexión
 */
@Entity(tableName = "pending_events")
data class PendingEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val beaconId: String,
    val zona: String,
    val nombreDispositivo: String,
    val deviceId: String,
    val empresaId: String,
    val rssi: Int,
    val timestamp: Long = System.currentTimeMillis(),

    // Metadata
    val retryCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
