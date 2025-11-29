package com.akiestoy.beacons.tracking

import android.util.Log
import com.akiestoy.beacons.model.RegisteredBeacon
import com.akiestoy.beacons.state.AppState
import com.akiestoy.beacons.state.ZoneInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ZoneManager - Gestiona la determinación estable de la zona activa
 *
 * Características:
 * - Suavizado de RSSI con EMA (Exponential Moving Average)
 * - Histéresis para evitar cambios frecuentes de zona
 * - Confirmación temporal antes de cambiar zona
 * - Timeout para considerar salida de todas las zonas
 *
 * Parámetros configurables:
 * - EMA_ALPHA: Factor de suavizado (0.3 = 30% peso al nuevo valor)
 * - HYSTERESIS_DB: Diferencia mínima de RSSI para cambiar zona (5 dB)
 * - ZONE_CHANGE_CONFIRMATION_MS: Tiempo que debe mantenerse dominante (3s)
 * - BEACON_TIMEOUT_MS: Tiempo sin señal para considerar fuera de zona (20s)
 * - BEACON_ACTIVE_WINDOW_MS: Ventana para considerar beacon "activo" (15s)
 */
class ZoneManager {

    private val TAG = "ZoneManager"

    // ==================== CONFIGURACIÓN ====================
    companion object {
        // Factor de suavizado EMA: 0.5 = balance entre respuesta rápida y estabilidad
        // Con beacons de 3s: 0.5 estabiliza en ~4 detecciones (12s)
        const val EMA_ALPHA = 0.5

        // Diferencia mínima de RSSI para considerar cambio de zona
        // 6 dB da margen para ruido BLE típico (±3dB)
        const val HYSTERESIS_DB = 6

        // Detecciones consecutivas para confirmar cambio de zona
        // 2 detecciones = ~6 segundos con beacons de 3s
        const val ZONE_CHANGE_CONSECUTIVE_COUNT = 2

        // Tiempo sin señal de ningún beacon para considerar OUTSIDE (ms)
        // Debe ser > que el timeout total de BeaconTrackingService
        const val BEACON_TIMEOUT_MS = 25_000L

        // Ventana de tiempo para considerar un beacon "activo" (ms)
        // 4x intervalo de emisión (3s * 4 = 12s)
        const val BEACON_ACTIVE_WINDOW_MS = 12_000L

        // Intervalo mínimo para log de debug (evitar saturación)
        const val LOG_INTERVAL_MS = 5000L

        // Penalización de RSSI por tiempo sin señal (dB por segundo)
        // Beacon no visto en 6s pierde 3dB de "ventaja"
        const val RSSI_DECAY_PER_SECOND = 0.5
    }

    // ==================== ESTADO INTERNO ====================

    /**
     * Estado de un beacon individual con RSSI suavizado
     */
    data class BeaconRssiState(
        val beaconId: String,
        val zoneName: String,
        val mac: String?,
        var smoothedRssi: Double = -100.0,
        var lastRawRssi: Int = -100,
        var lastSeenTimestamp: Long = 0L,
        var sampleCount: Int = 0
    ) {
        /**
         * Actualiza el RSSI usando EMA (Exponential Moving Average)
         */
        fun updateRssi(newRssi: Int, timestamp: Long) {
            lastRawRssi = newRssi
            lastSeenTimestamp = timestamp
            sampleCount++

            // Primera muestra: usar valor directo
            if (sampleCount == 1) {
                smoothedRssi = newRssi.toDouble()
            } else {
                // EMA: nuevo = alpha * raw + (1 - alpha) * anterior
                smoothedRssi = EMA_ALPHA * newRssi + (1 - EMA_ALPHA) * smoothedRssi
            }
        }

        /**
         * Obtiene RSSI efectivo con penalización por tiempo sin señal
         * Beacons no vistos recientemente pierden "ventaja" gradualmente
         */
        fun getEffectiveRssi(currentTime: Long): Double {
            val timeSinceLastSeen = (currentTime - lastSeenTimestamp) / 1000.0 // en segundos
            val penalty = (timeSinceLastSeen * RSSI_DECAY_PER_SECOND).coerceAtMost(15.0)
            return smoothedRssi - penalty
        }

        /**
         * Verifica si el beacon está activo (señal reciente)
         */
        fun isActive(currentTime: Long): Boolean {
            return (currentTime - lastSeenTimestamp) < BEACON_ACTIVE_WINDOW_MS
        }
    }

    // Mapa de estados de RSSI por beaconId
    private val beaconStates = mutableMapOf<String, BeaconRssiState>()

    // Zona activa actual (confirmada)
    private val _currentZone = MutableStateFlow<ZoneInfo?>(null)
    val currentZone: StateFlow<ZoneInfo?> = _currentZone.asStateFlow()

    // Zona candidata (pendiente de confirmación)
    private var candidateZone: String? = null
    private var candidateZoneConsecutiveCount: Int = 0

    // Timestamp del último beacon detectado (cualquier zona)
    private var lastAnyBeaconTimestamp: Long = 0L

    // Control de logs
    private var lastLogTimestamp: Long = 0L

    // ==================== API PÚBLICA ====================

    /**
     * Procesa la detección de un beacon
     * @param beacon Beacon registrado (de la BD)
     * @param rssi RSSI crudo de la detección
     * @param macAddress MAC del dispositivo detectado
     */
    fun onBeaconDetected(beacon: RegisteredBeacon, rssi: Int, macAddress: String) {
        val currentTime = System.currentTimeMillis()
        lastAnyBeaconTimestamp = currentTime

        // Obtener o crear estado para este beacon
        val state = beaconStates.getOrPut(beacon.id) {
            BeaconRssiState(
                beaconId = beacon.id,
                zoneName = beacon.zoneName,
                mac = beacon.mac
            )
        }

        // Actualizar RSSI suavizado
        state.updateRssi(rssi, currentTime)

        // Log cada 5 segundos para evitar saturación
        if (currentTime - lastLogTimestamp >= LOG_INTERVAL_MS) {
            logCurrentState(currentTime)
            lastLogTimestamp = currentTime
        }

        // Evaluar cambio de zona
        evaluateZoneChange(currentTime)
    }

    /**
     * Verifica si se ha perdido señal de todos los beacons (debe llamarse periódicamente)
     * @return true si se considera que la persona salió de todas las zonas
     */
    fun checkTimeout(): Boolean {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastBeacon = currentTime - lastAnyBeaconTimestamp

        if (lastAnyBeaconTimestamp > 0 && timeSinceLastBeacon > BEACON_TIMEOUT_MS) {
            if (_currentZone.value != null) {
                Log.w(TAG, "⏰ Timeout: ${timeSinceLastBeacon/1000}s sin señal de ningún beacon")
                clearCurrentZone()
                return true
            }
        }
        return false
    }

    /**
     * Obtiene la zona activa actual (para UI)
     */
    fun getCurrentZoneName(): String? = _currentZone.value?.beaconName

    /**
     * Obtiene el beacon más cercano actualmente
     */
    fun getClosestBeacon(): BeaconRssiState? {
        val currentTime = System.currentTimeMillis()
        return beaconStates.values
            .filter { it.isActive(currentTime) }
            .maxByOrNull { it.smoothedRssi }
    }

    /**
     * Limpia todo el estado (para logout o reinicio)
     */
    fun reset() {
        beaconStates.clear()
        _currentZone.value = null
        candidateZone = null
        candidateZoneConsecutiveCount = 0
        lastAnyBeaconTimestamp = 0L
        AppState.clearCurrentZone()
        Log.i(TAG, "🔄 ZoneManager reset")
    }

    // ==================== LÓGICA INTERNA ====================

    /**
     * Evalúa si debe cambiar la zona activa
     * Implementa histéresis y confirmación por detecciones consecutivas
     *
     * Usa RSSI efectivo (con penalización temporal) para comparaciones justas
     * entre beacons vistos recientemente vs beacons con señal estancada
     */
    private fun evaluateZoneChange(currentTime: Long) {
        // Obtener beacon más cercano usando RSSI efectivo (con penalización temporal)
        val activeBeacons = beaconStates.values.filter { it.isActive(currentTime) }

        if (activeBeacons.isEmpty()) {
            // No hay beacons activos, pero aún no ha pasado timeout
            return
        }

        // Usar getEffectiveRssi para comparación justa (penaliza beacons no vistos recientemente)
        val closest = activeBeacons.maxByOrNull { it.getEffectiveRssi(currentTime) } ?: return
        val currentZoneName = _currentZone.value?.beaconName

        // Si no hay zona activa, establecer inmediatamente
        if (currentZoneName == null) {
            confirmZoneChange(closest, currentTime)
            return
        }

        // Si ya estamos en la zona del beacon más cercano, mantener
        if (closest.zoneName == currentZoneName) {
            // Actualizar info (RSSI puede haber cambiado)
            updateCurrentZoneInfo(closest)
            // Cancelar cualquier candidato pendiente
            candidateZone = null
            candidateZoneConsecutiveCount = 0
            return
        }

        // Hay una zona diferente que es más cercana
        // Verificar histéresis usando RSSI efectivo: el nuevo debe ser significativamente más fuerte
        val currentZoneState = beaconStates.values.find { it.zoneName == currentZoneName && it.isActive(currentTime) }

        if (currentZoneState != null) {
            // Comparar RSSI efectivos (con penalización temporal)
            val closestEffectiveRssi = closest.getEffectiveRssi(currentTime)
            val currentEffectiveRssi = currentZoneState.getEffectiveRssi(currentTime)
            val rssiDifference = closestEffectiveRssi - currentEffectiveRssi

            if (rssiDifference < HYSTERESIS_DB) {
                // Diferencia no es suficiente, mantener zona actual
                Log.v(TAG, "📊 Histéresis: ${closest.zoneName} solo +${rssiDifference.toInt()}dB sobre ${currentZoneName}, manteniendo")
                candidateZone = null
                candidateZoneConsecutiveCount = 0
                return
            }
        }

        // El nuevo beacon supera la histéresis
        // Verificar si ya es candidato y ha alcanzado el conteo de confirmación
        if (candidateZone == closest.zoneName) {
            candidateZoneConsecutiveCount++

            if (candidateZoneConsecutiveCount >= ZONE_CHANGE_CONSECUTIVE_COUNT) {
                // Confirmar cambio de zona después de N detecciones consecutivas
                Log.i(TAG, "✅ Zona confirmada después de $candidateZoneConsecutiveCount detecciones: ${closest.zoneName}")
                confirmZoneChange(closest, currentTime)
            } else {
                Log.v(TAG, "⏳ Candidato ${closest.zoneName} pendiente: $candidateZoneConsecutiveCount de $ZONE_CHANGE_CONSECUTIVE_COUNT detecciones")
            }
        } else {
            // Nuevo candidato - reiniciar contador
            candidateZone = closest.zoneName
            candidateZoneConsecutiveCount = 1  // Primera detección del nuevo candidato
            Log.i(TAG, "🆕 Nuevo candidato a zona: ${closest.zoneName} (RSSI efectivo: ${closest.getEffectiveRssi(currentTime).toInt()} dBm)")
        }
    }

    /**
     * Confirma el cambio a una nueva zona
     */
    private fun confirmZoneChange(beacon: BeaconRssiState, timestamp: Long) {
        val zoneInfo = ZoneInfo(
            beaconName = beacon.zoneName,
            beaconMac = beacon.mac ?: "",
            rssi = beacon.smoothedRssi.toInt(),
            timestamp = timestamp
        )

        val previousZone = _currentZone.value?.beaconName
        _currentZone.value = zoneInfo
        candidateZone = null
        candidateZoneConsecutiveCount = 0

        // Actualizar AppState para que la UI lo vea
        AppState.updateCurrentZone(zoneInfo)

        if (previousZone != null) {
            Log.i(TAG, "🔄 Cambio de zona: $previousZone → ${beacon.zoneName}")
        } else {
            Log.i(TAG, "📍 Zona inicial establecida: ${beacon.zoneName}")
        }
    }

    /**
     * Actualiza la información de la zona actual sin cambiarla
     */
    private fun updateCurrentZoneInfo(beacon: BeaconRssiState) {
        _currentZone.value?.let { current ->
            val updated = current.copy(
                rssi = beacon.smoothedRssi.toInt(),
                timestamp = beacon.lastSeenTimestamp
            )
            _currentZone.value = updated
            AppState.updateCurrentZone(updated)
        }
    }

    /**
     * Limpia la zona actual (salida)
     */
    private fun clearCurrentZone() {
        val previousZone = _currentZone.value?.beaconName
        _currentZone.value = null
        candidateZone = null
        AppState.clearCurrentZone()

        if (previousZone != null) {
            Log.i(TAG, "🚪 Salida de zona: $previousZone")
        }
    }

    /**
     * Log del estado actual (cada 5 segundos)
     */
    private fun logCurrentState(currentTime: Long) {
        val activeBeacons = beaconStates.values.filter { it.isActive(currentTime) }

        if (activeBeacons.isEmpty()) {
            Log.d(TAG, "📡 Sin beacons activos | Zona actual: ${_currentZone.value?.beaconName ?: "NINGUNA"}")
            return
        }

        val beaconsSummary = activeBeacons
            .sortedByDescending { it.smoothedRssi }
            .take(5)
            .joinToString(", ") { "${it.zoneName}:${it.smoothedRssi.toInt()}dBm" }

        Log.d(TAG, "📡 Beacons activos: $beaconsSummary | Zona: ${_currentZone.value?.beaconName ?: "NINGUNA"} | Candidato: ${candidateZone ?: "-"}")
    }

    /**
     * Obtiene información de debug para la UI
     */
    fun getDebugInfo(): String {
        val currentTime = System.currentTimeMillis()
        val activeBeacons = beaconStates.values.filter { it.isActive(currentTime) }

        return buildString {
            appendLine("=== ZoneManager Debug ===")
            appendLine("Zona actual: ${_currentZone.value?.beaconName ?: "NINGUNA"}")
            appendLine("Candidato: ${candidateZone ?: "-"} (${candidateZoneConsecutiveCount}/$ZONE_CHANGE_CONSECUTIVE_COUNT)")
            appendLine("Beacons activos: ${activeBeacons.size}")
            activeBeacons.sortedByDescending { it.getEffectiveRssi(currentTime) }.forEach { b ->
                val effectiveRssi = b.getEffectiveRssi(currentTime)
                val timeSince = (currentTime - b.lastSeenTimestamp) / 1000
                appendLine("  - ${b.zoneName}: EMA=${b.smoothedRssi.toInt()} Eff=${effectiveRssi.toInt()} dBm (raw: ${b.lastRawRssi}, ${timeSince}s ago)")
            }
        }
    }
}
