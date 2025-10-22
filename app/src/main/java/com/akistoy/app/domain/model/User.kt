package com.akistoy.app.domain.model

data class User(
    val id: String,
    val name: String,
    val token: String,
    val deviceId: String
)
