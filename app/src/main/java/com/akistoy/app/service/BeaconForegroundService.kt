package com.akistoy.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.akistoy.app.R
import com.akistoy.app.domain.usecase.StartScanningUseCase
import com.akistoy.app.domain.usecase.StopScanningUseCase
import com.akistoy.app.ui.MainActivityIntentFactory
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BeaconForegroundService : Service() {

    @Inject
    lateinit var startScanningUseCase: StartScanningUseCase

    @Inject
    lateinit var stopScanningUseCase: StopScanningUseCase

    @Inject
    lateinit var intentFactory: MainActivityIntentFactory

    private val scope = CoroutineScope(Dispatchers.Default)
    private var runningJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startForegroundService()
            ACTION_STOP -> stopForegroundService()
        }
        return START_STICKY
    }

    private fun startForegroundService() {
        if (runningJob != null) return

        // Adquirir wake lock para mantener el CPU activo
        acquireWakeLock()

        createNotificationChannel()
        val notification = buildNotification("Escaneando beacons...")
        startForeground(NOTIFICATION_ID, notification)

        // ESTABILIDAD: Wrappear en try-catch con auto-recovery
        runningJob = scope.launch {
            try {
                startScanningUseCase()
            } catch (e: Exception) {
                android.util.Log.e("BeaconService", "Error starting scan", e)

                // ESTABILIDAD: Auto-recovery después de 5 segundos
                kotlinx.coroutines.delay(5000)
                android.util.Log.i("BeaconService", "Attempting to restart scan...")

                try {
                    startScanningUseCase()
                } catch (retryError: Exception) {
                    android.util.Log.e("BeaconService", "Retry failed", retryError)
                    updateNotification("Error en escaneo - Reintentando...")

                    // Segundo retry después de 10 segundos
                    kotlinx.coroutines.delay(10000)
                    startScanningUseCase()
                }
            }
        }
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager

            // ESTABILIDAD: Reducir timeout a 3 minutos y renovar periódicamente
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Akistoy::BeaconServiceWakeLock"
            ).apply {
                acquire(3 * 60 * 1000L /*3 minutos*/)
            }

            // Renovar wake lock cada 2 minutos
            scope.launch {
                while (runningJob?.isActive == true) {
                    kotlinx.coroutines.delay(2 * 60 * 1000L)
                    if (wakeLock?.isHeld == false) {
                        android.util.Log.w("BeaconService", "WakeLock expired, re-acquiring")
                        wakeLock?.acquire(3 * 60 * 1000L)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("BeaconService", "Error acquiring WakeLock", e)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }

    private fun stopForegroundService() {
        runningJob?.cancel()
        runningJob = null
        releaseWakeLock()
        scope.launch { stopScanningUseCase() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(status: String = "Activo"): Notification {
        val intent = intentFactory.create(this)
        val pending = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(status)
            .setOngoing(true)
            .setContentIntent(pending)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    fun updateNotification(text: String) {
        val notification = buildNotification(text)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
            }
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        runningJob?.cancel()
        releaseWakeLock()
        scope.launch { stopScanningUseCase() }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // No detener el servicio cuando se cierra la app
        // El servicio seguirá corriendo en segundo plano
    }

    companion object {
        const val ACTION_START = "com.akistoy.app.service.action.START"
        const val ACTION_STOP = "com.akistoy.app.service.action.STOP"
        private const val CHANNEL_ID = "beacon_service"
        private const val NOTIFICATION_ID = 1001

        fun intent(context: Context, action: String): Intent =
            Intent(context, BeaconForegroundService::class.java).apply { this.action = action }
    }
}
