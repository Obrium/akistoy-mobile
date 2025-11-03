package com.akiestoy.beacons.scanner

import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.util.Log
import com.akiestoy.beacons.model.BeaconDetection
import com.akiestoy.beacons.model.BeaconLocation
import com.akiestoy.beacons.model.ProximityZone
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.altbeacon.beacon.Beacon
import org.altbeacon.beacon.BeaconConsumer
import org.altbeacon.beacon.BeaconManager
import org.altbeacon.beacon.BeaconParser
import org.altbeacon.beacon.Identifier
import org.altbeacon.beacon.RangeNotifier
import org.altbeacon.beacon.Region

/**
 * Scanner de beacons iBeacon usando la librería AltBeacon
 * Configurado específicamente para detectar los beacons ESP32 con UUID:
 * e2c56db5-dffb-48d2-b060-d0f5a71096e0
 *
 * Beacons configurados:
 * - BeaconA (Baño): Major=100, Minor=1, MAC=38:18:2b:b3:80:34
 * - BeaconB (Sala): Major=101, Minor=1, MAC=94:54:c5:2e:94:ec
 */
class BeaconScanner(private val context: Context) : BeaconConsumer {

    private val beaconManager: BeaconManager = BeaconManager.getInstanceForApplication(context)
    private var isScanning = false

    companion object {
        private const val TAG = "BeaconScanner"
        private const val AKIESTOY_UUID = "e2c56db5-dffb-48d2-b060-d0f5a71096e0"
        private const val REGION_ID = "akiestoy-beacons"

        // Layout para iBeacon (formato Apple)
        private const val IBEACON_LAYOUT = "m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"
    }

    init {
        // Configurar múltiples parsers para detectar diferentes formatos de beacons
        beaconManager.beaconParsers.clear()

        // Parser 1: iBeacon estándar (Apple)
        beaconManager.beaconParsers.add(
            BeaconParser()
                .setBeaconLayout(IBEACON_LAYOUT)
        )

        // Parser 2: iBeacon sin validación estricta del prefijo
        // Busca directamente el UUID en los datos
        beaconManager.beaconParsers.add(
            BeaconParser()
                .setBeaconLayout("m:0-3=4c000215,i:4-19,i:20-21,i:22-23,p:24-24")
        )

        // Parser 3: Formato genérico ESP32 (sin manufacturer prefix estricto)
        beaconManager.beaconParsers.add(
            BeaconParser()
                .setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24,d:25-25")
        )

        // Configurar intervalos de escaneo más agresivos
        beaconManager.foregroundScanPeriod = 1100L  // Tiempo de escaneo
        beaconManager.foregroundBetweenScanPeriod = 0L  // Sin pausa entre escaneos

        // Configuraciones para background
        beaconManager.backgroundScanPeriod = 1100L
        beaconManager.backgroundBetweenScanPeriod = 1100L

        Log.d(TAG, "BeaconScanner initialized with multiple parsers")
    }

    /**
     * Inicia el escaneo de beacons y retorna un Flow con las detecciones
     */
    fun startScanning(): Flow<List<BeaconDetection>> = callbackFlow {
        if (isScanning) {
            Log.w(TAG, "Scanning already in progress")
            close()
            return@callbackFlow
        }

        Log.d(TAG, "Starting beacon scanning...")
        isScanning = true

        // Crear la región para monitorear todos los beacons con nuestro UUID
        val region = Region(
            REGION_ID,
            Identifier.parse(AKIESTOY_UUID),
            null,  // No filtrar por Major
            null   // No filtrar por Minor
        )

        // Configurar el notificador de ranging
        val rangeNotifier = RangeNotifier { beacons, region ->
            // Log solo si hay beacons detectados
            if (beacons.isNotEmpty()) {
                val detections = beacons.map { beacon ->
                    convertBeaconToDetection(beacon)
                }
                trySend(detections)
            } else {
                trySend(emptyList())
            }
        }

        beaconManager.addRangeNotifier(rangeNotifier)

        // Vincular al servicio de beacons
        beaconManager.bind(this@BeaconScanner)

        awaitClose {
            Log.d(TAG, "Stopping beacon scanning...")
            beaconManager.removeRangeNotifier(rangeNotifier)
            beaconManager.stopRangingBeacons(region)
            beaconManager.unbind(this@BeaconScanner)
            isScanning = false
        }
    }

    /**
     * Convierte un objeto Beacon de AltBeacon a nuestro modelo BeaconDetection
     */
    private fun convertBeaconToDetection(beacon: Beacon): BeaconDetection {
        val major = beacon.id2.toInt()
        val minor = beacon.id3.toInt()
        val location = BeaconLocation.fromMajor(major)
        val proximity = ProximityZone.fromDistance(beacon.distance)

        return BeaconDetection(
            uuid = beacon.id1.toString(),
            major = major,
            minor = minor,
            rssi = beacon.rssi,
            txPower = beacon.txPower,
            distance = beacon.distance,
            location = location,
            proximity = proximity,
            macAddress = beacon.bluetoothAddress
        )
    }

    /**
     * Callback cuando el servicio de beacons se conecta
     */
    override fun onBeaconServiceConnect() {
        Log.d(TAG, "Beacon service connected")

        val region = Region(
            REGION_ID,
            Identifier.parse(AKIESTOY_UUID),
            null,
            null
        )

        try {
            beaconManager.startRangingBeacons(region)
            Log.d(TAG, "Started ranging beacons in region: $REGION_ID")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting ranging: ${e.message}", e)
        }
    }

    override fun getApplicationContext(): Context = context

    override fun unbindService(connection: ServiceConnection) {
        context.unbindService(connection)
    }

    override fun bindService(intent: Intent, connection: ServiceConnection, mode: Int): Boolean {
        return context.bindService(intent, connection, mode)
    }

    /**
     * Verifica si el escaneo está activo
     */
    fun isScanning(): Boolean = isScanning
}
