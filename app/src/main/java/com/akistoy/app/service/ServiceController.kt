package com.akistoy.app.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.akistoy.app.data.repository.UserPreferencesDataSource
import com.akistoy.app.worker.MarkSyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServiceController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: UserPreferencesDataSource
) {

    suspend fun startService() {
        preferences.setServiceEnabled(true)
        val intent = Intent(context, BeaconForegroundService::class.java).apply {
            action = BeaconForegroundService.ACTION_START
        }
        ContextCompat.startForegroundService(context, intent)
        MarkSyncWorker.schedule(context)
    }

    suspend fun stopService() {
        preferences.setServiceEnabled(false)
        val intent = Intent(context, BeaconForegroundService::class.java).apply {
            action = BeaconForegroundService.ACTION_STOP
        }
        context.startService(intent)
    }

    suspend fun shouldStartService(): Boolean = preferences.isServiceEnabled()
}
