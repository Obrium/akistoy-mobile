package com.akiestoy.beacons.model.api

import com.google.gson.annotations.SerializedName

/**
 * Respuesta del servicio mobile-login
 */
data class LoginResponse(
    @SerializedName("accessToken")
    val accessToken: String,

    @SerializedName("employee")
    val employee: EmployeeData,

    @SerializedName("device")
    val device: DeviceData,

    @SerializedName("expiresIn")
    val expiresIn: Long
)

/**
 * Datos del empleado
 */
data class EmployeeData(
    @SerializedName("id")
    val id: String,

    @SerializedName("rut")
    val rut: String,

    @SerializedName("name")
    val name: String,

    @SerializedName("email")
    val email: String,

    @SerializedName("companyId")
    val companyId: String,

    @SerializedName("tenantId")
    val tenantId: String,

    @SerializedName("active")
    val active: Boolean,

    @SerializedName("consentTracking")
    val consentTracking: Boolean
)

/**
 * Datos del dispositivo
 */
data class DeviceData(
    @SerializedName("deviceId")
    val deviceId: String,

    @SerializedName("hmacKey")
    val hmacKey: String
)
