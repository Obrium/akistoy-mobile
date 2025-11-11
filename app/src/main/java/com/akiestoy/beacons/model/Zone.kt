package com.akiestoy.beacons.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidad de zona almacenada en la base de datos local
 * Representa una zona física vinculada a la empresa/tenant
 */
@Entity(tableName = "zones")
data class Zone(
    @PrimaryKey
    val id: String,

    val tenantId: String,
    val companyId: String,
    val name: String,
    val type: String, // "warehouse", "entrance", "office", etc.
    val rssiThresholdNear: Int,
    val rssiThresholdFar: Int,
    
    // Metadatos
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

