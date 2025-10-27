package com.akistoy.app.data.repository

import com.akistoy.app.core.util.AppDispatchers
import com.akistoy.app.data.beacon.BeaconScanner
import com.akistoy.app.domain.model.BeaconEvent
import com.akistoy.app.domain.model.BeaconEventType
import com.akistoy.app.domain.repo.BeaconRepository
import com.akistoy.app.domain.repo.ConfigRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Singleton
class BeaconRepositoryImpl @Inject constructor(
    private val scanner: BeaconScanner,
    private val configRepository: ConfigRepository,
    private val dispatchers: AppDispatchers
) : BeaconRepository {

    private val scope = CoroutineScope(dispatchers.io + SupervisorJob())
    private val _detections = MutableStateFlow<List<BeaconEvent>>(emptyList())
    override val detections: Flow<List<BeaconEvent>> = _detections.asStateFlow()

    // Mapa de beaconId → nombre de zona
    private var beaconToZoneMap = mapOf<String, String>()

    // Tracking de beacons activos con sus timeouts
    private data class BeaconState(
        val lastSeen: Instant,
        val isActive: Boolean,
        val timeoutJob: Job?
    )

    // ESTABILIDAD: Usar LinkedHashMap para implementar LRU cache
    // Previene memory leak limitando a 100 beacons tracked
    private val beaconStates = object : LinkedHashMap<String, BeaconState>(
        100,  // Initial capacity
        0.75f,  // Load factor
        true  // Access order (LRU)
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, BeaconState>?): Boolean {
            // Cancelar job del beacon más viejo antes de removerlo
            if (size > MAX_TRACKED_BEACONS) {
                eldest?.value?.timeoutJob?.cancel()
                return true
            }
            return false
        }
    }

    init {
        scope.launch {
            // Cargar zonas para mapear beaconId → nombre de zona
            loadZoneMapping()

            // Comenzar a escuchar detecciones de beacons
            scanner.detections.collect { rawEvent ->
                handleBeaconDetection(rawEvent)
            }
        }
    }

    private suspend fun loadZoneMapping() {
        configRepository.fetchRemoteConfig().onSuccess { zones ->
            // Crear mapa: cada beaconId se asocia con el nombre de la zona que lo contiene
            beaconToZoneMap = zones.flatMap { zone ->
                zone.beaconUuids.map { beaconId -> beaconId to zone.name }
            }.toMap()
        }
    }

    private suspend fun handleBeaconDetection(rawEvent: BeaconEvent) {
        val key = "${rawEvent.beaconId}-${rawEvent.namespace ?: ""}"
        val now = Clock.System.now()

        // Obtener el nombre de la zona para este beacon
        val zoneName = beaconToZoneMap[rawEvent.beaconId] ?: rawEvent.beaconId

        val currentState = beaconStates[key]

        // Si es la primera vez que vemos este beacon o ya había salido
        if (currentState == null || !currentState.isActive) {
            // Evento de ENTRADA
            val entryEvent = rawEvent.copy(
                eventType = BeaconEventType.ENTRY,
                zoneName = zoneName
            )
            emitEvent(entryEvent)

            // Cancelar job de timeout anterior si existe
            currentState?.timeoutJob?.cancel()

            // Crear nuevo timeout job
            val timeoutJob = scope.launch {
                delay(EXIT_TIMEOUT_SECONDS.seconds)
                handleBeaconExit(key, rawEvent, zoneName)
            }

            beaconStates[key] = BeaconState(
                lastSeen = now,
                isActive = true,
                timeoutJob = timeoutJob
            )
        } else {
            // Beacon ya estaba activo, actualizar último visto
            // Solo emitir si pasó la ventana de deduplicación
            val timeSinceLastSeen = now.toEpochMilliseconds() - currentState.lastSeen.toEpochMilliseconds()

            if (timeSinceLastSeen > DEDUP_WINDOW_MS) {
                // Emitir evento de detección continua (no se envía al backend por defecto)
                val detectionEvent = rawEvent.copy(
                    eventType = BeaconEventType.DETECTION,
                    zoneName = zoneName
                )
                emitEvent(detectionEvent)
            }

            // Cancelar timeout anterior y crear uno nuevo
            currentState.timeoutJob?.cancel()
            val newTimeoutJob = scope.launch {
                delay(EXIT_TIMEOUT_SECONDS.seconds)
                handleBeaconExit(key, rawEvent, zoneName)
            }

            beaconStates[key] = currentState.copy(
                lastSeen = now,
                timeoutJob = newTimeoutJob
            )
        }
    }

    private suspend fun handleBeaconExit(key: String, lastEvent: BeaconEvent, zoneName: String) {
        val state = beaconStates[key] ?: return

        if (state.isActive) {
            // Emitir evento de SALIDA
            val exitEvent = lastEvent.copy(
                eventType = BeaconEventType.EXIT,
                timestamp = Clock.System.now(),
                rssi = lastEvent.rssi,  // Mantener el último RSSI conocido
                zoneName = zoneName
            )
            emitEvent(exitEvent)

            // Marcar como inactivo pero mantener en el mapa por si vuelve
            beaconStates[key] = state.copy(
                isActive = false,
                timeoutJob = null
            )
        }
    }

    private fun emitEvent(event: BeaconEvent) {
        val updated = (_detections.value + event).takeLast(MAX_BUFFER_SIZE)
        _detections.value = updated
    }

    override suspend fun startScanning(uuids: List<String>) {
        withContext(dispatchers.io) {
            scanner.startScanning(uuids)
        }
    }

    override suspend fun stopScanning() {
        withContext(dispatchers.io) {
            // Cancelar todos los timeouts
            beaconStates.values.forEach { it.timeoutJob?.cancel() }
            beaconStates.clear()
            scanner.stopScanning()
        }
    }

    companion object {
        private const val MAX_BUFFER_SIZE = 50
        private const val DEDUP_WINDOW_MS = 3_000L  // Reducido a 3 segundos
        private const val EXIT_TIMEOUT_SECONDS = 5L  // Considerar salida después de 5 segundos sin señal
        private const val MAX_TRACKED_BEACONS = 100  // Límite LRU para prevenir memory leak
    }
}
