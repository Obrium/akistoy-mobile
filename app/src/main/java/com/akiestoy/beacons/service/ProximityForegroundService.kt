package com.akiestoy.beacons.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.akiestoy.beacons.MainActivity
import com.akiestoy.beacons.R
import com.akiestoy.beacons.api.ApiClient
import com.akiestoy.beacons.data.AppDatabase
import com.akiestoy.beacons.data.FavoritesRepository
import com.akiestoy.beacons.proximity.ProximityBeaconScanner
import com.akiestoy.beacons.tracking.BeaconTrackingService
import com.akiestoy.beacons.tracking.EventBatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Servicio en primer plano para escaneo de proximidad BLE
 * Integra ProximityBeaconScanner, BeaconProximityManager y ProximityEventSender
 */
class ProximityForegroundService : Service() {

    private val TAG = "ProximityForegroundService"

    private lateinit var trackingService: BeaconTrackingService
    private lateinit var proximityScanner: ProximityBeaconScanner
    private lateinit var favoritesRepository: FavoritesRepository

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    companion object {
        private const val CHANNEL_ID = "ProximityServiceChannel"
        private const val NOTIFICATION_ID = 1002

        fun startService(context: Context) {
            val intent = Intent(context, ProximityForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, ProximityForegroundService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "🚀 ProximityForegroundService created")

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Inicializando..."))

        // Inicializar componentes
        initializeComponents()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "▶️ Service started")

        // Iniciar escaneo
        startProximityScanning()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        Log.i(TAG, "🛑 Service destroyed")

        // Detener escaneo
        proximityScanner.stopScanning()

        // Limpiar tracking service
        trackingService.cleanup()

        // Cancelar coroutines
        serviceScope.cancel()

        Log.d(TAG, "Final state: ${trackingService.getStateInfo()}")

        super.onDestroy()
    }

    /**
     * Inicializa los componentes del servicio
     */
    @SuppressLint("HardwareIds")
    private fun initializeComponents() {
        // Obtener device ID
        val deviceId = try {
            Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        } catch (e: Exception) {
            "unknown-device"
        }

        // Obtener device name
        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"

        // Crear FavoritesRepository
        favoritesRepository = FavoritesRepository(this)

        // Obtener base de datos y DAO
        val database = AppDatabase.getDatabase(this)
        val pendingEventDao = database.pendingEventDao()

        // Crear EventBatcher con cola offline
        val eventBatcher = EventBatcher(
            api = ApiClient.proximityApi,
            pendingEventDao = pendingEventDao
        )

        // Crear BeaconTrackingService con máquina de estados
        trackingService = BeaconTrackingService(
            api = ApiClient.proximityApi,
            deviceId = deviceId,
            deviceName = deviceName,
            eventBatcher = eventBatcher
        )

        // Crear Scanner con callback al tracking service
        proximityScanner = ProximityBeaconScanner(
            context = this,
            onBeaconDetected = { macAddress, rssi ->
                // Solo procesar si está en favoritos
                if (favoritesRepository.isFavorite(macAddress)) {
                    serviceScope.launch {
                        // Enviar al tracking service
                        // El beacon ID es el MAC, la zona también es el MAC
                        trackingService.onBeaconDetected(
                            beaconId = macAddress,
                            zoneName = macAddress,
                            rssi = rssi
                        )
                    }
                }
            },
            isFavorite = { macAddress -> favoritesRepository.isFavorite(macAddress) }
        )

        // Log de beacons favoritos
        val favoritesCount = favoritesRepository.favorites.value.size
        Log.i(TAG, "📌 Monitoring $favoritesCount favorite beacon(s)")
        if (favoritesCount > 0) {
            favoritesRepository.favorites.value.forEach { mac ->
                Log.d(TAG, "   └─ Favorite: $mac")
            }
        } else {
            Log.w(TAG, "⚠️ No favorite beacons configured! Please add beacons to favorites first.")
        }

        // Observar cambios de estado
        serviceScope.launch {
            trackingService.currentState.collect { state ->
                Log.i(TAG, "🔄 State changed: $state")
                updateNotification(state.toString(), 0)
            }
        }

        Log.i(TAG, "✅ All components initialized successfully")
        Log.d(TAG, "Device ID: $deviceId")
        Log.d(TAG, "Device Name: $deviceName")
    }

    /**
     * Inicia el escaneo de proximidad
     */
    private fun startProximityScanning() {
        try {
            // Iniciar escaneo BLE
            proximityScanner.startScanning(serviceScope)

            // Iniciar verificación de señal en tracking service
            trackingService.startSignalCheck(serviceScope)

            updateNotification("Escaneando...", 0)
            Log.i(TAG, "🔍 Proximity scanning started in BALANCED mode")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting proximity scanning", e)
        }
    }

    /**
     * Crea el canal de notificación
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Servicio de Proximidad",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificaciones del servicio de proximidad BLE"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Crea la notificación para el servicio en primer plano
     */
    private fun createNotification(status: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AkiEstoy - Proximidad")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    /**
     * Actualiza la notificación con información del último evento
     */
    private fun updateNotification(lastEvent: String, beaconsInProximity: Int) {
        val status = "Último: $lastEvent | En proximidad: $beaconsInProximity"
        val notification = createNotification(status)

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
