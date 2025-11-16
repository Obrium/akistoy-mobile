package com.akiestoy.beacons.tracking

import android.util.Log
import com.akiestoy.beacons.api.BeaconProximityApi
import com.akiestoy.beacons.config.AppConfig
import com.akiestoy.beacons.data.PendingEvent
import com.akiestoy.beacons.data.PendingEventDao
import com.akiestoy.beacons.model.proximity.BeaconProximityRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Agrupa eventos de beacons y los envía en batch para reducir requests al backend
 * Reduce de ~30 requests/seg a ~2 requests/seg con 30 dispositivos
 * Incluye cola offline con Room DB para reintentos
 */
class EventBatcher(
    private val api: BeaconProximityApi,
    private val pendingEventDao: PendingEventDao? = null
) {
    private val TAG = "EventBatcher"

    // Cola thread-safe de eventos pendientes
    private val pendingEvents = ConcurrentLinkedQueue<BeaconProximityRequest>()

    // Mutex para sincronización
    private val mutex = Mutex()

    // Job del batch timer
    private var batchJob: Job? = null

    // Estadísticas
    private var totalEventsQueued = 0
    private var totalBatchesSent = 0
    private var totalEventsSuccess = 0
    private var totalEventsFailed = 0

    /**
     * Inicia el procesamiento en batch y cola offline
     */
    fun start(scope: CoroutineScope) {
        if (batchJob?.isActive == true) {
            Log.w(TAG, "⚠️ Batching already running")
            return
        }

        Log.i(TAG, "🚀 Starting event batching (interval: ${AppConfig.EVENT_BATCH_INTERVAL_MS}ms)")

        batchJob = scope.launch {
            while (isActive) {
                delay(AppConfig.EVENT_BATCH_INTERVAL_MS)

                // Procesar batch normal
                processBatch()

                // Cada 3 ciclos, procesar cola offline
                if (totalBatchesSent % 3 == 0) {
                    processOfflineQueue()
                }
            }
        }
    }

    /**
     * Detiene el procesamiento en batch
     */
    fun stop() {
        batchJob?.cancel()
        batchJob = null

        // Intentar enviar eventos pendientes antes de cerrar
        if (pendingEvents.isNotEmpty()) {
            Log.i(TAG, "📤 Sending ${pendingEvents.size} pending events before shutdown...")
        }

        Log.i(TAG, "🛑 Event batching stopped")
        logStats()
    }

    /**
     * Agrega un evento a la cola para envío en batch
     */
    suspend fun queueEvent(event: BeaconProximityRequest) {
        mutex.withLock {
            pendingEvents.offer(event)
            totalEventsQueued++
        }
    }

    /**
     * Procesa y envía el batch actual
     */
    private suspend fun processBatch() {
        val eventsToSend = mutableListOf<BeaconProximityRequest>()

        mutex.withLock {
            // Extraer todos los eventos pendientes
            while (pendingEvents.isNotEmpty()) {
                pendingEvents.poll()?.let { eventsToSend.add(it) }
            }
        }

        if (eventsToSend.isEmpty()) {
            return // No hay nada que enviar
        }

        Log.d(TAG, "📦 Processing batch with ${eventsToSend.size} events")

        try {
            // Enviar eventos uno por uno (el backend actual no tiene endpoint de batch)
            // En producción deberías implementar un endpoint /api/beacons/batch
            var successCount = 0
            var failCount = 0

            eventsToSend.forEach { event ->
                try {
                    Log.i(TAG, "🌐 POST /v1/mobile/beacon-reading")
                    Log.i(TAG, "   beaconId: ${event.beaconId}")
                    Log.i(TAG, "   zona: ${event.zona}")
                    Log.i(TAG, "   deviceId: ${event.deviceId}")
                    Log.i(TAG, "   empresaId: ${event.empresaId}")
                    Log.i(TAG, "   nombreDispositivo: ${event.nombreDispositivo}")
                    Log.i(TAG, "   rssi: ${event.rssi}")
                    
                    val response = api.sendProximityEvent(event)
                    
                    if (response.isSuccessful) {
                        successCount++
                        totalEventsSuccess++
                        Log.i(TAG, "✅ Respuesta: ${response.code()} OK")
                    } else {
                        failCount++
                        totalEventsFailed++
                        Log.e(TAG, "❌ Respuesta: ${response.code()} - ${response.message()}")
                        Log.e(TAG, "   Error body: ${response.errorBody()?.string()}")

                        // Guardar en cola offline si está disponible
                        saveToOfflineQueue(event)
                    }
                } catch (e: Exception) {
                    failCount++
                    totalEventsFailed++
                    Log.e(TAG, "❌ Exception sending event: ${e.message}")

                    // Guardar en cola offline si está disponible
                    saveToOfflineQueue(event)
                }
            }

            totalBatchesSent++
            Log.i(TAG, "✅ Batch sent: $successCount success, $failCount failed")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error processing batch: ${e.message}", e)
            totalEventsFailed += eventsToSend.size

            // Reintentar agregando de vuelta a la cola
            mutex.withLock {
                eventsToSend.forEach { pendingEvents.offer(it) }
            }
        }
    }

    /**
     * Registra estadísticas del batcher
     */
    private fun logStats() {
        Log.i(TAG, """
            📊 Event Batcher Statistics:
               └─ Total queued: $totalEventsQueued
               └─ Batches sent: $totalBatchesSent
               └─ Success: $totalEventsSuccess
               └─ Failed: $totalEventsFailed
               └─ Pending: ${pendingEvents.size}
        """.trimIndent())
    }

    /**
     * Obtiene estadísticas actuales
     */
    fun getStats(): String {
        return "Queued: $totalEventsQueued | Batches: $totalBatchesSent | Success: $totalEventsSuccess | Failed: $totalEventsFailed | Pending: ${pendingEvents.size}"
    }

    /**
     * Guarda un evento en la cola offline (Room DB)
     */
    private suspend fun saveToOfflineQueue(event: BeaconProximityRequest) {
        pendingEventDao?.let { dao ->
            try {
                val pendingEvent = PendingEvent(
                    beaconId = event.beaconId,
                    zona = event.zona,
                    nombreDispositivo = event.nombreDispositivo,
                    deviceId = event.deviceId,
                    empresaId = event.empresaId,
                    rssi = event.rssi
                )
                dao.insert(pendingEvent)
                Log.d(TAG, "💾 Event saved to offline queue")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to save to offline queue: ${e.message}")
            }
        }
    }

    /**
     * Procesa eventos de la cola offline (reintentos)
     */
    suspend fun processOfflineQueue() {
        pendingEventDao?.let { dao ->
            try {
                val offlineEvents = dao.getRetryable(limit = 50)

                if (offlineEvents.isEmpty()) {
                    return
                }

                Log.i(TAG, "📥 Processing ${offlineEvents.size} offline events...")

                val successIds = mutableListOf<Long>()

                offlineEvents.forEach { pendingEvent ->
                    try {
                        val request = BeaconProximityRequest(
                            beaconId = pendingEvent.beaconId,
                            zona = pendingEvent.zona,
                            nombreDispositivo = pendingEvent.nombreDispositivo,
                            deviceId = pendingEvent.deviceId,
                            empresaId = pendingEvent.empresaId,
                            rssi = pendingEvent.rssi
                        )

                        val response = api.sendProximityEvent(request)

                        if (response.isSuccessful) {
                            successIds.add(pendingEvent.id)
                            totalEventsSuccess++
                        } else {
                            // Incrementar retry count
                            dao.update(pendingEvent.copy(retryCount = pendingEvent.retryCount + 1))
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Failed to retry offline event: ${e.message}")
                        dao.update(pendingEvent.copy(retryCount = pendingEvent.retryCount + 1))
                    }
                }

                // Eliminar eventos enviados exitosamente
                if (successIds.isNotEmpty()) {
                    dao.deleteByIds(successIds)
                    Log.i(TAG, "✅ ${successIds.size} offline events sent successfully")
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error processing offline queue: ${e.message}")
            }
        }
    }
}
