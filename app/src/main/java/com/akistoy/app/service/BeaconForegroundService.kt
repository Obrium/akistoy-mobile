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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startForegroundService()
            ACTION_STOP -> stopForegroundService()
        }
        return START_STICKY
    }

    private fun startForegroundService() {
        if (runningJob != null) return
        createNotificationChannel()
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
        runningJob = scope.launch {
            startScanningUseCase()
        }
    }

    private fun stopForegroundService() {
        runningJob?.cancel()
        runningJob = null
        scope.launch { stopScanningUseCase() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(): Notification {
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
            .setContentText(getString(R.string.notification_text))
            .setOngoing(true)
            .setContentIntent(pending)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
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
        scope.launch { stopScanningUseCase() }
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
