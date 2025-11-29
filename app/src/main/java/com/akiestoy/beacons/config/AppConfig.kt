package com.akiestoy.beacons.config

import com.akiestoy.beacons.BuildConfig

/**
 * Configuración centralizada de la aplicación
 * Lee valores desde BuildConfig (generados desde secrets.properties)
 */
object AppConfig {
    /**
     * Intervalo de escaneo de beacons (en milisegundos)
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
     * Intervalo para enviar lecturas de beacons al servidor (en milisegundos)
     * Por defecto: 15 segundos
     */
    val BEACON_READING_INTERVAL_MS: Long = try {
        (BuildConfig.BEACON_READING_INTERVAL_SECONDS.toLongOrNull() ?: 15L) * 1000L
    } catch (e: Exception) {
        15000L // Fallback a 15 segundos
    }

    /**
     * Intervalo de verificación de señal (en milisegundos)
     * Por defecto: 2 segundos
     */
    val SIGNAL_CHECK_INTERVAL_MS: Long = try {
        (BuildConfig.SIGNAL_CHECK_INTERVAL_SECONDS.toLongOrNull() ?: 2L) * 1000L
    } catch (e: Exception) {
        2000L // Fallback a 2 segundos
    }

    /**
     * Intervalo de heartbeat (keepalive) (en milisegundos)
     * Por defecto: 60 segundos
     */
    val HEARTBEAT_INTERVAL_MS: Long = try {
        (BuildConfig.HEARTBEAT_INTERVAL_SECONDS.toLongOrNull() ?: 60L) * 1000L
    } catch (e: Exception) {
        60000L // Fallback a 60 segundos
    }

    /**
     * Delay antes de marcar salida definitiva (en milisegundos)
     * Por defecto: 120 segundos (2 minutos)
     */
    val EXIT_DELAY_MS: Long = try {
        (BuildConfig.EXIT_DELAY_SECONDS.toLongOrNull() ?: 120L) * 1000L
    } catch (e: Exception) {
        120000L // Fallback a 120 segundos
    }

    /**
     * Timeout para considerar señal perdida (en milisegundos)
     * Por defecto: 5 segundos
     */
    val SIGNAL_LOST_THRESHOLD_MS: Long = try {
        (BuildConfig.SIGNAL_LOST_THRESHOLD_SECONDS.toLongOrNull() ?: 5L) * 1000L
    } catch (e: Exception) {
        5000L // Fallback a 5 segundos
    }

    /**
     * Intervalo de actualización de filtros en UI (en milisegundos)
     * Por defecto: 1 segundo
     */
    val FILTER_UPDATE_INTERVAL_MS: Long = try {
        (BuildConfig.FILTER_UPDATE_INTERVAL_SECONDS.toLongOrNull() ?: 1L) * 1000L
    } catch (e: Exception) {
        1000L // Fallback a 1 segundo
    }

    /**
     * Intervalo de logs para evitar saturación de logcat (en milisegundos)
     * Por defecto: 5 segundos
     */
    val LOG_INTERVAL_MS: Long = 5000L

    // ==========================================
    // IDs FIJOS PARA TESTING/DESARROLLO
    // ==========================================

    /**
     * Tenant ID fijo para pruebas
     */
    const val DEFAULT_TENANT_ID = "550e8400-e29b-41d4-a716-446655440000"

    /**
     * Company ID fijo para pruebas
     */
    const val DEFAULT_COMPANY_ID = "660e8400-e29b-41d4-a716-446655440000"
}
