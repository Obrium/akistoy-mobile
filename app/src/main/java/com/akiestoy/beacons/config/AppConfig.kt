package com.akiestoy.beacons.config

import com.akiestoy.beacons.BuildConfig

/**
 * Configuración centralizada de la aplicación
 * Lee valores desde BuildConfig (generados desde .env)
 */
object AppConfig {
    /**
     * Intervalo de verificación de señal de beacons (en milisegundos)
     * Por defecto: 2 segundos
     */
    val SCAN_INTERVAL_MS: Long = try {
        (BuildConfig.SCAN_INTERVAL_SECONDS.toLongOrNull() ?: 2L) * 1000L
    } catch (e: Exception) {
        2000L // Fallback a 2 segundos
    }

    /**
     * Intervalo para enviar eventos en batch (en milisegundos)
     * Por defecto: 15 segundos
     */
    val EVENT_BATCH_INTERVAL_MS: Long = try {
        (BuildConfig.EVENT_BATCH_INTERVAL_SECONDS.toLongOrNull() ?: 15L) * 1000L
    } catch (e: Exception) {
        15000L // Fallback a 15 segundos
    }

    /**
     * Timeout para considerar señal perdida (en milisegundos)
     * Por defecto: 5 segundos
     */
    const val SIGNAL_LOST_THRESHOLD_MS = 5000L

    /**
     * Intervalo de heartbeat (en milisegundos)
     * Por defecto: 60 segundos
     */
    const val HEARTBEAT_INTERVAL_MS = 60000L

    /**
     * Delay antes de marcar salida definitiva (en milisegundos)
     * Por defecto: 2 minutos
     */
    const val EXIT_DELAY_MS = 120000L

    /**
     * Intervalo de logs para evitar saturación de logcat (en milisegundos)
     * Por defecto: 5 segundos
     */
    const val LOG_INTERVAL_MS = 5000L
}
