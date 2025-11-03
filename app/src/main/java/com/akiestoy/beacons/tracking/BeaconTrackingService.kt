package com.akiestoy.beacons.tracking

import android.util.Log
import com.akiestoy.beacons.api.BeaconProximityApi
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
 * Implementa lógica de entrada/salida implícita
 */
class BeaconTrackingService(
    private val api: BeaconProximityApi,
    private val deviceId: String,
    private val deviceName: String,
    private val tenantId: String = "550e8400-e29b-41d4-a716-446655440000" // Empresa ID por defecto
) {
    private val TAG = "BeaconTrackingService"

    // Estado actual de la máquina de estados
    private val _currentState = MutableStateFlow(BeaconState.OUTSIDE)
    val currentState: StateFlow<BeaconState> = _currentState.asStateFlow()

    // Último beacon detectado
    private var lastBeaconId: String? = null
    private var lastBeaconTimestamp: Long = 0L

    // Jobs para timers
    private var exitTimerJob: Job? = null
    private var heartbeatJob: Job? = null
    private var signalCheckJob: Job? = null

    // Constantes
    companion object {
        private const val EXIT_DELAY_MS = 120_000L         // 2 minutos
        private const val HEARTBEAT_INTERVAL_MS = 60_000L  // 60 segundos
        private const val SIGNAL_CHECK_INTERVAL_MS = 5_000L // 5 segundos
        private const val SIGNAL_LOST_THRESHOLD_MS = 5_000L // 5 segundos sin señal
    }

    /**
     * Inicia la verificación periódica de señal
     */
    fun startSignalCheck(scope: CoroutineScope) {
        signalCheckJob?.cancel()
        signalCheckJob = scope.launch {
            while (isActive) {
                delay(SIGNAL_CHECK_INTERVAL_MS)
                checkBeaconSignal()
            }
        }
        Log.i(TAG, "🔍 Signal check started (interval: ${SIGNAL_CHECK_INTERVAL_MS}ms)")
    }

    /**
     * Detiene la verificación de señal
     */
    fun stopSignalCheck() {
        signalCheckJob?.cancel()
        signalCheckJob = null
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

        Log.d(TAG, "📡 Beacon detected: $beaconId | State: ${_currentState.value} | RSSI: $rssi dBm")

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
        exitTimerJob = kotlinx.coroutines.GlobalScope.launch {
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
        heartbeatJob = kotlinx.coroutines.GlobalScope.launch {
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

            Log.d(TAG, "📤 Sending beacon-reading to backend")
            val response = api.sendProximityEvent(request)

            if (response.isSuccessful) {
                Log.v(TAG, "✅ Beacon reading sent successfully")
            } else {
                Log.e(TAG, "❌ Error sending beacon reading: ${response.code()} - ${response.message()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception sending beacon reading: ${e.message}", e)
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
