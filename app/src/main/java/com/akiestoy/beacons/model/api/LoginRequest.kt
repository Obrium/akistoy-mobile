package com.akiestoy.beacons.model.api

import com.google.gson.annotations.SerializedName

/**
 * Request para el servicio mobile-login
 */
data class LoginRequest(
    @SerializedName("rut")
    val rut: String,

    @SerializedName("rutEmpresa")
    val rutEmpresa: String
)
