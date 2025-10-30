package com.akiestoy.beacons.model.proximity

import com.google.gson.annotations.SerializedName
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Evento de proximidad que se envía al backend
 *
 * Ejemplo JSON:
 * {
 *   "deviceId": "phone-uuid",
 *   "beaconId": "beacon-mac",
 *   "event": "enter",
 *   "timestamp": "2025-10-30T16:45:10Z",
 *   "metrics": {
 *     "rssiAvg": -62.0,
 *     "samples": 5
 *   }
 * }
 */
data class ProximityEvent(
    /**
     * ID único del dispositivo móvil (Android ID)
     */
    @SerializedName("deviceId")
    val deviceId: String,

    /**
     * ID del beacon (MAC address según requerimiento)
     */
    @SerializedName("beaconId")
    val beaconId: String,

    /**
     * Tipo de evento: enter, exit, heartbeat
     */
    @SerializedName("event")
    val event: String,

    /**
     * Timestamp en formato ISO 8601 UTC
     */
    @SerializedName("timestamp")
    val timestamp: String,

    /**
     * Métricas de proximidad
     */
    @SerializedName("metrics")
    val metrics: ProximityMetrics
) {
    companion object {
        private val ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT

        /**
         * Crea un evento de proximidad con timestamp actual
         */
        fun create(
            deviceId: String,
            beaconId: String,
            eventType: ProximityEventType,
            rssiAvg: Double,
            samples: Int
        ): ProximityEvent {
            val timestamp = Instant.now().atOffset(ZoneOffset.UTC).format(ISO_FORMATTER)

            return ProximityEvent(
                deviceId = deviceId,
                beaconId = beaconId,
                event = eventType.name.lowercase(),
                timestamp = timestamp,
                metrics = ProximityMetrics(rssiAvg, samples)
            )
        }
    }
}
