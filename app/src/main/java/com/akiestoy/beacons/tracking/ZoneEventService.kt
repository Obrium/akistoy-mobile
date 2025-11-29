package com.akiestoy.beacons.tracking

import android.util.Log
import com.akiestoy.beacons.api.BeaconProximityApi
import com.akiestoy.beacons.model.RegisteredBeacon
import com.akiestoy.beacons.model.tracking.ZoneEventRequest
import com.akiestoy.beacons.state.ZoneInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * ZoneEventService - Envía eventos de zona optimizados al backend
 *
 * Este servicio reduce drásticamente los eventos enviados:
 * - De ~115,200 eventos/día (beacon-readings cada 3s)
 * - A ~10-20 eventos/día (solo cambios de zona y entrada/salida)
 *
 * Eventos:
 * - COMPANY_ENTRY: Primer beacon detectado con tipo "entrada_salida"
 * - COMPANY_EXIT: Beacon "entrada_salida" + 1min sin beacons "tracking"
 * - ZONE_CHANGE: Cambio entre zonas "tracking"
 */
class ZoneEventService(
    private val api: BeaconProximityApi,
    private val deviceId: String,
    private val tenantId: String = "550e8400-e29b-41d4-a716-446655440000"
) {
    private val TAG = "ZoneEventService"

    // Estado actual
    private var isInsideCompany: Boolean = false
    private var currentZoneName: String? = null
    private var lastEntryBeaconId: String? = null
    private var lastEntryBeaconMac: String? = null

    // Timeout para detectar salida
    private var exitCheckJob: Job? = null
    private var lastTrackingBeaconTimestamp: Long = 0L

    // Configuración
    companion object {
        // Tiempo sin beacons tracking para considerar posible salida (1 minuto)
        private const val TRACKING_TIMEOUT_MS = 60_000L
        // Intervalo de verificación (cada 10 segundos)
        private const val CHECK_INTERVAL_MS = 10_000L
    }

    /**
     * Inicia el servicio de monitoreo de eventos
     */
    fun start(scope: CoroutineScope) {
        exitCheckJob?.cancel()
        exitCheckJob = scope.launch {
            while (isActive) {
                delay(CHECK_INTERVAL_MS)
                checkForCompanyExit()
            }
        }
        Log.i(TAG, "🚀 ZoneEventService started")
    }

    /**
     * Detiene el servicio
     */
    fun stop() {
        exitCheckJob?.cancel()
        exitCheckJob = null
        Log.i(TAG, "🛑 ZoneEventService stopped")
    }

    /**
     * Procesa la detección de un beacon registrado
     * Llamar cada vez que ZoneManager detecta un beacon
     */
    suspend fun onBeaconDetected(
        beacon: RegisteredBeacon,
        rssi: Int,
        macAddress: String
    ) {
        val now = System.currentTimeMillis()
        val beaconType = beacon.beaconType.lowercase()

        when (beaconType) {
            "entrada_salida" -> handleEntradaSalidaBeacon(beacon, rssi, macAddress, now)
            "tracking" -> handleTrackingBeacon(beacon, rssi, macAddress, now)
            else -> {
                // Tratar tipos desconocidos como tracking
                handleTrackingBeacon(beacon, rssi, macAddress, now)
            }
        }
    }

    /**
     * Procesa un cambio de zona desde ZoneManager
     * Llamar cuando ZoneManager.currentZone cambia
     */
    suspend fun onZoneChanged(
        previousZone: ZoneInfo?,
        newZone: ZoneInfo?,
        beacon: RegisteredBeacon?
    ) {
        if (newZone == null || beacon == null) return

        val now = System.currentTimeMillis()
        val fromZoneName = previousZone?.beaconName

        // Solo enviar ZONE_CHANGE para beacons tracking
        if (beacon.beaconType.lowercase() == "tracking" && isInsideCompany) {
            if (fromZoneName != null && fromZoneName != newZone.beaconName) {
                sendZoneChange(
                    beaconId = beacon.id,
                    beaconMac = beacon.mac,
                    newZoneName = newZone.beaconName,
                    fromZoneName = fromZoneName,
                    rssi = newZone.rssi,
                    timestamp = now
                )
            }
        }

        currentZoneName = newZone.beaconName
    }

    // ========== Handlers por tipo de beacon ==========

    private suspend fun handleEntradaSalidaBeacon(
        beacon: RegisteredBeacon,
        rssi: Int,
        macAddress: String,
        timestamp: Long
    ) {
        if (!isInsideCompany) {
            // Usuario ENTRANDO a la empresa
            isInsideCompany = true
            lastEntryBeaconId = beacon.id
            lastEntryBeaconMac = macAddress
            currentZoneName = beacon.zoneName

            sendCompanyEntry(
                beaconId = beacon.id,
                beaconMac = macAddress,
                zoneName = beacon.zoneName,
                rssi = rssi,
                timestamp = timestamp
            )

            Log.i(TAG, "🚪 COMPANY_ENTRY detectada en ${beacon.zoneName}")

        } else {
            // Usuario ya dentro, detectando beacon entrada_salida de nuevo
            // Verificar si es SALIDA (timeout de tracking)
            val timeSinceLastTracking = timestamp - lastTrackingBeaconTimestamp

            if (lastTrackingBeaconTimestamp > 0 && timeSinceLastTracking > TRACKING_TIMEOUT_MS) {
                // Ha pasado más de 1 minuto sin beacons tracking → SALIDA
                isInsideCompany = false

                sendCompanyExit(
                    beaconId = beacon.id,
                    beaconMac = macAddress,
                    zoneName = beacon.zoneName,
                    rssi = rssi,
                    timestamp = timestamp
                )

                Log.i(TAG, "🚪 COMPANY_EXIT detectada en ${beacon.zoneName} (${timeSinceLastTracking/1000}s sin tracking)")

                // Reset estado
                currentZoneName = null
                lastTrackingBeaconTimestamp = 0L
            } else {
                Log.d(TAG, "📍 Beacon entrada_salida detectado pero aún hay tracking activo")
            }
        }
    }

    private suspend fun handleTrackingBeacon(
        beacon: RegisteredBeacon,
        rssi: Int,
        macAddress: String,
        timestamp: Long
    ) {
        lastTrackingBeaconTimestamp = timestamp

        if (!isInsideCompany) {
            // Si detectamos tracking sin haber pasado por entrada_salida,
            // asumimos entrada implícita
            isInsideCompany = true
            currentZoneName = beacon.zoneName

            sendCompanyEntry(
                beaconId = beacon.id,
                beaconMac = macAddress,
                zoneName = beacon.zoneName,
                rssi = rssi,
                timestamp = timestamp
            )

            Log.i(TAG, "🚪 COMPANY_ENTRY implícita (beacon tracking detectado)")
        }

        // Verificar cambio de zona
        if (currentZoneName != null && currentZoneName != beacon.zoneName) {
            val fromZone = currentZoneName!!

            sendZoneChange(
                beaconId = beacon.id,
                beaconMac = macAddress,
                newZoneName = beacon.zoneName,
                fromZoneName = fromZone,
                rssi = rssi,
                timestamp = timestamp
            )

            currentZoneName = beacon.zoneName
            Log.i(TAG, "🔄 ZONE_CHANGE: $fromZone → ${beacon.zoneName}")
        }
    }

    // ========== Verificación periódica ==========

    private suspend fun checkForCompanyExit() {
        if (!isInsideCompany) return

        val now = System.currentTimeMillis()
        val timeSinceLastTracking = now - lastTrackingBeaconTimestamp

        // Si no hemos visto ningún beacon tracking en más de 2 minutos,
        // y tampoco entrada_salida, considerar salida implícita
        if (lastTrackingBeaconTimestamp > 0 && timeSinceLastTracking > TRACKING_TIMEOUT_MS * 2) {
            Log.w(TAG, "⏰ Sin beacons por ${timeSinceLastTracking/1000}s, considerando salida implícita")
            // No enviamos evento aquí - esperamos a que el beacon entrada_salida lo confirme
        }
    }

    // ========== API Calls ==========

    private suspend fun sendCompanyEntry(
        beaconId: String,
        beaconMac: String?,
        zoneName: String,
        rssi: Int,
        timestamp: Long
    ) {
        try {
            val request = ZoneEventRequest(
                deviceId = deviceId,
                tenantId = tenantId,
                eventType = "COMPANY_ENTRY",
                beaconId = beaconId,
                beaconMac = beaconMac,
                zoneName = zoneName,
                rssi = rssi,
                timestamp = timestamp
            )

            Log.i(TAG, "📤 Enviando COMPANY_ENTRY: $zoneName")
            val response = api.sendZoneEvent(request)

            if (response.isSuccessful) {
                Log.i(TAG, "✅ COMPANY_ENTRY enviado exitosamente")
            } else {
                Log.e(TAG, "❌ Error enviando COMPANY_ENTRY: ${response.code()} - ${response.message()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception enviando COMPANY_ENTRY: ${e.message}", e)
        }
    }

    private suspend fun sendCompanyExit(
        beaconId: String,
        beaconMac: String?,
        zoneName: String,
        rssi: Int,
        timestamp: Long
    ) {
        try {
            val request = ZoneEventRequest(
                deviceId = deviceId,
                tenantId = tenantId,
                eventType = "COMPANY_EXIT",
                beaconId = beaconId,
                beaconMac = beaconMac,
                zoneName = zoneName,
                rssi = rssi,
                timestamp = timestamp
            )

            Log.i(TAG, "📤 Enviando COMPANY_EXIT: $zoneName")
            val response = api.sendZoneEvent(request)

            if (response.isSuccessful) {
                Log.i(TAG, "✅ COMPANY_EXIT enviado exitosamente")
            } else {
                Log.e(TAG, "❌ Error enviando COMPANY_EXIT: ${response.code()} - ${response.message()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception enviando COMPANY_EXIT: ${e.message}", e)
        }
    }

    private suspend fun sendZoneChange(
        beaconId: String,
        beaconMac: String?,
        newZoneName: String,
        fromZoneName: String,
        rssi: Int,
        timestamp: Long
    ) {
        try {
            val request = ZoneEventRequest(
                deviceId = deviceId,
                tenantId = tenantId,
                eventType = "ZONE_CHANGE",
                beaconId = beaconId,
                beaconMac = beaconMac,
                zoneName = newZoneName,
                fromZone = fromZoneName,
                rssi = rssi,
                timestamp = timestamp
            )

            Log.i(TAG, "📤 Enviando ZONE_CHANGE: $fromZoneName → $newZoneName")
            val response = api.sendZoneEvent(request)

            if (response.isSuccessful) {
                Log.i(TAG, "✅ ZONE_CHANGE enviado exitosamente")
            } else {
                Log.e(TAG, "❌ Error enviando ZONE_CHANGE: ${response.code()} - ${response.message()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception enviando ZONE_CHANGE: ${e.message}", e)
        }
    }

    /**
     * Limpia el estado
     */
    fun reset() {
        isInsideCompany = false
        currentZoneName = null
        lastEntryBeaconId = null
        lastEntryBeaconMac = null
        lastTrackingBeaconTimestamp = 0L
        stop()
        Log.i(TAG, "🔄 ZoneEventService reset")
    }

    /**
     * Información de debug
     */
    fun getDebugInfo(): String {
        return buildString {
            appendLine("=== ZoneEventService Debug ===")
            appendLine("Inside Company: $isInsideCompany")
            appendLine("Current Zone: ${currentZoneName ?: "NONE"}")
            appendLine("Last Entry Beacon: ${lastEntryBeaconId ?: "N/A"}")
            val trackingAgo = if (lastTrackingBeaconTimestamp > 0) {
                "${(System.currentTimeMillis() - lastTrackingBeaconTimestamp) / 1000}s ago"
            } else "N/A"
            appendLine("Last Tracking Beacon: $trackingAgo")
        }
    }
}
