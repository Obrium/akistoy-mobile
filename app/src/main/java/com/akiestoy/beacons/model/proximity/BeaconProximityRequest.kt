package com.akiestoy.beacons.model.proximity

import com.google.gson.annotations.SerializedName

/**
 * Request para el backend según el endpoint /v1/mobile/beacon-reading
 */
data class BeaconProximityRequest(
    /**
     * UUID del beacon (el más cercano)
     */
    @SerializedName("beaconId")
    val beaconId: String,

    /**
     * MAC address del beacon
     */
    @SerializedName("zona")
    val zona: String,

    /**
     * Nombre del dispositivo móvil
     */
    @SerializedName("nombreDispositivo")
    val nombreDispositivo: String,

    /**
     * Android ID del dispositivo
     */
    @SerializedName("deviceId")
    val deviceId: String,

    /**
     * ID de la empresa (hardcoded por ahora)
     */
    @SerializedName("empresaId")
    val empresaId: String,

    /**
     * RSSI actual
     */
    @SerializedName("rssi")
    val rssi: Int
) {
    companion object {
        // ID de empresa hardcoded (cambiar según sea necesario)
        private const val DEFAULT_EMPRESA_ID = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"

        // UUID común de los beacons
        private const val BEACON_UUID = "e2c56db5-dffb-48d2-b060-d0f5a71096e0"

        /**
         * Crea un request desde un ProximityEvent
         */
        fun fromProximityEvent(
            event: ProximityEvent,
            deviceName: String,
            deviceId: String
        ): BeaconProximityRequest {
            return BeaconProximityRequest(
                beaconId = BEACON_UUID,
                zona = event.beaconId,  // MAC del beacon
                nombreDispositivo = deviceName,
                deviceId = deviceId,
                empresaId = DEFAULT_EMPRESA_ID,
                rssi = event.metrics.rssiAvg.toInt()
            )
        }
    }
}
