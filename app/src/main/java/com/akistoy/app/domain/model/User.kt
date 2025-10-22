package com.akistoy.app.domain.model

data class User(
    val id: String,
    val email: String,
    val token: String,
    val deviceId: String
)
