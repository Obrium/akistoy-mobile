package com.akiestoy.beacons.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.akiestoy.beacons.service.ProximityForegroundService

/**
 * BroadcastReceiver que reinicia el servicio de proximidad en eventos clave
 */
class ServiceRestartReceiver : BroadcastReceiver() {

    private val TAG = "ServiceRestartReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "📡 Received broadcast: ${intent.action}")

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.i(TAG, "🔄 Device booted - restarting proximity service")
                ProximityForegroundService.startService(context)
            }
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Log.i(TAG, "🔄 App updated - restarting proximity service")
                ProximityForegroundService.startService(context)
            }
            "com.akiestoy.beacons.RESTART_SERVICE" -> {
                Log.i(TAG, "🔄 Manual restart requested")
                ProximityForegroundService.startService(context)
            }
            else -> {
                Log.w(TAG, "⚠️ Unknown action: ${intent.action}")
            }
        }
    }
}
