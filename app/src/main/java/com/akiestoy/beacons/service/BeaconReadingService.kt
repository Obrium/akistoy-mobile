package com.akiestoy.beacons.service

import android.content.Context
import android.os.Build
import android.util.Log
import com.akiestoy.beacons.api.ApiClient
import com.akiestoy.beacons.config.AppConfig
import com.akiestoy.beacons.data.AppDatabase
import com.akiestoy.beacons.data.FavoritesRepository
import com.akiestoy.beacons.model.BLEScanLog
import com.akiestoy.beacons.model.RegisteredBeacon
import com.akiestoy.beacons.model.api.BeaconReadingRequest
import com.akiestoy.beacons.model.api.Coordenadas
import com.akiestoy.beacons.model.user.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.pow

/**
 * Servicio para enviar lecturas de beacons al servidor periódicamente
 * El intervalo se configura en AppConfig.BEACON_READING_INTERVAL_MS
 */
class BeaconReadingService(
    private val context: Context,
    private val serviceScope: CoroutineScope,
    private val favoriteBeaconsFlow: StateFlow<List<BLEScanLog>>
) {

    private val TAG = "BeaconReadingService"
    private val database = AppDatabase.getDatabase(context)
    private val favoritesRepository = FavoritesRepository(context)
    private val beaconReadingApi = ApiClient.beaconReadingApi

    private var isRunning = false

    /**
     * Inicia el envío periódico de lecturas de beacons
     */
    fun startSending() {
        if (isRunning) {
            Log.w(TAG, "⚠️ BeaconReadingService ya está corriendo")
            return
        }

        isRunning = true
        val intervalSeconds = AppConfig.BEACON_READING_INTERVAL_MS / 1000
        Log.i(TAG, "🚀 Iniciando envío periódico de lecturas de beacons (cada $intervalSeconds segundos)")

        serviceScope.launch {
            while (isRunning) {
                try {
                    sendCurrentBeaconReading()
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error al enviar lectura de beacon", e)
                }

                // Esperar según configuración antes del próximo envío
                delay(AppConfig.BEACON_READING_INTERVAL_MS)
            }
        }
    }

    /**
     * Detiene el envío de lecturas
     */
    fun stopSending() {
        isRunning = false
        Log.i(TAG, "🛑 Deteniendo envío de lecturas de beacons")
    }

    /**
     * Envía la lectura del beacon activo actual
     */
    private suspend fun sendCurrentBeaconReading() {
        // Obtener el usuario actual
        val user = database.userDao().getCurrentUserOnce()
        if (user == null) {
            Log.d(TAG, "⏭️ No hay usuario autenticado, omitiendo envío")
            return
        }

        // Obtener el beacon favorito más cercano
        val favoriteBeacons = favoriteBeaconsFlow.value
        if (favoriteBeacons.isEmpty()) {
            Log.d(TAG, "⏭️ No hay beacons favoritos activos, omitiendo envío")
            return
        }

        // Tomar el beacon con mejor señal (más cercano)
        val closestBeacon = favoriteBeacons.maxByOrNull { it.rssi }
        if (closestBeacon == null || closestBeacon.iBeaconData == null) {
            Log.d(TAG, "⏭️ No hay beacon válido para enviar")
            return
        }

        // Buscar información del beacon registrado
        Log.i(TAG, "🔍 Buscando beacon en BD (BeaconReadingService):")
        Log.i(TAG, "   UUID: ${closestBeacon.iBeaconData.uuid}")
        Log.i(TAG, "   Major: ${closestBeacon.iBeaconData.major}")
        Log.i(TAG, "   Minor: ${closestBeacon.iBeaconData.minor}")
        
        val registeredBeacon = database.registeredBeaconDao().getBeaconByIdentifiers(
            uuid = closestBeacon.iBeaconData.uuid.lowercase(),
            major = closestBeacon.iBeaconData.major,
            minor = closestBeacon.iBeaconData.minor
        )

        if (registeredBeacon != null) {
            Log.i(TAG, "✅ Beacon encontrado: ID=${registeredBeacon.id}, Zona=${registeredBeacon.zoneName}")
        } else {
            Log.w(TAG, "⚠️ Beacon NO encontrado en BD. Se usará 'Desconocida' como zona")
            // Listar beacons en BD para debug
            val allBeacons = database.registeredBeaconDao().getAllBeaconsOnce()
            Log.w(TAG, "📋 Total beacons en BD: ${allBeacons.size}")
            allBeacons.take(5).forEach { b ->
                Log.d(TAG, "   - UUID=${b.advUuid}, Major=${b.major}, Minor=${b.minor}, Zona=${b.zoneName}")
            }
        }

        // Crear el request con los datos del beacon
        val reading = createBeaconReadingRequest(
            user = user,
            beacon = closestBeacon,
            registeredBeacon = registeredBeacon
        )

        // Enviar al servidor
        try {
            val response = beaconReadingApi.sendBeaconReading(reading)
            if (response.isSuccessful) {
                Log.i(TAG, "✅ Lectura enviada: Beacon=${reading.beaconName}, RSSI=${reading.rssi}, Distancia=${String.format("%.2f", reading.estimatedDistance)}m")
            } else {
                Log.e(TAG, "❌ Error al enviar lectura: ${response.code()} - ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Excepción al enviar lectura de beacon", e)
        }
    }

    /**
     * Crea el request de lectura de beacon con todos los datos necesarios
     */
    private fun createBeaconReadingRequest(
        user: User,
        beacon: BLEScanLog,
        registeredBeacon: RegisteredBeacon?
    ): BeaconReadingRequest {
        val iBeacon = beacon.iBeaconData!!

        // Calcular distancia estimada usando la fórmula de path loss
        val distance = calculateDistance(beacon.rssi, iBeacon.txPower)

        // Determinar nivel de proximidad
        val proximityLevel = when {
            distance < 1.0 -> "IMMEDIATE"
            distance < 3.0 -> "NEAR"
            distance < 10.0 -> "FAR"
            else -> "UNKNOWN"
        }

        // Obtener información del dispositivo
        val deviceManufacturer = Build.MANUFACTURER
        val deviceModel = Build.MODEL
        val deviceName = "$deviceManufacturer $deviceModel"
        val androidVersion = Build.VERSION.RELEASE

        return BeaconReadingRequest(
            userId = user.id,
            userName = user.name,
            userPhone = user.phone.ifEmpty { null },
            deviceId = user.deviceId,
            nombreDispositivo = deviceName,
            deviceManufacturer = deviceManufacturer,
            deviceModel = deviceModel,
            androidVersion = androidVersion,
            beaconMac = beacon.macAddress,
            beaconUuid = iBeacon.uuid,
            beaconMajor = iBeacon.major,
            beaconMinor = iBeacon.minor,
            beaconName = registeredBeacon?.zoneName ?: beacon.deviceName,
            beaconId = registeredBeacon?.id ?: iBeacon.uuid,
            txPower = iBeacon.txPower,
            rssi = beacon.rssi,
            estimatedDistance = distance,
            proximityLevel = proximityLevel,
            zona = registeredBeacon?.zoneName ?: "Desconocida",
            empresaId = user.tenantId,
            timestamp = System.currentTimeMillis(),
            eventType = "READING",
            coordenadas = null // TODO: Agregar coordenadas GPS si se necesitan
        )
    }

    /**
     * Calcula la distancia estimada en metros usando la fórmula de path loss
     */
    private fun calculateDistance(rssi: Int, txPower: Int): Double {
        if (rssi == 0) {
            return -1.0
        }

        val ratio = rssi * 1.0 / txPower
        return if (ratio < 1.0) {
            ratio.pow(10.0)
        } else {
            val accuracy = (0.89976) * ratio.pow(7.7095) + 0.111
            accuracy
        }
    }
}
