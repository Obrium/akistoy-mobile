package com.akiestoy.beacons.api

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.akiestoy.beacons.model.proximity.BeaconProximityRequest
import com.akiestoy.beacons.model.proximity.ProximityEvent
import com.akiestoy.beacons.utils.DeviceIdManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Servicio para enviar eventos de proximidad al backend
 * Convierte ProximityEvent a BeaconProximityRequest y lo envía vía REST API
 */
class ProximityEventSender(
    private val context: Context,
    private val api: BeaconProximityApi = ApiClient.proximityApi
) {
    private val TAG = "ProximityEventSender"

    // Obtener nombre del dispositivo
    private fun getDeviceName(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL} (${getDeviceId()})"
    }

    /**
     * Envía un evento de proximidad al backend
     */
    fun sendEvent(event: ProximityEvent, scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            try {
                // Convertir evento a formato del backend
                val request = BeaconProximityRequest.fromProximityEvent(
                    event,
                    getDeviceName(),
                    getDeviceId()
                )

                Log.d(TAG, "📤 Sending ${event.event.uppercase()} event to backend:")
                Log.d(TAG, "   └─ BeaconId: ${request.beaconId}")
                Log.d(TAG, "   └─ Zona (MAC): ${request.zona}")
                Log.d(TAG, "   └─ Device: ${request.nombreDispositivo}")
                Log.d(TAG, "   └─ DeviceId: ${request.deviceId}")
                Log.d(TAG, "   └─ EmpresaId: ${request.empresaId}")
                Log.d(TAG, "   └─ RSSI: ${request.rssi} dBm")

                // Enviar al backend
                val response = api.sendProximityEvent(request)

                if (response.isSuccessful) {
                    val body = response.body()
                    Log.i(TAG, "✅ Event sent successfully: ${body?.message ?: "OK"}")
                } else {
                    Log.e(TAG, "❌ Error sending event. HTTP ${response.code()}: ${response.message()}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception sending event to backend: ${e.message}", e)
            }
        }
    }

    /**
     * Envía múltiples eventos en batch
     */
    suspend fun sendEventsBatch(events: List<ProximityEvent>): Result<Int> {
        return withContext(Dispatchers.IO) {
            try {
                var successCount = 0

                events.forEach { event ->
                    val request = BeaconProximityRequest.fromProximityEvent(
                        event,
                        getDeviceName(),
                        getDeviceId()
                    )

                    try {
                        val response = api.sendProximityEvent(request)
                        if (response.isSuccessful) {
                            successCount++
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error sending individual event", e)
                    }
                }

                Result.success(successCount)

            } catch (e: Exception) {
                Log.e(TAG, "Error sending batch events", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Obtiene el ID único del dispositivo (UUID persistente)
     */
    private fun getDeviceId(): String {
        return DeviceIdManager.getDeviceId(context)
    }

    /**
     * Obtiene el Android ID del sistema
     */
    @SuppressLint("HardwareIds")
    private fun getAndroidId(): String {
        return DeviceIdManager.getAndroidId(context)
    }
}
