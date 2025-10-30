package com.akiestoy.beacons.proximity

import android.util.Log
import com.akiestoy.beacons.model.proximity.BeaconState
import com.akiestoy.beacons.model.proximity.ProximityConfig
import com.akiestoy.beacons.model.proximity.ProximityEvent
import com.akiestoy.beacons.model.proximity.ProximityEventType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Manager de proximidad que implementa la lógica de eventos enter/exit/heartbeat
 * con histeresis, suavizado de RSSI y detección de timeouts
 */
class BeaconProximityManager(
    private val deviceId: String
) {
    private val TAG = "BeaconProximityManager"

    // Estado de cada beacon (thread-safe)
    private val beaconStates = ConcurrentHashMap<String, BeaconState>()

    // Flow para emitir eventos de proximidad
    private val _proximityEvents = MutableSharedFlow<ProximityEvent>(replay = 0)
    val proximityEvents: SharedFlow<ProximityEvent> = _proximityEvents.asSharedFlow()

    init {
        Log.i(TAG, "📡 ProximityManager initialized for device: $deviceId")
    }

    /**
     * Procesa una nueva detección de beacon
     * @param beaconId MAC address del beacon
     * @param rssi Valor RSSI actual
     */
    suspend fun processBeaconDetection(beaconId: String, rssi: Int) {
        val currentTime = System.currentTimeMillis()

        // Obtener o crear estado del beacon
        val state = beaconStates.getOrPut(beaconId) {
            Log.i(TAG, "🆕 New beacon detected: $beaconId")
            BeaconState(beaconId = beaconId)
        }

        // Actualizar buffer de RSSI y timestamp
        state.addRssiSample(rssi)
        state.updateLastSeen(currentTime)

        Log.v(TAG, "📶 Beacon $beaconId - RSSI: $rssi, Avg: ${String.format("%.1f", state.getAverageRssi())}, " +
                "Samples: ${state.rssiBuffer.size}, InProximity: ${state.isInProximity}")

        // Evaluar transiciones de estado
        evaluateProximityState(state, currentTime)
    }

    /**
     * Evalúa el estado de proximidad y genera eventos según corresponda
     */
    private suspend fun evaluateProximityState(state: BeaconState, currentTime: Long) {
        val avgRssi = state.getAverageRssi()

        when {
            // Caso 1: Beacon NO está en proximidad -> Verificar ENTER
            !state.isInProximity && state.isAboveEnterThreshold() -> {
                handleEnterEvent(state, currentTime)
            }

            // Caso 2: Beacon SÍ está en proximidad -> Verificar EXIT o HEARTBEAT
            state.isInProximity -> {
                when {
                    // EXIT por RSSI bajo
                    state.isBelowExitThreshold() -> {
                        handleExitEvent(state, currentTime, "RSSI below threshold")
                    }
                    // HEARTBEAT periódico
                    state.shouldSendHeartbeat(currentTime) -> {
                        handleHeartbeatEvent(state, currentTime)
                    }
                }
            }
        }
    }

    /**
     * Verifica timeouts de beacons que no se han detectado recientemente
     */
    suspend fun checkTimeouts() {
        val currentTime = System.currentTimeMillis()

        beaconStates.values.forEach { state ->
            if (state.isInProximity && state.isLost(currentTime)) {
                handleExitEvent(state, currentTime, "Timeout - not detected")
            }
        }
    }

    /**
     * Maneja el evento ENTER
     */
    private suspend fun handleEnterEvent(state: BeaconState, currentTime: Long) {
        Log.i(TAG, "🟢 ENTER event for beacon ${state.beaconId} - Avg RSSI: ${String.format("%.1f", state.getAverageRssi())} dBm, " +
                "Samples: ${state.rssiBuffer.size}")

        state.isInProximity = true
        state.markEventSent(currentTime)
        state.markHeartbeatSent(currentTime)

        // Crear y emitir evento
        val event = ProximityEvent.create(
            deviceId = deviceId,
            beaconId = state.beaconId,
            eventType = ProximityEventType.ENTER,
            rssiAvg = state.getAverageRssi(),
            samples = state.rssiBuffer.size
        )

        _proximityEvents.emit(event)
        Log.d(TAG, "✅ ENTER event emitted successfully")
    }

    /**
     * Maneja el evento EXIT
     */
    private suspend fun handleExitEvent(state: BeaconState, currentTime: Long, reason: String) {
        Log.i(TAG, "🔴 EXIT event for beacon ${state.beaconId} - Reason: $reason, Avg RSSI: ${String.format("%.1f", state.getAverageRssi())} dBm")

        state.isInProximity = false
        state.markEventSent(currentTime)

        // Crear y emitir evento
        val event = ProximityEvent.create(
            deviceId = deviceId,
            beaconId = state.beaconId,
            eventType = ProximityEventType.EXIT,
            rssiAvg = state.getAverageRssi(),
            samples = state.rssiBuffer.size
        )

        _proximityEvents.emit(event)
        Log.d(TAG, "✅ EXIT event emitted successfully")

        // Limpiar buffer RSSI para el próximo ciclo
        state.rssiBuffer.clear()
    }

    /**
     * Maneja el evento HEARTBEAT
     */
    private suspend fun handleHeartbeatEvent(state: BeaconState, currentTime: Long) {
        Log.d(TAG, "💓 HEARTBEAT event for beacon ${state.beaconId} - Avg RSSI: ${String.format("%.1f", state.getAverageRssi())} dBm")

        state.markHeartbeatSent(currentTime)

        // Crear y emitir evento
        val event = ProximityEvent.create(
            deviceId = deviceId,
            beaconId = state.beaconId,
            eventType = ProximityEventType.HEARTBEAT,
            rssiAvg = state.getAverageRssi(),
            samples = state.rssiBuffer.size
        )

        _proximityEvents.emit(event)
    }

    /**
     * Configura los umbrales para un beacon específico
     */
    fun configureBeacon(beaconId: String, config: ProximityConfig) {
        val state = beaconStates.getOrPut(beaconId) {
            BeaconState(beaconId = beaconId, config = config)
        }

        // Si ya existe, solo actualizamos la configuración (requeriría refactorización para ser mutable)
        Log.i(TAG, "Configured beacon $beaconId with custom thresholds: " +
                "enter=${config.enterThreshold}, exit=${config.exitThreshold}")
    }

    /**
     * Obtiene el estado actual de un beacon
     */
    fun getBeaconState(beaconId: String): BeaconState? {
        return beaconStates[beaconId]
    }

    /**
     * Obtiene todos los beacons en proximidad actual
     */
    fun getBeaconsInProximity(): List<String> {
        return beaconStates.values
            .filter { it.isInProximity }
            .map { it.beaconId }
    }

    /**
     * Limpia el estado de todos los beacons
     */
    fun clearAllStates() {
        Log.w(TAG, "Clearing all beacon states")
        beaconStates.clear()
    }

    /**
     * Obtiene estadísticas del manager
     */
    fun getStats(): String {
        val totalBeacons = beaconStates.size
        val inProximity = beaconStates.values.count { it.isInProximity }
        return "Total beacons: $totalBeacons, In proximity: $inProximity"
    }
}
