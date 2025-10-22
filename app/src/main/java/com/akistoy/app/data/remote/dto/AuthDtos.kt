package com.akistoy.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    @SerialName("email") val email: String,
    @SerialName("device_id") val deviceId: String
)

@Serializable
data class LoginResponse(
    @SerialName("user_id") val userId: String,
    @SerialName("token") val token: String,
    @SerialName("email") val email: String
)
