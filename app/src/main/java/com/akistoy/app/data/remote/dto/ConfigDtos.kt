package com.akistoy.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ConfigResponse(
    @SerialName("zones") val zones: List<ZoneDto> = emptyList(),
    @SerialName("uuids") val uuids: List<String> = emptyList()
)

@Serializable
data class ZoneDto(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("beacon_ids") val beaconIds: List<String> = emptyList()
)
