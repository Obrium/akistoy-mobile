package com.akiestoy.beacons.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * Helper para manejar optimizaciones de batería específicas por fabricante
 * Basado en: https://dontkillmyapp.com/
 */
object BatteryOptimizationHelper {

    private const val TAG = "BatteryOptimizationHelper"

    data class ManufacturerInfo(
        val name: String,
        val instructions: String,
        val hasSpecialSettings: Boolean
    )

    fun getManufacturerInfo(): ManufacturerInfo {
        val manufacturer = Build.MANUFACTURER.lowercase()

        return when {
            manufacturer.contains("samsung") -> ManufacturerInfo(
                name = "Samsung",
                instructions = """
                    1. Ve a Configuración → Cuidado del dispositivo → Batería
                    2. Toca en "Límites de uso en segundo plano"
                    3. Selecciona "Apps que no se pondrán en reposo"
                    4. Agrega AkiEstoy a la lista

                    También:
                    5. Ve a Configuración → Apps → AkiEstoy
                    6. Toca en "Batería"
                    7. Selecciona "Sin restricciones"
                """.trimIndent(),
                hasSpecialSettings = true
            )
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> ManufacturerInfo(
                name = "Xiaomi/Redmi/Poco",
                instructions = """
                    1. Ve a Configuración → Apps → Administrar apps
                    2. Busca y selecciona AkiEstoy
                    3. Activa "Inicio automático"
                    4. Ve a "Ahorro de batería" y selecciona "Sin restricciones"
                    5. Ve a "Otras autorizaciones" y activa todo

                    También en MIUI 12+:
                    6. Configuración → Batería y rendimiento
                    7. Selecciona "Sin restricciones" para AkiEstoy
                """.trimIndent(),
                hasSpecialSettings = true
            )
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> ManufacturerInfo(
                name = "Huawei/Honor",
                instructions = """
                    1. Ve a Configuración → Batería → Inicio de aplicaciones
                    2. Busca AkiEstoy y desactiva "Administrar automáticamente"
                    3. Activa manualmente las 3 opciones:
                       - Inicio automático
                       - Inicio secundario
                       - Ejecutar en segundo plano

                    EMUI 9+:
                    4. Configuración → Batería → Configuración
                    5. Desactiva "Optimización del uso de batería"
                    6. Selecciona AkiEstoy
                """.trimIndent(),
                hasSpecialSettings = true
            )
            manufacturer.contains("oppo") -> ManufacturerInfo(
                name = "Oppo",
                instructions = """
                    1. Ve a Configuración → Batería → Optimización de batería
                    2. Selecciona "Todas las apps"
                    3. Busca AkiEstoy y selecciona "No optimizar"

                    ColorOS 7+:
                    4. Configuración → Apps → Administrador de aplicaciones
                    5. Selecciona AkiEstoy
                    6. Activa "Permitir inicio automático"
                    7. Activa "Permitir actividad en segundo plano"
                """.trimIndent(),
                hasSpecialSettings = true
            )
            manufacturer.contains("vivo") -> ManufacturerInfo(
                name = "Vivo",
                instructions = """
                    1. Ve a Configuración → Batería → Aplicaciones en segundo plano
                    2. Busca AkiEstoy y permite que se ejecute en segundo plano

                    3. Configuración → Más ajustes → Aplicaciones
                    4. Selecciona AkiEstoy
                    5. Activa "Inicio automático"
                    6. Desactiva "Consumo elevado de batería en segundo plano"
                """.trimIndent(),
                hasSpecialSettings = true
            )
            manufacturer.contains("oneplus") -> ManufacturerInfo(
                name = "OnePlus",
                instructions = """
                    1. Ve a Configuración → Batería → Optimización de batería
                    2. Toca el menú (⋮) y selecciona "Apps avanzadas"
                    3. Busca AkiEstoy
                    4. Desactiva "Optimización de batería"

                    OxygenOS 12+:
                    5. Configuración → Apps → AkiEstoy
                    6. Batería → Uso de batería → Sin restricciones
                """.trimIndent(),
                hasSpecialSettings = true
            )
            else -> ManufacturerInfo(
                name = "Android Estándar",
                instructions = """
                    1. La app solicitará automáticamente excluirse de la optimización de batería
                    2. Si no funciona, ve a:
                       Configuración → Apps → AkiEstoy → Batería
                    3. Selecciona "Sin restricciones"
                """.trimIndent(),
                hasSpecialSettings = false
            )
        }
    }

    /**
     * Intenta abrir la configuración de batería específica del fabricante
     */
    fun openBatterySettings(context: Context) {
        val manufacturer = Build.MANUFACTURER.lowercase()

        try {
            val intent = when {
                manufacturer.contains("xiaomi") -> {
                    Intent().apply {
                        component = android.content.ComponentName(
                            "com.miui.securitycenter",
                            "com.miui.permcenter.autostart.AutoStartManagementActivity"
                        )
                    }
                }
                manufacturer.contains("huawei") -> {
                    Intent().apply {
                        component = android.content.ComponentName(
                            "com.huawei.systemmanager",
                            "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                        )
                    }
                }
                manufacturer.contains("oppo") -> {
                    Intent().apply {
                        component = android.content.ComponentName(
                            "com.coloros.safecenter",
                            "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                        )
                    }
                }
                manufacturer.contains("vivo") -> {
                    Intent().apply {
                        component = android.content.ComponentName(
                            "com.iqoo.secure",
                            "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
                        )
                    }
                }
                else -> {
                    // Fallback a configuración de batería estándar
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                }
            }

            context.startActivity(intent)
            Log.i(TAG, "✅ Opened manufacturer-specific battery settings")
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Could not open manufacturer settings, falling back to standard", e)
            // Fallback a configuración de la app
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            } catch (e2: Exception) {
                Log.e(TAG, "❌ Could not open any settings", e2)
            }
        }
    }
}
