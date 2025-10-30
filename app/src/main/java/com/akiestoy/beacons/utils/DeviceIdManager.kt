package com.akiestoy.beacons.utils

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import java.util.UUID

/**
 * Manager para obtener un ID único y persistente del dispositivo
 */
object DeviceIdManager {
    private const val PREFS_NAME = "device_id_prefs"
    private const val KEY_DEVICE_UUID = "device_uuid"

    /**
     * Obtiene un ID único del dispositivo que persiste entre instalaciones
     * usando SharedPreferences
     */
    fun getDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Intentar obtener el UUID guardado
        var deviceId = prefs.getString(KEY_DEVICE_UUID, null)

        if (deviceId == null) {
            // Si no existe, generarlo y guardarlo
            deviceId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_UUID, deviceId).apply()
        }

        return deviceId
    }

    /**
     * Obtiene el Android ID del sistema (cambia con factory reset)
     */
    @SuppressLint("HardwareIds")
    fun getAndroidId(context: Context): String {
        return try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (e: Exception) {
            "unknown"
        }
    }

    /**
     * Obtiene un ID combinado que incluye ambos
     * Formato: "uuid_androidId"
     */
    fun getCombinedId(context: Context): String {
        return "${getDeviceId(context)}_${getAndroidId(context)}"
    }
}
