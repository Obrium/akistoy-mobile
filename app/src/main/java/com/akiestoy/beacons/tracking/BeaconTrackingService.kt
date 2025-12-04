package com.akiestoy.beacons.tracking

import android.util.Log
import com.akiestoy.beacons.api.BeaconProximityApi
import com.akiestoy.beacons.config.AppConfig
import com.akiestoy.beacons.model.proximity.BeaconProximityRequest
import com.akiestoy.beacons.model.tracking.BeaconState
import com.akiestoy.beacons.model.tracking.HeartbeatRequest
import com.akiestoy.beacons.model.tracking.ImplicitEventRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Servicio de tracking de beacons con máquina de estados
 * Implementa lógica de entrada/salida implícita + batching de eventos
 */
class BeaconTrackingService(
    private val api: BeaconProximityApi,
    private val deviceId: String,
    private val deviceName: String,
    private val eventBatcher: EventBatcher,
    private val tenantId: String = "550e8400-e29b-41d4-a716-446655440000" // Empresa ID por defecto
) {
    private val TAG = "BeaconTrackingService"

    // Estado actual de la máquina de estados
    private val _currentState = MutableStateFlow(BeaconState.OUTSIDE)
    val currentState: StateFlow<BeaconState> = _currentState.asStateFlow()

    // Último beacon detectado
    private var lastBeaconId: String? = null
    @Volatile
    private var lastBeaconTimestamp: Long = 0L

    // Jobs para timers
    private var exitTimerJob: Job? = null
    private var heartbeatJob: Job? = null
    private var signalCheckJob: Job? = null

    // Scope controlado para timers (evita GlobalScope memory leak)
    private var timerScope: CoroutineScope? = null

    // Control de logs (solo cada 5 segundos)
    private var lastLogTime = 0L
    private var beaconsDetectedSinceLastLog = 0

    // Constantes (ahora usando AppConfig)
    companion object {
        private val EXIT_DELAY_MS = AppConfig.EXIT_DELAY_MS
        private val HEARTBEAT_INTERVAL_MS = AppConfig.HEARTBEAT_INTERVAL_MS
        private val SIGNAL_CHECK_INTERVAL_MS = AppConfig.SIGNAL_CHECK_INTERVAL_MS // Usa variable de entorno
        private val SIGNAL_LOST_THRESHOLD_MS = AppConfig.SIGNAL_LOST_THRESHOLD_MS
        private val LOG_INTERVAL_MS = AppConfig.LOG_INTERVAL_MS
    }

    /**
     * Inicia la verificación periódica de señal y el batching de eventos
     * Verifica cada X segundos si hay señal de beacons (configurado en .env)
     */
    fun startSignalCheck(scope: CoroutineScope) {
        // Guardar scope para usar en timers (evita GlobalScope)
        timerScope = scope

        signalCheckJob?.cancel()
        signalCheckJob = scope.launch {
            while (isActive) {
                delay(SIGNAL_CHECK_INTERVAL_MS)
                checkBeaconSignal()
            }
        }
        Log.i(TAG, "🔍 Signal check started (verificando cada ${SIGNAL_CHECK_INTERVAL_MS/1000}s)")

        // Iniciar event batcher
        eventBatcher.start(scope)
    }

    /**
     * Detiene la verificación de señal y el batching
     */
    fun stopSignalCheck() {
        signalCheckJob?.cancel()
        signalCheckJob = null
        eventBatcher.stop()
        Log.i(TAG, "🛑 Signal check stopped")
    }

    /**
     * Procesa la detección de un beacon
     * Este método debe ser llamado cada vez que se detecta un beacon
     */
    suspend fun onBeaconDetected(beaconId: String, zoneName: String, rssi: Int) {
        val now = System.currentTimeMillis()
        lastBeaconId = beaconId
        lastBeaconTimestamp = now
        beaconsDetectedSinceLastLog++

        // Log solo cada 5 segundos para no saturar la consola
        if (now - lastLogTime >= LOG_INTERVAL_MS) {
            Log.d(TAG, "📡 Beacon tracking: $beaconsDetectedSinceLastLog detections in 5s | State: ${_currentState.value} | Latest: $beaconId (RSSI: $rssi dBm)")
            lastLogTime = now
            beaconsDetectedSinceLastLog = 0
        }

        // SIEMPRE enviar beacon-reading al backend
        sendBeaconReading(beaconId, zoneName, rssi, now)

        // Procesar según el estado actual
        when (_currentState.value) {
            BeaconState.OUTSIDE -> handleOutsideState(beaconId, now)
            BeaconState.ENTERING -> handleEnteringState(beaconId, now)
            BeaconState.INSIDE -> handleInsideState(beaconId, now)
            BeaconState.EXITING -> handleExitingState(beaconId, now)
        }
    }

    /**
     * Verifica si se ha perdido la señal de beacons
     */
    private fun checkBeaconSignal() {
        val now = System.currentTimeMillis()
        val timeSinceLastBeacon = now - lastBeaconTimestamp

        if (_currentState.value == BeaconState.INSIDE && timeSinceLastBeacon > SIGNAL_LOST_THRESHOLD_MS) {
            // Perdió señal mientras estaba dentro
            Log.w(TAG, "📵 Signal lost! Last beacon: ${timeSinceLastBeacon}ms ago")
            transitionToExiting()
        }
    }

    // ========== Manejadores de Estados ==========

    private suspend fun handleOutsideState(beaconId: String, timestamp: Long) {
        // Primera detección de beacon
        Log.i(TAG, "🚶 Estado: OUTSIDE → ENTERING (primer beacon detectado)")
        _currentState.value = BeaconState.ENTERING
    }

    private suspend fun handleEnteringState(beaconId: String, timestamp: Long) {
        // Detectó un segundo beacon, confirma entrada
        Log.i(TAG, "🚪 Estado: ENTERING → INSIDE (entrada confirmada)")
        _currentState.value = BeaconState.INSIDE

        // Enviar evento de entrada implícita
        sendImplicitEntry(beaconId, timestamp)

        // Iniciar heartbeat
        startHeartbeat()
    }

    private suspend fun handleInsideState(beaconId: String, timestamp: Long) {
        // Ya está dentro, solo actualiza beacon
        cancelExitTimer()
        Log.v(TAG, "✅ Estado: INSIDE (beacon actualizado)")
    }

    private suspend fun handleExitingState(beaconId: String, timestamp: Long) {
        // Detectó beacon mientras estaba en proceso de salida
        // Cancela el timer de salida
        cancelExitTimer()
        _currentState.value = BeaconState.INSIDE
        Log.i(TAG, "↩️ Estado: EXITING → INSIDE (salida cancelada, beacon detectado)")
    }

    // ========== Transiciones de Estado ==========

    private fun transitionToExiting() {
        _currentState.value = BeaconState.EXITING
        Log.i(TAG, "🚶 Estado: INSIDE → EXITING (señal perdida)")
        startExitTimer()
    }

    // ========== Timers ==========

    private fun startExitTimer() {
        exitTimerJob?.cancel()
        // Usar timerScope en lugar de GlobalScope para evitar memory leaks
        val scope = timerScope ?: return
        exitTimerJob = scope.launch {
            Log.d(TAG, "⏱️ Exit timer started (${EXIT_DELAY_MS / 1000}s)")
            delay(EXIT_DELAY_MS)

            // Timer expiró, confirmar salida
            Log.i(TAG, "🚪 Estado: EXITING → OUTSIDE (salida confirmada, timer expirado)")
            _currentState.value = BeaconState.OUTSIDE

            // Enviar evento de salida implícita
            sendImplicitExit()

            // Detener heartbeat
            stopHeartbeat()
        }
    }

    private fun cancelExitTimer() {
        exitTimerJob?.cancel()
        exitTimerJob = null
        Log.v(TAG, "⏱️ Exit timer cancelled")
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        // Usar timerScope en lugar de GlobalScope para evitar memory leaks
        val scope = timerScope ?: return
        heartbeatJob = scope.launch {
            Log.i(TAG, "💓 Heartbeat started (interval: ${HEARTBEAT_INTERVAL_MS / 1000}s)")
            while (isActive && _currentState.value == BeaconState.INSIDE) {
                delay(HEARTBEAT_INTERVAL_MS)
                if (_currentState.value == BeaconState.INSIDE) {
                    sendHeartbeat()
                }
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        Log.i(TAG, "💔 Heartbeat stopped")
    }

    // ========== API Calls ==========

    private suspend fun sendBeaconReading(beaconId: String, zoneName: String, rssi: Int, timestamp: Long) {
        try {
            val request = BeaconProximityRequest(
                beaconId = beaconId,
                zona = zoneName,
                nombreDispositivo = deviceName,
                deviceId = deviceId,
                empresaId = tenantId,
                rssi = rssi
            )

            Log.i(TAG, "📤 ENVIANDO BEACON READING:")
            Log.i(TAG, "   └─ beaconId: $beaconId")
            Log.i(TAG, "   └─ zona: $zoneName")
            Log.i(TAG, "   └─ deviceId: $deviceId")
            Log.i(TAG, "   └─ empresaId: $tenantId")
            Log.i(TAG, "   └─ nombreDispositivo: $deviceName")
            Log.i(TAG, "   └─ rssi: $rssi")

            // Usar batching en lugar de enviar inmediatamente
            eventBatcher.queueEvent(request)
            Log.v(TAG, "📥 Beacon reading queued for batch sending")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception queueing beacon reading: ${e.message}", e)
        }
    }

    private suspend fun sendImplicitEntry(beaconId: String, timestamp: Long) {
        try {
            val request = ImplicitEventRequest(
                deviceId = deviceId,
                eventType = "IMPLICIT_ENTRY",
                timestamp = timestamp,
                tenantId = tenantId,
                lastBeaconId = beaconId
            )

            Log.i(TAG, "📥 Sending IMPLICIT_ENTRY event")
            val response = api.sendImplicitEvent(request)

            if (response.isSuccessful) {
                Log.i(TAG, "✅ IMPLICIT_ENTRY sent successfully")
            } else {
                Log.e(TAG, "❌ Error sending IMPLICIT_ENTRY: ${response.code()} - ${response.message()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception sending IMPLICIT_ENTRY: ${e.message}", e)
        }
    }

    private suspend fun sendImplicitExit() {
        try {
            val request = ImplicitEventRequest(
                deviceId = deviceId,
                eventType = "IMPLICIT_EXIT",
                timestamp = System.currentTimeMillis(),
                tenantId = tenantId,
                lastBeaconId = lastBeaconId
            )

            Log.i(TAG, "📤 Sending IMPLICIT_EXIT event")
            val response = api.sendImplicitEvent(request)

            if (response.isSuccessful) {
                Log.i(TAG, "✅ IMPLICIT_EXIT sent successfully")
            } else {
                Log.e(TAG, "❌ Error sending IMPLICIT_EXIT: ${response.code()} - ${response.message()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception sending IMPLICIT_EXIT: ${e.message}", e)
        }
    }

    private suspend fun sendHeartbeat() {
        try {
            val request = HeartbeatRequest(
                deviceId = deviceId,
                timestamp = System.currentTimeMillis(),
                tenantId = tenantId
            )

            Log.d(TAG, "💓 Sending heartbeat")
            val response = api.sendHeartbeat(request)

            if (response.isSuccessful) {
                Log.v(TAG, "✅ Heartbeat sent successfully")
            } else {
                Log.e(TAG, "❌ Error sending heartbeat: ${response.code()} - ${response.message()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception sending heartbeat: ${e.message}", e)
        }
    }

    /**
     * Limpia todos los recursos
     */
    fun cleanup() {
        cancelExitTimer()
        stopHeartbeat()
        stopSignalCheck()
        timerScope = null  // Liberar referencia al scope
        Log.i(TAG, "🧹 Cleanup completed")
    }

    /**
     * Obtiene información del estado actual
     */
    fun getStateInfo(): String {
        return buildString {
            append("Estado: ${_currentState.value}\n")
            append("Último beacon: ${lastBeaconId ?: "N/A"}\n")
            val timeSinceLast = if (lastBeaconTimestamp > 0) {
                "${(System.currentTimeMillis() - lastBeaconTimestamp) / 1000}s"
            } else "N/A"
            append("Tiempo desde último: $timeSinceLast\n")
            append("Heartbeat activo: ${heartbeatJob?.isActive == true}\n")
            append("Exit timer activo: ${exitTimerJob?.isActive == true}")
        }
    }
}
