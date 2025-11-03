package com.akiestoy.beacons.model.user

import com.google.gson.annotations.SerializedName

/**
 * Respuesta del servicio de registro de usuario
 */
data class UserResponse(
    @SerializedName("id")
    val id: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("phone")
    val phone: String
)
