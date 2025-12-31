package com.akiestoy.beacons.proximity

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Scanner BLE en modo passive (sin conexión) con LOW_LATENCY
 * Diseñado para escanear beacons y notificar detecciones
 * Solo procesa beacons que estén en la lista de favoritos
 */
class ProximityBeaconScanner(
    private val context: Context,
    private val onBeaconDetected: (macAddress: String, uuid: String, major: Int, minor: Int, rssi: Int) -> Unit,
    private val isFavorite: (String) -> Boolean
) {
    private val TAG = "ProximityBeaconScanner"

    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    // Obtener scanner fresh cada vez (para recuperarse de estados corruptos)
    private fun getBluetoothLeScanner(): BluetoothLeScanner? {
        return bluetoothManager.adapter?.bluetoothLeScanner
    }

    private var scanCallback: ScanCallback? = null
    private var currentScope: CoroutineScope? = null
    private var retryJob: Job? = null

    // Estado del scanner
    private val _isScanning = MutableStateFlow(false)
    val isScanning: Flow<Boolean> = _isScanning.asStateFlow()

    // Timestamp de última detección (para watchdog)
    @Volatile
    var lastDetectionTimestamp: Long = 0L
        private set

    // Timestamp de último inicio de escaneo (para detectar zombies)
    @Volatile
    var lastScanStartTimestamp: Long = 0L
        private set

    // Contador de detecciones totales (para verificar que el scanner está funcionando)
    @Volatile
    var totalDetectionCount: Long = 0L
        private set

    // Contador de errores consecutivos
    private var consecutiveErrors = 0
    private var lastErrorTimestamp = 0L

    companion object {
        // UUID de los beacons iBeacon (formato Apple)
        private const val IBEACON_UUID = "e2c56db5-dffb-48d2-b060-d0f5a71096e0"

        // Configuración de retry
        // AUMENTADO: 5 era muy bajo - en un día pueden haber varios glitches de BLE
        // Con el watchdog activo, no hay riesgo de loops infinitos
        private const val MAX_CONSECUTIVE_ERRORS = 20
        private const val INITIAL_RETRY_DELAY_MS = 2000L
        private const val MAX_RETRY_DELAY_MS = 30000L
        private const val ERROR_RESET_WINDOW_MS = 60000L // Reset error count después de 1 min sin errores
    }

    /**
     * Inicia el escaneo BLE en modo BALANCED
     * Incluye auto-recovery si el scan falla
     */
    @SuppressLint("MissingPermission")
    fun startScanning(scope: CoroutineScope) {
        currentScope = scope

        if (_isScanning.value) {
            Log.w(TAG, "⚠️ Scanning already in progress")
            return
        }

        val bluetoothAdapter = bluetoothManager.adapter
        val bluetoothLeScanner = getBluetoothLeScanner()

        if (bluetoothAdapter == null || bluetoothLeScanner == null) {
            Log.e(TAG, "❌ Bluetooth not available on this device")
            scheduleRetry(scope, "Bluetooth not available")
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            Log.e(TAG, "❌ Bluetooth is disabled")
            scheduleRetry(scope, "Bluetooth disabled")
            return
        }

        Log.i(TAG, "🔍 Starting BLE scanning in BALANCED mode...")
        Log.d(TAG, "📍 Looking for iBeacon UUID: $IBEACON_UUID")

        // Configurar ScanSettings para BALANCED (balance entre batería y rendimiento)
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)  // Escaneo balanceado - ahorra ~60% batería
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
            .setReportDelay(0L)  // Reportar inmediatamente
            .build()

        // Configurar filtros para detectar solo nuestros beacons
        val scanFilters = buildScanFilters()

        // Crear callback para recibir resultados
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                // Reset error count on successful scan
                consecutiveErrors = 0
                handleScanResult(result, scope)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                // Reset error count on successful scan
                consecutiveErrors = 0
                results.forEach { result ->
                    handleScanResult(result, scope)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                val errorName = getScanErrorName(errorCode)
                Log.e(TAG, "❌ Scan failed with error: $errorName (code: $errorCode)")
                _isScanning.value = false

                // Auto-recovery: programar reintento
                handleScanError(scope, errorCode)
            }
        }

        // Iniciar escaneo
        try {
            bluetoothLeScanner.startScan(scanFilters, scanSettings, scanCallback)
            _isScanning.value = true
            lastScanStartTimestamp = System.currentTimeMillis()
            // NOTA: Ya no inicializamos lastDetectionTimestamp aquí
            // Esto permite que el watchdog detecte si el scanner nunca ha detectado nada
            // después de un tiempo razonable (indica scanner zombie o sin beacons cerca)
            Log.i(TAG, "✅ BLE scan started successfully (detection count: $totalDetectionCount)")

        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Permission denied for BLE scanning", e)
            scheduleRetry(scope, "Permission denied")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting BLE scan", e)
            scheduleRetry(scope, e.message ?: "Unknown error")
        }
    }

    /**
     * Maneja errores de escaneo e intenta recuperación automática
     */
    private fun handleScanError(scope: CoroutineScope, errorCode: Int) {
        val now = System.currentTimeMillis()

        // Reset error count si ha pasado suficiente tiempo desde el último error
        if (now - lastErrorTimestamp > ERROR_RESET_WINDOW_MS) {
            consecutiveErrors = 0
        }

        lastErrorTimestamp = now
        consecutiveErrors++

        when (errorCode) {
            ScanCallback.SCAN_FAILED_ALREADY_STARTED -> {
                // El scan ya está corriendo, solo actualizar estado
                Log.w(TAG, "⚠️ Scan already started, updating state")
                _isScanning.value = true
            }
            ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> {
                // Error crítico: reintentar después de delay
                Log.e(TAG, "🔄 App registration failed, will retry...")
                scheduleRetry(scope, "App registration failed")
            }
            ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> {
                // No reintentar - el dispositivo no soporta BLE
                Log.e(TAG, "❌ BLE scanning not supported on this device")
            }
            ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> {
                // Error interno de BLE stack - necesita reinicio
                Log.e(TAG, "🔄 Internal BLE error, will retry with fresh scanner...")
                scheduleRetry(scope, "Internal BLE error")
            }
            else -> {
                Log.e(TAG, "🔄 Unknown scan error, will retry...")
                scheduleRetry(scope, "Unknown error: $errorCode")
            }
        }
    }

    /**
     * Programa un reintento de escaneo con backoff exponencial
     * NOTA: Ya no se rinde completamente - siempre sigue intentando con delay máximo
     * El watchdog también puede forzar un restart si detecta problemas
     */
    private fun scheduleRetry(scope: CoroutineScope, reason: String) {
        // Ya no abandonamos después de MAX_CONSECUTIVE_ERRORS
        // En su lugar, usamos delay máximo para no saturar el sistema
        val useMaxDelay = consecutiveErrors >= MAX_CONSECUTIVE_ERRORS

        if (useMaxDelay) {
            Log.w(TAG, "⚠️ Muchos errores consecutivos ($consecutiveErrors), usando delay máximo")
        }

        // Calcular delay con backoff exponencial (máximo 30 segundos)
        val delayMs = if (useMaxDelay) {
            MAX_RETRY_DELAY_MS
        } else {
            (INITIAL_RETRY_DELAY_MS * (1 shl consecutiveErrors.coerceAtMost(4)))
                .coerceAtMost(MAX_RETRY_DELAY_MS)
        }

        Log.i(TAG, "🔄 Scheduling retry #${consecutiveErrors} in ${delayMs}ms (reason: $reason)")

        retryJob?.cancel()
        retryJob = scope.launch {
            delay(delayMs)
            Log.i(TAG, "🔄 Retrying scan...")
            startScanning(scope)
        }
    }

    /**
     * Obtiene nombre legible del error de scan
     */
    private fun getScanErrorName(errorCode: Int): String {
        return when (errorCode) {
            ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "ALREADY_STARTED"
            ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "APP_REGISTRATION_FAILED"
            ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "FEATURE_UNSUPPORTED"
            ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "INTERNAL_ERROR"
            5 -> "OUT_OF_HARDWARE_RESOURCES"
            6 -> "SCANNING_TOO_FREQUENTLY"
            else -> "UNKNOWN($errorCode)"
        }
    }

    /**
     * Fuerza un reinicio completo del scanner
     * Útil cuando el scanner está en estado corrupto
     */
    @SuppressLint("MissingPermission")
    fun forceRestart() {
        Log.w(TAG, "🔄 Force restarting scanner...")
        consecutiveErrors = 0
        stopScanning()

        currentScope?.let { scope ->
            scope.launch {
                delay(1000) // Dar tiempo al sistema para limpiar
                startScanning(scope)
            }
        }
    }

    /**
     * Detiene el escaneo BLE
     */
    @SuppressLint("MissingPermission")
    fun stopScanning() {
        Log.i(TAG, "🛑 Stopping BLE scanning...")

        // Cancelar retry pendiente
        retryJob?.cancel()
        retryJob = null

        if (!_isScanning.value) {
            Log.w(TAG, "⚠️ Scanning is not active")
            return
        }

        try {
            scanCallback?.let { callback ->
                getBluetoothLeScanner()?.stopScan(callback)
            }
            scanCallback = null
            _isScanning.value = false

            Log.i(TAG, "✅ BLE scan stopped successfully")

        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Permission denied for stopping BLE scan", e)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error stopping BLE scan", e)
        }
    }

    /**
     * Verifica si el scanner está realmente funcionando
     * (no solo marcado como "scanning" pero sin detecciones)
     *
     * @param maxSilenceMs Tiempo máximo sin detecciones para considerar unhealthy
     * @return Triple(isHealthy, reason, timeSinceLastDetectionMs)
     */
    fun isHealthy(maxSilenceMs: Long = 60000L): Boolean {
        if (!_isScanning.value) return false

        val now = System.currentTimeMillis()
        val timeSinceLastDetection = now - lastDetectionTimestamp
        val timeSinceScanStart = now - lastScanStartTimestamp

        // Si nunca ha detectado nada (lastDetectionTimestamp == 0)
        // verificamos cuánto tiempo ha estado escaneando
        if (lastDetectionTimestamp == 0L) {
            // Si ha estado escaneando por más de maxSilenceMs sin detectar nada,
            // podría ser un scanner zombie o simplemente no hay beacons cerca
            // No lo consideramos unhealthy inmediatamente, pero lo logueamos
            if (timeSinceScanStart > maxSilenceMs) {
                Log.d(TAG, "📊 Scanner activo pero sin detecciones aún (${timeSinceScanStart/1000}s desde inicio)")
            }
            // Consideramos healthy si ha estado escaneando menos de 5 minutos sin detecciones
            // Después de 5 minutos, lo consideramos potencialmente unhealthy
            return timeSinceScanStart < 300_000L
        }

        return timeSinceLastDetection < maxSilenceMs
    }

    /**
     * Obtiene información de diagnóstico del scanner
     */
    fun getDiagnosticInfo(): String {
        val now = System.currentTimeMillis()
        return buildString {
            appendLine("=== Scanner Diagnostic ===")
            appendLine("isScanning: ${_isScanning.value}")
            appendLine("consecutiveErrors: $consecutiveErrors")
            appendLine("totalDetectionCount: $totalDetectionCount")
            if (lastDetectionTimestamp > 0) {
                appendLine("lastDetection: ${(now - lastDetectionTimestamp) / 1000}s ago")
            } else {
                appendLine("lastDetection: NEVER")
            }
            if (lastScanStartTimestamp > 0) {
                appendLine("scanStarted: ${(now - lastScanStartTimestamp) / 1000}s ago")
            }
        }
    }

    /**
     * Construye filtros de escaneo para iBeacons
     * Los iBeacons usan manufacturerData con Apple ID (0x004C), NO serviceUuid
     */
    private fun buildScanFilters(): List<ScanFilter> {
        val filters = mutableListOf<ScanFilter>()

        try {
            // Apple manufacturer ID = 0x004C (76 decimal)
            // iBeacon prefix: 0x02 0x15 (subtype iBeacon, 21 bytes data)
            val appleManufacturerId = 0x004C
            // Prefijo mínimo para identificar iBeacons: [0x02, 0x15]
            val iBeaconPrefix = byteArrayOf(0x02, 0x15)
            // Máscara para verificar solo los primeros 2 bytes
            val iBeaconMask = byteArrayOf(0xFF.toByte(), 0xFF.toByte())

            val filter = ScanFilter.Builder()
                .setManufacturerData(appleManufacturerId, iBeaconPrefix, iBeaconMask)
                .build()
            filters.add(filter)
            Log.i(TAG, "✅ iBeacon filter configured (Apple ID: 0x004C, prefix: 0x02 0x15)")
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Could not create iBeacon filter, scanning all devices", e)
        }

        // Si no se pudo crear el filtro, retornar lista vacía (escanear todo)
        return filters
    }

    /**
     * Procesa un resultado de escaneo
     */
    private fun handleScanResult(result: ScanResult, scope: CoroutineScope) {
        val device = result.device
        val rssi = result.rssi
        val macAddress = device.address

        // Actualizar timestamp de última detección (cualquier beacon BLE)
        lastDetectionTimestamp = System.currentTimeMillis()
        totalDetectionCount++

        // Verificar si es un iBeacon válido
        val scanRecord = result.scanRecord
        if (scanRecord == null) {
            Log.v(TAG, "❌ No scan record for device: $macAddress")
            return
        }

        // Extraer datos del beacon (iBeacon parsing)
        val beaconData = parseIBeaconData(scanRecord.bytes)
        if (beaconData != null) {
            val (uuid, major, minor, txPower) = beaconData
            Log.d(TAG, "📡 Found iBeacon: MAC=$macAddress, UUID=$uuid, Major=$major, Minor=$minor, RSSI=$rssi")

            // Verificar que sea nuestro UUID
            if (uuid.equals(IBEACON_UUID, ignoreCase = true)) {
                Log.d(TAG, "✅ UUID matches! Checking if favorite...")
                // Verificar si el beacon está en favoritos
                if (isFavorite(macAddress)) {
                    Log.i(TAG, "⭐ Beacon is favorite! Notifying detection")
                    // Notificar detección mediante callback
                    onBeaconDetected(macAddress, uuid, major, minor, rssi)
                } else {
                    Log.d(TAG, "⚠️ Beacon not in favorites: $macAddress")
                }
            } else {
                Log.v(TAG, "⚠️ UUID doesn't match. Expected: $IBEACON_UUID, Got: $uuid")
            }
        } else {
            Log.v(TAG, "⚠️ Not an iBeacon or invalid format: $macAddress")
        }
    }

    /**
     * Parsea los datos de advertising de un iBeacon
     * Formato iBeacon: [Prefix][UUID][Major][Minor][TX Power]
     */
    private fun parseIBeaconData(scanData: ByteArray?): IBeaconData? {
        if (scanData == null || scanData.size < 30) return null

        try {
            // Buscar el prefijo de iBeacon (0x02 0x15)
            // El formato completo es: [0x02, 0x01, 0x06, 0x1A, 0xFF, 0x4C, 0x00, 0x02, 0x15, UUID...]
            var startIndex = -1
            for (i in 0 until scanData.size - 1) {
                if (scanData[i] == 0x02.toByte() && scanData[i + 1] == 0x15.toByte()) {
                    startIndex = i
                    break
                }
            }

            if (startIndex == -1 || startIndex + 25 > scanData.size) {
                return null
            }

            // UUID (16 bytes)
            val uuidBytes = scanData.sliceArray((startIndex + 2)..(startIndex + 17))
            val uuid = bytesToUUID(uuidBytes)

            // Major (2 bytes)
            val major = ((scanData[startIndex + 18].toInt() and 0xFF) shl 8) or
                    (scanData[startIndex + 19].toInt() and 0xFF)

            // Minor (2 bytes)
            val minor = ((scanData[startIndex + 20].toInt() and 0xFF) shl 8) or
                    (scanData[startIndex + 21].toInt() and 0xFF)

            // TX Power (1 byte signed)
            val txPower = scanData[startIndex + 22].toInt()

            return IBeaconData(uuid, major, minor, txPower)

        } catch (e: Exception) {
            Log.w(TAG, "Error parsing iBeacon data", e)
            return null
        }
    }

    /**
     * Convierte bytes a formato UUID
     */
    private fun bytesToUUID(bytes: ByteArray): String {
        val hex = bytes.joinToString("") { "%02x".format(it) }
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-" +
                "${hex.substring(16, 20)}-${hex.substring(20, 32)}"
    }

    /**
     * Clase de datos para información de iBeacon
     */
    private data class IBeaconData(
        val uuid: String,
        val major: Int,
        val minor: Int,
        val txPower: Int
    )
}
