package com.akiestoy.beacons.tracking

import android.util.Log
import com.akiestoy.beacons.api.BeaconProximityApi
import com.akiestoy.beacons.data.PendingZoneEvent
import com.akiestoy.beacons.data.PendingZoneEventDao
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
 * ARQUITECTURA:
 * - Observa ZoneManager.currentZone para cambios de zona ESTABILIZADOS
 * - Solo envía ZONE_CHANGE cuando ZoneManager confirma un cambio
 * - Usa histéresis y EMA del ZoneManager para evitar fluctuaciones
 *
 * Eventos:
 * - COMPANY_ENTRY: Primera zona detectada (entrada a la empresa)
 * - COMPANY_EXIT: Timeout sin señal de beacons
 * - ZONE_CHANGE: Cambio de zona confirmado por ZoneManager
 */
class ZoneEventService(
    private val api: BeaconProximityApi,
    private val deviceId: String,
    private val pendingZoneEventDao: PendingZoneEventDao? = null,
    private val tenantId: String = "550e8400-e29b-41d4-a716-446655440000",
    private var userRut: String? = null,
    private var userName: String? = null
) {
    private val TAG = "ZoneEventService"

    /**
     * Actualiza los datos del usuario (llamar después de login)
     */
    fun setUser(rut: String?, name: String?) {
        userRut = rut
        userName = name
        Log.i(TAG, "👤 Usuario configurado: $name (RUT: $rut)")
    }

    // Estado actual
    private var isInsideCompany: Boolean = false
    private var currentZoneName: String? = null
    private var currentBeaconId: String? = null
    private var currentBeaconMac: String? = null

    // Timeout para detectar salida
    private var exitCheckJob: Job? = null
    private var retryQueueJob: Job? = null
    private var lastBeaconTimestamp: Long = 0L

    // Cache del último beacon detectado (para tener beaconId cuando ZoneManager cambia)
    private var lastDetectedBeacons: MutableMap<String, CachedBeaconInfo> = mutableMapOf()

    data class CachedBeaconInfo(
        val beaconId: String,
        val beaconMac: String?,
        val beaconType: String,
        val rssi: Int,
        val timestamp: Long
    )

    // Configuración
    companion object {
        // Tiempo sin beacons para considerar salida (25 segundos - igual que ZoneManager)
        private const val EXIT_TIMEOUT_MS = 25_000L
        // Intervalo de verificación (cada 10 segundos)
        private const val CHECK_INTERVAL_MS = 10_000L
        // Intervalo de reintento de cola offline (cada 30 segundos)
        private const val RETRY_QUEUE_INTERVAL_MS = 30_000L
        // Máximo de reintentos por evento
        private const val MAX_RETRY_COUNT = 3
        // Tiempo máximo para mantener eventos en cola (24 horas)
        private const val MAX_EVENT_AGE_MS = 24 * 60 * 60 * 1000L
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

        // Iniciar job de reintento de cola offline
        retryQueueJob?.cancel()
        retryQueueJob = scope.launch {
            // Esperar 10 segundos antes de empezar a reintentar
            delay(10_000L)
            while (isActive) {
                retryPendingEvents()
                delay(RETRY_QUEUE_INTERVAL_MS)
            }
        }

        Log.i(TAG, "🚀 ZoneEventService started with offline queue support")
    }

    /**
     * Detiene el servicio
     */
    fun stop() {
        exitCheckJob?.cancel()
        exitCheckJob = null
        retryQueueJob?.cancel()
        retryQueueJob = null
        Log.i(TAG, "🛑 ZoneEventService stopped")
    }

    /**
     * Actualiza el cache de beacons detectados
     * Se llama en cada detección para mantener info actualizada de beaconId/mac por zona
     * NO envía eventos - solo mantiene el cache
     */
    fun updateBeaconCache(
        beacon: RegisteredBeacon,
        rssi: Int,
        macAddress: String
    ) {
        val now = System.currentTimeMillis()
        lastBeaconTimestamp = now

        // Guardar info del beacon para usar cuando ZoneManager confirme cambio
        lastDetectedBeacons[beacon.zoneName] = CachedBeaconInfo(
            beaconId = beacon.id,
            beaconMac = macAddress,
            beaconType = beacon.beaconType,
            rssi = rssi,
            timestamp = now
        )
    }

    /**
     * Procesa un cambio de zona ESTABILIZADO desde ZoneManager
     * Este es el único punto donde se envían eventos ZONE_CHANGE
     *
     * @param previousZone Zona anterior (puede ser null si es entrada)
     * @param newZone Nueva zona confirmada por ZoneManager
     */
    suspend fun onStableZoneChanged(
        previousZone: ZoneInfo?,
        newZone: ZoneInfo?
    ) {
        val now = System.currentTimeMillis()

        // Si newZone es null, ZoneManager perdió señal (timeout)
        if (newZone == null) {
            if (isInsideCompany && currentZoneName != null) {
                // Enviar COMPANY_EXIT
                val cachedBeacon = lastDetectedBeacons[currentZoneName]
                sendCompanyExit(
                    beaconId = cachedBeacon?.beaconId ?: "unknown",
                    beaconMac = cachedBeacon?.beaconMac,
                    zoneName = currentZoneName!!,
                    rssi = cachedBeacon?.rssi ?: -100,
                    timestamp = now
                )
                Log.i(TAG, "🚪 COMPANY_EXIT (timeout de ZoneManager)")

                isInsideCompany = false
                currentZoneName = null
                currentBeaconId = null
                currentBeaconMac = null
            }
            return
        }

        val newZoneName = newZone.beaconName
        val cachedBeacon = lastDetectedBeacons[newZoneName]

        // Primera zona detectada = COMPANY_ENTRY
        if (!isInsideCompany) {
            isInsideCompany = true
            currentZoneName = newZoneName
            currentBeaconId = cachedBeacon?.beaconId
            currentBeaconMac = cachedBeacon?.beaconMac

            sendCompanyEntry(
                beaconId = cachedBeacon?.beaconId ?: "unknown",
                beaconMac = cachedBeacon?.beaconMac,
                zoneName = newZoneName,
                rssi = newZone.rssi,
                timestamp = now
            )
            Log.i(TAG, "🚪 COMPANY_ENTRY: $newZoneName")
            return
        }

        // Cambio de zona (solo si es diferente)
        val previousZoneName = previousZone?.beaconName ?: currentZoneName
        if (previousZoneName != null && previousZoneName != newZoneName) {
            sendZoneChange(
                beaconId = cachedBeacon?.beaconId ?: "unknown",
                beaconMac = cachedBeacon?.beaconMac,
                newZoneName = newZoneName,
                fromZoneName = previousZoneName,
                rssi = newZone.rssi,
                timestamp = now
            )
            Log.i(TAG, "🔄 ZONE_CHANGE (estabilizado): $previousZoneName → $newZoneName")
        }

        // Actualizar estado
        currentZoneName = newZoneName
        currentBeaconId = cachedBeacon?.beaconId
        currentBeaconMac = cachedBeacon?.beaconMac
    }

    // ========== Verificación periódica ==========

    private suspend fun checkForCompanyExit() {
        if (!isInsideCompany) return

        val now = System.currentTimeMillis()
        val timeSinceLastBeacon = now - lastBeaconTimestamp

        // Si no hemos visto ningún beacon en más del timeout
        if (lastBeaconTimestamp > 0 && timeSinceLastBeacon > EXIT_TIMEOUT_MS) {
            Log.w(TAG, "⏰ Sin beacons por ${timeSinceLastBeacon/1000}s")
            // El ZoneManager manejará esto con su propio timeout y notificará via onStableZoneChanged(null)
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
        val request = ZoneEventRequest(
            deviceId = deviceId,
            tenantId = tenantId,
            eventType = "COMPANY_ENTRY",
            beaconId = beaconId,
            beaconMac = beaconMac,
            zoneName = zoneName,
            rssi = rssi,
            timestamp = timestamp,
            userRut = userRut,
            userName = userName
        )

        val success = sendEventWithRetry(request)
        if (!success) {
            // Guardar en cola offline
            saveToOfflineQueue(
                eventType = "COMPANY_ENTRY",
                beaconId = beaconId,
                beaconMac = beaconMac,
                zoneName = zoneName,
                fromZone = null,
                rssi = rssi,
                timestamp = timestamp
            )
        }
    }

    private suspend fun sendCompanyExit(
        beaconId: String,
        beaconMac: String?,
        zoneName: String,
        rssi: Int,
        timestamp: Long
    ) {
        val request = ZoneEventRequest(
            deviceId = deviceId,
            tenantId = tenantId,
            eventType = "COMPANY_EXIT",
            beaconId = beaconId,
            beaconMac = beaconMac,
            zoneName = zoneName,
            rssi = rssi,
            timestamp = timestamp,
            userRut = userRut,
            userName = userName
        )

        val success = sendEventWithRetry(request)
        if (!success) {
            saveToOfflineQueue(
                eventType = "COMPANY_EXIT",
                beaconId = beaconId,
                beaconMac = beaconMac,
                zoneName = zoneName,
                fromZone = null,
                rssi = rssi,
                timestamp = timestamp
            )
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
        val request = ZoneEventRequest(
            deviceId = deviceId,
            tenantId = tenantId,
            eventType = "ZONE_CHANGE",
            beaconId = beaconId,
            beaconMac = beaconMac,
            zoneName = newZoneName,
            fromZone = fromZoneName,
            rssi = rssi,
            timestamp = timestamp,
            userRut = userRut,
            userName = userName
        )

        val success = sendEventWithRetry(request)
        if (!success) {
            saveToOfflineQueue(
                eventType = "ZONE_CHANGE",
                beaconId = beaconId,
                beaconMac = beaconMac,
                zoneName = newZoneName,
                fromZone = fromZoneName,
                rssi = rssi,
                timestamp = timestamp
            )
        }
    }

    /**
     * Envía un evento con reintento inmediato (1 vez)
     * @return true si el envío fue exitoso, false si falló
     */
    private suspend fun sendEventWithRetry(request: ZoneEventRequest): Boolean {
        // VALIDACIÓN: No enviar eventos sin RUT - el backend requiere identificación del empleado
        if (request.userRut.isNullOrBlank()) {
            Log.w(TAG, "⚠️ NO SE ENVÍA ${request.eventType} - Usuario sin RUT (no ha iniciado sesión)")
            return false
        }

        try {
            Log.i(TAG, "📤 Enviando ${request.eventType}: ${request.zoneName} (RUT: ${request.userRut})")
            val response = api.sendZoneEvent(request)

            if (response.isSuccessful) {
                Log.i(TAG, "✅ ${request.eventType} enviado exitosamente")
                return true
            } else {
                Log.e(TAG, "❌ Error enviando ${request.eventType}: ${response.code()} - ${response.message()}")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception enviando ${request.eventType}: ${e.message}", e)
            return false
        }
    }

    /**
     * Guarda un evento en la cola offline para reintento posterior
     */
    private suspend fun saveToOfflineQueue(
        eventType: String,
        beaconId: String,
        beaconMac: String?,
        zoneName: String,
        fromZone: String?,
        rssi: Int?,
        timestamp: Long
    ) {
        // VALIDACIÓN: No guardar en cola eventos sin RUT - no tiene sentido reintentarlos
        if (userRut.isNullOrBlank()) {
            Log.w(TAG, "⚠️ NO SE GUARDA EN COLA $eventType - Usuario sin RUT")
            return
        }

        if (pendingZoneEventDao == null) {
            Log.w(TAG, "⚠️ No hay DAO configurado para cola offline")
            return
        }

        try {
            val pendingEvent = PendingZoneEvent(
                eventType = eventType,
                beaconId = beaconId,
                beaconMac = beaconMac,
                zoneName = zoneName,
                fromZone = fromZone,
                deviceId = deviceId,
                tenantId = tenantId,
                userRut = userRut,
                userName = userName,
                rssi = rssi,
                timestamp = timestamp
            )

            val id = pendingZoneEventDao.insert(pendingEvent)
            Log.i(TAG, "💾 Evento guardado en cola offline (ID: $id): $eventType - $zoneName")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error guardando en cola offline: ${e.message}", e)
        }
    }

    /**
     * Reintenta enviar eventos pendientes de la cola offline
     */
    private suspend fun retryPendingEvents() {
        if (pendingZoneEventDao == null) return

        try {
            // Primero, limpiar eventos muy viejos (más de 24 horas)
            val cutoffTime = System.currentTimeMillis() - MAX_EVENT_AGE_MS
            pendingZoneEventDao.deleteOlderThan(cutoffTime)

            // Obtener eventos pendientes reintentables
            val pendingEvents = pendingZoneEventDao.getRetryable(limit = 10)

            if (pendingEvents.isEmpty()) return

            Log.i(TAG, "📦 Reintentando ${pendingEvents.size} eventos pendientes...")

            for (event in pendingEvents) {
                val request = ZoneEventRequest(
                    deviceId = event.deviceId,
                    tenantId = event.tenantId,
                    eventType = event.eventType,
                    beaconId = event.beaconId,
                    beaconMac = event.beaconMac,
                    zoneName = event.zoneName,
                    fromZone = event.fromZone,
                    rssi = event.rssi ?: 0,
                    timestamp = event.timestamp,
                    userRut = event.userRut,
                    userName = event.userName
                )

                try {
                    val response = api.sendZoneEvent(request)

                    if (response.isSuccessful) {
                        // Eliminar evento exitoso de la cola
                        pendingZoneEventDao.delete(event.id)
                        Log.i(TAG, "✅ Evento pendiente enviado: ${event.eventType} - ${event.zoneName}")
                    } else {
                        // Incrementar contador de reintentos
                        pendingZoneEventDao.incrementRetryCount(event.id)
                        Log.w(TAG, "⚠️ Fallo reintento ${event.retryCount + 1}/$MAX_RETRY_COUNT: ${event.eventType}")
                    }
                } catch (e: Exception) {
                    // Incrementar contador de reintentos
                    pendingZoneEventDao.incrementRetryCount(event.id)
                    Log.e(TAG, "❌ Exception reintentando: ${e.message}")
                }

                // Pequeño delay entre eventos para no saturar
                delay(500)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error procesando cola offline: ${e.message}", e)
        }
    }

    /**
     * Limpia el estado
     */
    fun reset() {
        isInsideCompany = false
        currentZoneName = null
        currentBeaconId = null
        currentBeaconMac = null
        lastBeaconTimestamp = 0L
        lastDetectedBeacons.clear()
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
            appendLine("Current Beacon: ${currentBeaconId ?: "N/A"}")
            val beaconAgo = if (lastBeaconTimestamp > 0) {
                "${(System.currentTimeMillis() - lastBeaconTimestamp) / 1000}s ago"
            } else "N/A"
            appendLine("Last Beacon: $beaconAgo")
            appendLine("Cached Zones: ${lastDetectedBeacons.keys.joinToString(", ")}")
            appendLine("Offline Queue: ${if (pendingZoneEventDao != null) "enabled" else "disabled"}")
        }
    }

    /**
     * Obtiene el conteo de eventos pendientes en la cola
     */
    suspend fun getPendingCount(): Int {
        return pendingZoneEventDao?.count() ?: 0
    }

    /**
     * Obtiene un Flow del conteo de eventos pendientes
     */
    fun getPendingCountFlow() = pendingZoneEventDao?.countFlow()
}
