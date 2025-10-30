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
import com.akiestoy.beacons.api.ProximityEventSender
import com.akiestoy.beacons.proximity.BeaconProximityManager
import com.akiestoy.beacons.proximity.ProximityBeaconScanner
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

    private lateinit var proximityManager: BeaconProximityManager
    private lateinit var proximityScanner: ProximityBeaconScanner
    private lateinit var eventSender: ProximityEventSender

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

        // Cancelar coroutines
        serviceScope.cancel()

        Log.d(TAG, "Final stats: ${proximityManager.getStats()}")

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

        // Crear ProximityManager
        proximityManager = BeaconProximityManager(deviceId)

        // Crear Scanner
        proximityScanner = ProximityBeaconScanner(this, proximityManager)

        // Crear EventSender
        eventSender = ProximityEventSender(this)

        // Conectar proximityManager con eventSender
        serviceScope.launch {
            proximityManager.proximityEvents.collect { event ->
                Log.i(TAG, "📨 Proximity event received: ${event.event.uppercase()} for beacon ${event.beaconId}")

                // Enviar al backend
                eventSender.sendEvent(event, serviceScope)

                // Actualizar notificación
                updateNotification(event.event, proximityManager.getBeaconsInProximity().size)
            }
        }

        Log.i(TAG, "✅ All components initialized successfully")
        Log.d(TAG, "Device ID: $deviceId")
    }

    /**
     * Inicia el escaneo de proximidad
     */
    private fun startProximityScanning() {
        try {
            proximityScanner.startScanning(serviceScope)
            updateNotification("Escaneando...", 0)
            Log.i(TAG, "🔍 Proximity scanning started in LOW_LATENCY mode")
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
