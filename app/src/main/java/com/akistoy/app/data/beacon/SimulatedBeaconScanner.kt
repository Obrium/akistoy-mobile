package com.akistoy.app.data.beacon

import android.content.Context
import com.akistoy.app.domain.model.BeaconEvent
import com.akistoy.app.domain.model.BeaconProximity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.random.Random

/**
 * Scanner simulado para pruebas sin hardware BLE físico
 * Simula beacons que entran y salen del rango con comportamiento realista
 */
@Singleton
class SimulatedBeaconScanner @Inject constructor(
    @ApplicationContext private val context: Context
) : BeaconScanner {

    private val scope = CoroutineScope(Dispatchers.Default)
    private val _detections = MutableSharedFlow<BeaconEvent>(extraBufferCapacity = 64)
    override val detections: Flow<BeaconEvent> = _detections
        .shareIn(scope, SharingStarted.WhileSubscribed(5000), replay = 0)

    private var isScanning = false

    // Beacons simulados con diferentes comportamientos
    private val simulatedBeacons = listOf(
        SimulatedBeacon(
            id = "BEACON-ENTRADA-001",
            uuid = "e2c56db5-dffb-48d2-b060-d0f5a71096e0",
            minRssi = -50,
            maxRssi = -70,
            appearanceDelayMs = 2000L, // Aparece después de 2 segundos
            disappearanceDelayMs = 15000L // Desaparece después de 15 segundos
        ),
        SimulatedBeacon(
            id = "BEACON-OFICINA-002",
            uuid = "a1b2c3d4-e5f6-7890-1234-567890abcdef",
            minRssi = -60,
            maxRssi = -80,
            appearanceDelayMs = 5000L,
            disappearanceDelayMs = 20000L
        ),
        SimulatedBeacon(
            id = "BEACON-SALIDA-003",
            uuid = "11223344-5566-7788-99aa-bbccddeeff00",
            minRssi = -70,
            maxRssi = -90,
            appearanceDelayMs = 8000L,
            disappearanceDelayMs = 12000L // Se va más rápido
        )
    )

    override fun startScanning(uuids: List<String>) {
        if (isScanning) return
        isScanning = true

        // Simular cada beacon en su propio ciclo
        simulatedBeacons.forEach { beacon ->
            scope.launch {
                simulateBeaconBehavior(beacon)
            }
        }
    }

    private suspend fun simulateBeaconBehavior(beacon: SimulatedBeacon) {
        // Esperar antes de que aparezca el beacon
        delay(beacon.appearanceDelayMs)

        if (!isScanning) return

        // Fase 1: Beacon aparece (enviar señales continuamente)
        val appearanceDuration = beacon.disappearanceDelayMs - beacon.appearanceDelayMs
        val endTime = System.currentTimeMillis() + appearanceDuration

        while (isScanning && System.currentTimeMillis() < endTime) {
            // Generar RSSI variable (simula movimiento)
            // RSSI es negativo, maxRssi es más negativo que minRssi
            val rssi = Random.nextInt(beacon.maxRssi, beacon.minRssi + 1)
            val proximity = calculateProximity(rssi)
            val distance = calculateDistance(rssi)

            val event = BeaconEvent(
                beaconId = beacon.id,
                namespace = beacon.uuid,
                rssi = rssi,
                timestamp = Clock.System.now(),
                proximity = proximity,
                distanceMeters = distance
            )

            _detections.emit(event)

            // Emitir señal cada 1-2 segundos (comportamiento realista)
            delay(Random.nextLong(1000, 2000))
        }

        // Fase 2: Beacon desaparece (dejar de emitir)
        // El timeout de 5s en BeaconRepositoryImpl detectará la salida automáticamente
    }

    private fun calculateProximity(rssi: Int): BeaconProximity {
        return when {
            rssi >= -60 -> BeaconProximity.Immediate
            rssi in -80..-61 -> BeaconProximity.Near
            rssi < -80 -> BeaconProximity.Far
            else -> BeaconProximity.Unknown
        }
    }

    /**
     * Calcula la distancia en metros basándose en el RSSI
     * Fórmula: distance = 10 ^ ((txPower - rssi) / (10 * n))
     * txPower: -59 dBm (potencia típica de beacons BLE a 1 metro)
     * n: 2.0 (factor de propagación para ambientes abiertos)
     */
    private fun calculateDistance(rssi: Int): Double {
        val txPower = -59.0 // Potencia de transmisión a 1 metro
        val n = 2.0 // Factor de propagación

        if (rssi == 0) return -1.0 // Señal no válida

        val ratio = (txPower - rssi) / (10.0 * n)
        val distance = Math.pow(10.0, ratio)

        // Redondear a 2 decimales
        return (distance * 100).toInt() / 100.0
    }

    override fun stopScanning() {
        isScanning = false
    }

    private data class SimulatedBeacon(
        val id: String,
        val uuid: String,
        val minRssi: Int,
        val maxRssi: Int,
        val appearanceDelayMs: Long,
        val disappearanceDelayMs: Long
    )
}
