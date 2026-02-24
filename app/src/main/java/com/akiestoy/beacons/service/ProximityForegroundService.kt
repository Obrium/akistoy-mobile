package com.akiestoy.beacons.service

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.akiestoy.beacons.MainActivity
import com.akiestoy.beacons.R
import com.akiestoy.beacons.BuildConfig
import com.akiestoy.beacons.api.ApiClient
import com.akiestoy.beacons.data.AppDatabase
import com.akiestoy.beacons.data.FavoritesRepository
import com.akiestoy.beacons.model.RegisteredBeacon
import com.akiestoy.beacons.model.tracking.HeartbeatRequest
import com.akiestoy.beacons.proximity.ProximityBeaconScanner
import com.akiestoy.beacons.state.ZoneInfo
import com.akiestoy.beacons.tracking.BeaconTrackingService
import com.akiestoy.beacons.tracking.EventBatcher
import com.akiestoy.beacons.tracking.ZoneEventService
import com.akiestoy.beacons.tracking.ZoneManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Servicio en primer plano para escaneo de proximidad BLE
 * Integra ProximityBeaconScanner, BeaconProximityManager y ProximityEventSender
 */
class ProximityForegroundService : Service() {

    private val TAG = "ProximityForegroundService"

    private lateinit var trackingService: BeaconTrackingService
    private lateinit var zoneEventService: ZoneEventService
    private lateinit var proximityScanner: ProximityBeaconScanner
    private lateinit var favoritesRepository: FavoritesRepository
    private lateinit var database: AppDatabase
    private lateinit var zoneManager: ZoneManager
    private var wakeLock: PowerManager.WakeLock? = null

    // Cache de beacons registrados (actualizado periódicamente)
    private var registeredBeaconsCache: List<RegisteredBeacon> = emptyList()

    // Datos del usuario para heartbeat
    private var currentDeviceId: String = ""
    private var currentTenantId: String = ""
    private var currentEmployeeRut: String? = null
    private var currentEmployeeName: String? = null

    // Intervalo de heartbeat (5 minutos)
    private val HEARTBEAT_INTERVAL_MS = 5 * 60 * 1000L

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    companion object {
        private const val CHANNEL_ID = "ProximityServiceSilent"
        private const val OLD_CHANNEL_ID = "ProximityServiceChannel"
        private const val NOTIFICATION_ID = 1002
        private const val RESTART_DEBOUNCE_MS = 5000L  // 5 segundos entre reinicios

        @Volatile
        private var isRunning = false

        @Volatile
        private var lastRestartAttemptTimestamp = 0L

        fun startService(context: Context) {
            // Verificar permisos de ubicación antes de iniciar (requerido para foregroundServiceType=location)
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Log.w("ProximityForegroundService", "Location permission not granted, skipping service start")
                return
            }
            val intent = Intent(context, ProximityForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, ProximityForegroundService::class.java)
            context.stopService(intent)
        }

        /**
         * Verifica si el servicio está actualmente en ejecución
         * Usa ActivityManager para verificación robusta
         */
        @Suppress("DEPRECATION")
        fun isServiceRunning(context: Context): Boolean {
            try {
                val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
                    if (ProximityForegroundService::class.java.name == service.service.className) {
                        Log.d("ProximityForegroundService", "✅ Service is running (verified by ActivityManager)")
                        return true
                    }
                }
                Log.d("ProximityForegroundService", "❌ Service is NOT running (verified by ActivityManager)")
                return false
            } catch (e: Exception) {
                Log.e("ProximityForegroundService", "Error checking service status", e)
                return isRunning  // Fallback a la variable estática
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        Log.i(TAG, "ProximityForegroundService created")

        try {
            // Crear canal y notificación PRIMERO (requerido para foreground service)
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, createNotification("Servicio activo"))
        } catch (e: SecurityException) {
            // Permisos insuficientes para foreground service con tipo location
            Log.e(TAG, "SecurityException en startForeground - permisos insuficientes", e)
            isRunning = false
            stopSelf()
            return
        } catch (e: Exception) {
            Log.e(TAG, "Error en startForeground", e)
            isRunning = false
            stopSelf()
            return
        }

        try {
            // Adquirir Wake Lock para mantener el CPU activo con pantalla bloqueada
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Akistoy::BeaconScanningWakeLock"
            ).apply {
                acquire(10 * 60 * 1000L)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adquiriendo Wake Lock (no critico)", e)
        }

        try {
            initializeComponents()
        } catch (e: Exception) {
            Log.e(TAG, "Error inicializando componentes", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "▶️ Service started (startId: $startId)")

        if (intent == null) {
            Log.w(TAG, "⚠️ Service restarted by system (intent is null)")
        }

        // Iniciar escaneo con protección contra crashes
        try {
            startProximityScanning()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error iniciando escaneo en onStartCommand", e)
        }

        // START_STICKY: Si el sistema mata el servicio, lo reiniciará automáticamente
        // pero sin reenviar el Intent original (será null)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.w(TAG, "Task removed - scheduling service restart via AlarmManager")
        scheduleRestartAlarm()
        super.onTaskRemoved(rootIntent)
    }

    /**
     * Programa un AlarmManager para reiniciar el servicio.
     * A diferencia de Handler.postDelayed o broadcasts internos,
     * AlarmManager sobrevive la muerte del proceso.
     */
    private fun scheduleRestartAlarm() {
        try {
            val restartIntent = Intent(this, com.akiestoy.beacons.receiver.ServiceRestartReceiver::class.java)
            restartIntent.action = "com.akiestoy.beacons.RESTART_SERVICE"
            val pendingIntent = PendingIntent.getBroadcast(
                this, 0, restartIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 3000, // 3 segundos
                pendingIntent
            )
            Log.i(TAG, "Restart alarm scheduled (3s)")
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling restart alarm", e)
        }
    }

    override fun onDestroy() {
        isRunning = false
        Log.i(TAG, "🛑 Service destroyed")

        // Liberar Wake Lock
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.i(TAG, "🔋 Wake Lock liberado")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing wake lock", e)
        }

        // Detener escaneo (con verificación de inicialización)
        try {
            if (::proximityScanner.isInitialized) {
                proximityScanner.stopScanning()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping scanner", e)
        }

        // Limpiar tracking service (con verificación de inicialización)
        try {
            if (::trackingService.isInitialized) {
                trackingService.cleanup()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up tracking service", e)
        }

        // Limpiar ZoneManager (con verificación de inicialización)
        try {
            if (::zoneManager.isInitialized) {
                zoneManager.reset()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting zone manager", e)
        }

        // Limpiar ZoneEventService (con verificación de inicialización)
        try {
            if (::zoneEventService.isInitialized) {
                zoneEventService.reset()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting zone event service", e)
        }

        // Cancelar coroutines
        try {
            serviceScope.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling service scope", e)
        }

        try {
            if (::trackingService.isInitialized) {
                Log.d(TAG, "Final state: ${trackingService.getStateInfo()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting final state info", e)
        }

        // Programar reinicio via AlarmManager (sobrevive muerte del proceso)
        // DEBOUNCE: Prevenir loop infinito de restart -> crash -> restart
        val now = System.currentTimeMillis()
        val timeSinceLastRestart = now - lastRestartAttemptTimestamp

        if (timeSinceLastRestart > RESTART_DEBOUNCE_MS) {
            lastRestartAttemptTimestamp = now
            scheduleRestartAlarm()
        } else {
            Log.w(TAG, "Restart skipped (debounce: ${timeSinceLastRestart}ms < ${RESTART_DEBOUNCE_MS}ms)")
        }

        super.onDestroy()
    }

    /**
     * Inicializa los componentes del servicio
     */
    @SuppressLint("HardwareIds")
    private fun initializeComponents() {
        // Obtener device ID y guardarlo para heartbeat
        val deviceId = try {
            Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        } catch (e: Exception) {
            "unknown-device"
        }
        currentDeviceId = deviceId

        // Obtener device name
        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"

        // Crear FavoritesRepository
        favoritesRepository = FavoritesRepository(this)

        // Obtener base de datos y DAO
        database = AppDatabase.getDatabase(this)

        // ===== PRE-CARGA INMEDIATA DE BEACONS =====
        // Cargar beacons de la BD local SINCRÓNICAMENTE para estar listos al iniciar escaneo
        // Esto resuelve el problema de timing donde el escaneo iniciaba antes de tener beacons
        try {
            runBlocking {
                val cachedBeacons = database.registeredBeaconDao().getAllActiveBeaconsOnce()
                if (cachedBeacons.isNotEmpty()) {
                    registeredBeaconsCache = cachedBeacons
                    Log.i(TAG, "⚡ Pre-carga inmediata: ${cachedBeacons.size} beacons desde BD local")
                    cachedBeacons.forEach { beacon ->
                        Log.d(TAG, "   📍 ${beacon.zoneName} - MAC: ${beacon.mac ?: "N/A"}")
                    }
                } else {
                    Log.w(TAG, "⚠️ No hay beacons en BD local - esperando sincronización")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error en pre-carga de beacons (no crítico)", e)
        }

        // ===== PRE-CARGA INMEDIATA DE USUARIO =====
        // Cargar datos del usuario SINCRÓNICAMENTE para que ZoneEventService
        // tenga el RUT desde el primer momento. Sin esto, si un beacon se detecta
        // antes de que el usuario cargue asincrónicamente, el evento COMPANY_ENTRY
        // se descarta porque userRut es null (bug: "la app no conecta a la primera")
        var initialUserRut: String? = null
        var initialUserName: String? = null
        var initialTenantId: String = ""
        var initialCompanyId: String = ""
        try {
            runBlocking {
                val user = database.userDao().getCurrentUserOnce()
                if (user != null) {
                    initialUserRut = user.rut
                    initialUserName = user.name
                    initialTenantId = user.tenantId
                    initialCompanyId = user.companyId
                    currentTenantId = user.tenantId
                    currentEmployeeRut = user.rut
                    currentEmployeeName = user.name
                    Log.i(TAG, "⚡ Pre-carga de usuario: ${user.name} (RUT: ${user.rut}, Tenant: ${user.tenantId})")
                } else {
                    Log.w(TAG, "⚠️ No hay usuario logueado en BD local")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error en pre-carga de usuario (no crítico)", e)
        }

        // ===== SINCRONIZACIÓN FORZADA DESDE SERVIDOR =====
        // Si hay usuario logueado, forzar sincronización de beacons desde el servidor
        // Esto actualiza la BD y el cache se actualiza automáticamente via observer
        if (initialTenantId.isNotEmpty()) {
            serviceScope.launch {
                try {
                    Log.i(TAG, "👤 Usuario encontrado: $initialUserName - forzando sincronización de beacons...")
                    forceBeaconSync(initialTenantId, initialCompanyId)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error sincronizando beacons desde servidor", e)
                }
            }
        } else {
            Log.w(TAG, "⚠️ No hay usuario logueado - esperando login para sincronizar beacons")
        }

        val pendingEventDao = database.pendingEventDao()

        // Crear ZoneManager para detección estable de zonas
        zoneManager = ZoneManager()

        // Crear EventBatcher con cola offline
        val eventBatcher = EventBatcher(
            api = ApiClient.proximityApi,
            pendingEventDao = pendingEventDao
        )

        // Crear BeaconTrackingService con máquina de estados
        trackingService = BeaconTrackingService(
            api = ApiClient.proximityApi,
            deviceId = deviceId,
            deviceName = deviceName,
            eventBatcher = eventBatcher
        )

        // Crear ZoneEventService para eventos optimizados
        // Este servicio REEMPLAZA el envío de beacon-readings continuos
        // y solo envía COMPANY_ENTRY, COMPANY_EXIT, ZONE_CHANGE
        // Incluye cola offline para cuando no hay conexión a internet
        // IMPORTANTE: Pasar userRut/userName ya cargados para que el primer evento no se descarte
        val pendingZoneEventDao = database.pendingZoneEventDao()
        zoneEventService = ZoneEventService(
            api = ApiClient.proximityApi,
            deviceId = deviceId,
            pendingZoneEventDao = pendingZoneEventDao,
            userRut = initialUserRut,
            userName = initialUserName
        )

        // Observar cambios en el usuario logueado y actualizar ZoneEventService + datos para heartbeat
        // Esto permite que el servicio se actualice si el usuario hace login después de que el servicio inicie
        serviceScope.launch {
            database.userDao().getCurrentUser().collect { user ->
                if (user != null) {
                    // Actualizar ZoneEventService
                    zoneEventService.setUser(user.rut, user.name)
                    // Guardar datos para heartbeat
                    currentTenantId = user.tenantId
                    currentEmployeeRut = user.rut
                    currentEmployeeName = user.name
                    Log.i(TAG, "👤 Usuario actualizado (async): ${user.name} (RUT: ${user.rut}, Tenant: ${user.tenantId})")
                } else {
                    zoneEventService.setUser(null, null)
                    currentTenantId = ""
                    currentEmployeeRut = null
                    currentEmployeeName = null
                    Log.w(TAG, "⚠️ No hay usuario logueado - eventos se enviarán sin identificación")
                }
            }
        }

        // Observar cambios en beacons registrados y actualizar cache automáticamente
        // Esto resuelve el problema de timing donde el servicio arranca antes de que se sincronicen los beacons
        serviceScope.launch {
            database.registeredBeaconDao().getAllActiveBeacons().collect { beacons ->
                registeredBeaconsCache = beacons
                Log.i(TAG, "📋 Cache actualizado (observando BD): ${beacons.size} beacons registrados")
                beacons.forEach { beacon ->
                    Log.d(TAG, "   📍 ${beacon.zoneName} - MAC: ${beacon.mac ?: "N/A"} - Type: ${beacon.beaconType}")
                }
            }
        }

        // Crear Scanner con callback al tracking service y zone event service
        proximityScanner = ProximityBeaconScanner(
            context = this,
            onBeaconDetected = { macAddress, uuid, major, minor, rssi ->
                serviceScope.launch {
                    // Buscar beacon registrado usando PRIORIDAD:
                    // 1. Match por MAC address (más confiable)
                    // 2. Match por UUID + major + minor (fallback)
                    val registeredBeacon = findRegisteredBeacon(macAddress, uuid, major, minor)

                    if (registeredBeacon != null) {
                        Log.d(TAG, "✅ Beacon match: ${registeredBeacon.zoneName} (MAC: ${registeredBeacon.mac ?: "N/A"}, RSSI: $rssi, Type: ${registeredBeacon.beaconType})")

                        // 1. Actualizar ZoneManager para detección estable de zona
                        // ZoneManager aplica EMA, histéresis y confirmación por detecciones consecutivas
                        zoneManager.onBeaconDetected(registeredBeacon, rssi, macAddress)

                        // 2. Solo actualizar cache en ZoneEventService (NO envía eventos)
                        // Los eventos se envían cuando ZoneManager confirma un cambio de zona
                        zoneEventService.updateBeaconCache(registeredBeacon, rssi, macAddress)
                    }
                    // No loguear beacons no registrados para evitar saturación
                }
            },
            isFavorite = { macAddress -> true } // Procesa todos los beacons detectados
        )

        // Observar cambios de zona ESTABILIZADOS del ZoneManager
        // Este es el ÚNICO punto donde se envían eventos al backend
        serviceScope.launch {
            var previousZone: ZoneInfo? = null
            zoneManager.currentZone.collect { zoneInfo ->
                val zoneName = zoneInfo?.beaconName ?: "Buscando..."
                val rssi = zoneInfo?.rssi ?: 0
                Log.i(TAG, "📍 Zona activa cambiada: $zoneName (RSSI: $rssi dBm)")
                // updateNotification("Zona: $zoneName", rssi)

                // Enviar evento al backend SOLO cuando ZoneManager confirma el cambio
                // Esto usa la lógica de estabilización (EMA + histéresis + confirmación)
                zoneEventService.onStableZoneChanged(previousZone, zoneInfo)
                previousZone = zoneInfo
            }
        }

        // Observar cambios de estado del tracking service
        serviceScope.launch {
            trackingService.currentState.collect { state ->
                Log.i(TAG, "🔄 Estado máquina: $state")
            }
        }

        Log.i(TAG, "✅ All components initialized successfully")
        Log.d(TAG, "Device ID: $deviceId")
        Log.d(TAG, "Device Name: $deviceName")
    }

    /**
     * Busca un beacon registrado usando prioridad: MAC > UUID+major+minor
     */
    private suspend fun findRegisteredBeacon(
        macAddress: String,
        uuid: String,
        major: Int,
        minor: Int
    ): RegisteredBeacon? {
        // PRIORIDAD 1: Match por MAC address (más confiable)
        val byMac = registeredBeaconsCache.find { beacon ->
            !beacon.mac.isNullOrEmpty() &&
            beacon.mac.equals(macAddress, ignoreCase = true)
        }

        if (byMac != null) {
            return byMac
        }

        // PRIORIDAD 2: Match por UUID + major + minor
        val byIdentifiers = registeredBeaconsCache.find { beacon ->
            beacon.advUuid.equals(uuid, ignoreCase = true) &&
            beacon.major == major &&
            beacon.minor == minor
        }

        if (byIdentifiers != null) {
            return byIdentifiers
        }

        // Si no está en cache, intentar buscar en BD directamente
        // (puede que el cache esté desactualizado)
        return database.registeredBeaconDao().getBeaconByIdentifiers(
            uuid = uuid.lowercase(),
            major = major,
            minor = minor
        )
    }

    /**
     * Inicia el escaneo de proximidad
     */
    private fun startProximityScanning() {
        try {
            // Iniciar escaneo BLE
            proximityScanner.startScanning(serviceScope)

            // Iniciar ZoneEventService para eventos optimizados
            zoneEventService.start(serviceScope)

            // NOTA: Ya no iniciamos trackingService.startSignalCheck()
            // porque no enviamos beacon-readings continuos

            // Iniciar verificación periódica de timeout del ZoneManager
            startZoneTimeoutCheck()

            // Iniciar watchdog que verifica la salud del scanner
            startScannerWatchdog()

            // Iniciar envío periódico de heartbeat (cada 5 minutos)
            startHeartbeat()

            // updateNotification("Escaneando...", 0)
            Log.i(TAG, "🔍 Proximity scanning started with OPTIMIZED events (ZoneEventService)")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting proximity scanning", e)
        }
    }

    /**
     * Inicia el envío periódico de heartbeat al backend
     * Se envía cada 5 minutos para indicar que la app está activa
     * El backend usa esto para detectar usuarios sin actividad en horario laboral
     */
    private fun startHeartbeat() {
        serviceScope.launch {
            // Esperar 30 segundos antes del primer heartbeat para que se carguen los datos del usuario
            kotlinx.coroutines.delay(30000)

            while (true) {
                try {
                    // Solo enviar si tenemos tenantId (usuario logueado)
                    if (currentTenantId.isNotEmpty()) {
                        val request = HeartbeatRequest(
                            deviceId = currentDeviceId,
                            timestamp = System.currentTimeMillis(),
                            tenantId = currentTenantId,
                            employeeRut = currentEmployeeRut,
                            employeeName = currentEmployeeName,
                            appVersion = BuildConfig.VERSION_NAME
                        )

                        val response = withContext(Dispatchers.IO) {
                            ApiClient.proximityApi.sendHeartbeat(request)
                        }

                        if (response.isSuccessful) {
                            Log.d(TAG, "Heartbeat enviado OK (${currentEmployeeName})")
                        } else {
                            Log.w(TAG, "Heartbeat fallo: ${response.code()}")
                        }

                        // Re-adquirir WakeLock periódicamente para evitar que expire
                        wakeLock?.let {
                            if (!it.isHeld) {
                                it.acquire(10 * 60 * 1000L)
                                Log.d(TAG, "WakeLock re-adquirido")
                            }
                        }
                    } else {
                        Log.d(TAG, "💓 Heartbeat omitido - no hay usuario logueado")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error enviando heartbeat (no crítico)", e)
                }

                // Esperar 5 minutos antes del siguiente heartbeat
                kotlinx.coroutines.delay(HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    /**
     * Inicia la verificación periódica de timeout de zona
     * Verifica cada 5 segundos si se perdió señal de todos los beacons
     */
    private fun startZoneTimeoutCheck() {
        serviceScope.launch {
            while (true) {
                kotlinx.coroutines.delay(5000) // Cada 5 segundos
                if (zoneManager.checkTimeout()) {
                    Log.w(TAG, "⏰ ZoneManager reportó timeout - sin beacons activos")
                }
            }
        }
    }

    /**
     * Inicia el watchdog del scanner BLE
     * Verifica cada 30 segundos que el scanner esté realmente funcionando
     * Si detecta que el scanner está "muerto" (sin detecciones), lo reinicia
     *
     * MEJORAS:
     * - Distingue entre "scanner muerto" vs "sin beacons cerca"
     * - Usa el nuevo sistema de diagnóstico del scanner
     * - Reinicia más agresivamente si hay muchos errores
     */
    private fun startScannerWatchdog() {
        serviceScope.launch {
            // Esperar 60 segundos iniciales antes de empezar a verificar
            kotlinx.coroutines.delay(60000)

            var consecutiveUnhealthyChecks = 0

            while (true) {
                kotlinx.coroutines.delay(30000) // Verificar cada 30 segundos

                try {
                    val now = System.currentTimeMillis()
                    val isHealthy = proximityScanner.isHealthy(maxSilenceMs = 120000)
                    val lastDetection = proximityScanner.lastDetectionTimestamp
                    val timeSinceLastDetection = if (lastDetection > 0) now - lastDetection else -1L
                    val totalDetections = proximityScanner.totalDetectionCount

                    // Verificar si Bluetooth sigue activo
                    val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
                    val bluetoothEnabled = bluetoothManager.adapter?.isEnabled == true

                    if (!bluetoothEnabled) {
                        Log.e(TAG, "❌ WATCHDOG: Bluetooth is disabled!")
                        // updateNotification("Bluetooth desactivado", 0)
                        consecutiveUnhealthyChecks = 0
                        continue
                    }

                    if (!isHealthy) {
                        consecutiveUnhealthyChecks++
                        Log.w(TAG, "⚠️ WATCHDOG: Scanner unhealthy (check #$consecutiveUnhealthyChecks)")
                        Log.w(TAG, "   └─ lastDetection: ${if (timeSinceLastDetection >= 0) "${timeSinceLastDetection/1000}s ago" else "NEVER"}")
                        Log.w(TAG, "   └─ totalDetections: $totalDetections")
                        Log.d(TAG, proximityScanner.getDiagnosticInfo())

                        // Reiniciar si:
                        // 1. Han pasado más de 2 minutos sin detección Y hubo detecciones antes
                        // 2. O si llevamos 3+ checks unhealthy consecutivos (1.5 minutos)
                        val shouldRestart = (timeSinceLastDetection > 120000 && totalDetections > 0) ||
                                           consecutiveUnhealthyChecks >= 3

                        if (shouldRestart) {
                            Log.i(TAG, "🔄 WATCHDOG: Forcing scanner restart...")
                            proximityScanner.forceRestart()
                            // updateNotification("Reiniciando scanner...", 0)
                            consecutiveUnhealthyChecks = 0
                        } else {
                            Log.d(TAG, "📊 WATCHDOG: Waiting for more checks before restart (might just be no beacons nearby)")
                        }
                    } else {
                        if (consecutiveUnhealthyChecks > 0) {
                            Log.i(TAG, "✅ WATCHDOG: Scanner recovered (was unhealthy for $consecutiveUnhealthyChecks checks)")
                        } else {
                            Log.d(TAG, "✅ WATCHDOG: Scanner healthy (detections: $totalDetections)")
                        }
                        consecutiveUnhealthyChecks = 0
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ WATCHDOG: Error checking scanner health", e)
                }
            }
        }
    }

    /**
     * Crea el canal de notificación
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            // Borrar canal viejo que tenía IMPORTANCE_DEFAULT (vibraba)
            notificationManager.deleteNotificationChannel(OLD_CHANNEL_ID)

            val channel = NotificationChannel(
                CHANNEL_ID,
                "Servicio de Proximidad",
                NotificationManager.IMPORTANCE_LOW  // LOW = sin sonido ni vibración
            ).apply {
                description = "Mantiene el escaneo de beacons activo en segundo plano"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }

            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Crea la notificación para el servicio en primer plano
     */
    private fun createNotification(status: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Akistoy")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setSilent(true)  // Sin sonido ni vibración en updates
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setShowWhen(false)
            .build()
    }

    /**
     * Actualiza la notificación con información del último evento
     */
    private fun updateNotification(lastEvent: String, beaconsInProximity: Int) {
        val status = "Último: $lastEvent | En proximidad: $beaconsInProximity"
        val notification = createNotification(status)

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Fuerza la sincronización de beacons desde el servidor
     * Se ejecuta al iniciar el servicio si hay usuario logueado
     * Esto asegura que tengamos los beacons más recientes sin esperar al MainActivity
     */
    private suspend fun forceBeaconSync(tenantId: String, companyId: String) {
        try {
            Log.i(TAG, "🔄 Sincronizando beacons desde servidor...")
            // updateNotification("Sincronizando...", 0)

            val zonesResponse = withContext(Dispatchers.IO) {
                ApiClient.zonesApi.getZones(tenantId = tenantId, companyId = companyId)
            }

            if (zonesResponse.isSuccessful && zonesResponse.body() != null) {
                val zonesData = zonesResponse.body()!!
                Log.i(TAG, "✅ Se obtuvieron ${zonesData.size} zonas del servidor")

                // Extraer todos los beacons de las zonas
                val allBeacons = zonesData.flatMap { zoneResponse ->
                    zoneResponse.beacons.map { beaconResponse ->
                        com.akiestoy.beacons.model.RegisteredBeacon(
                            id = beaconResponse.id,
                            tenantId = beaconResponse.tenantId,
                            companyId = beaconResponse.companyId,
                            advUuid = beaconResponse.advUuid.lowercase(),
                            mac = beaconResponse.mac,
                            beaconName = beaconResponse.beaconName,
                            major = beaconResponse.major,
                            minor = beaconResponse.minor,
                            txPower = beaconResponse.txPower,
                            model = beaconResponse.model,
                            beaconType = beaconResponse.beaconType,
                            status = beaconResponse.status,
                            zoneName = beaconResponse.zoneName,
                            zoneId = beaconResponse.zoneId,
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                    }
                }

                // Guardar en BD (el observer actualizará automáticamente el cache)
                withContext(Dispatchers.IO) {
                    database.registeredBeaconDao().deleteAll()
                    database.registeredBeaconDao().insertBeacons(allBeacons)
                }

                // También actualizar cache directamente para uso inmediato
                registeredBeaconsCache = allBeacons

                Log.i(TAG, "✅ Sincronización completada: ${allBeacons.size} beacons listos")
                allBeacons.forEach { beacon ->
                    Log.d(TAG, "   📍 ${beacon.zoneName} - MAC: ${beacon.mac ?: "N/A"}")
                }
            } else {
                Log.e(TAG, "❌ Error al sincronizar beacons: ${zonesResponse.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error en sincronización de beacons (continuando con cache local)", e)
        }
    }
}
