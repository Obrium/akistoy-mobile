package com.akiestoy.beacons.model.user

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidad de usuario almacenada en la base de datos local
 */
@Entity(tableName = "users")
data class User(
    @PrimaryKey
    val id: String,
    val rut: String,
    val name: String,
    val phone: String,
    val createdAt: Long = System.currentTimeMillis()
)
