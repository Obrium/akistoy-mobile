package com.akiestoy.beacons

import android.app.Application
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.akiestoy.beacons.worker.ServiceCheckWorker
import java.util.concurrent.TimeUnit

class AkistoyApplication : Application() {

    private val TAG = "AkistoyApplication"

    override fun onCreate() {
        super.onCreate()

        // Capturar excepciones no manejadas para evitar que la app se cierre
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception in thread ${thread.name}", throwable)
            // Dejar que el handler default maneje el crash pero loguearlo
            defaultHandler?.uncaughtException(thread, throwable)
        }

        Log.i(TAG, "Application created - scheduling service watchdog")
        scheduleServiceWatchdog()
    }

    private fun scheduleServiceWatchdog() {
        try {
            val workRequest = PeriodicWorkRequestBuilder<ServiceCheckWorker>(
                15, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "service_watchdog",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )

            Log.i(TAG, "Service watchdog scheduled (every 15 min)")
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling watchdog", e)
        }
    }
}
