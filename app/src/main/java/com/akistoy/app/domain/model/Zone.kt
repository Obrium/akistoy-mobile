package com.akistoy.app.domain.model

data class Zone(
    val id: String,
    val name: String,
    val beaconUuids: List<String>
)
