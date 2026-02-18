package com.akiestoy.beacons.worker

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.akiestoy.beacons.service.ProximityForegroundService

/**
 * Worker periódico que verifica si el servicio de proximidad está corriendo.
 * Si no está corriendo, lo reinicia.
 * WorkManager garantiza ejecución incluso después de muerte del proceso o Doze.
 */
class ServiceCheckWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    private val TAG = "ServiceCheckWorker"

    override fun doWork(): Result {
        return try {
            val isRunning = ProximityForegroundService.isServiceRunning(applicationContext)
            Log.i(TAG, "Service check: running=$isRunning")

            if (!isRunning) {
                Log.w(TAG, "Service not running! Restarting...")
                ProximityForegroundService.startService(applicationContext)
                Log.i(TAG, "Service restart requested")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking/restarting service", e)
            Result.retry()
        }
    }
}
