package com.akiestoy.beacons.model.user

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidad de usuario almacenada en la base de datos local
 * Incluye datos de autenticación del empleado y dispositivo
 */
@Entity(tableName = "users")
data class User(
    @PrimaryKey
    val id: String,

    // Datos del empleado
    val rut: String,
    val name: String,
    val email: String,
    val phone: String = "",
    val companyId: String,
    val companyRut: String = "", // RUT de la empresa para re-login
    val tenantId: String,
    val active: Boolean = true,
    val consentTracking: Boolean = true,

    // Datos de autenticación
    val accessToken: String,
    val tokenExpiresAt: Long, // timestamp when token expires

    // Datos del dispositivo
    val deviceId: String,
    val hmacKey: String,

    // Metadatos
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
