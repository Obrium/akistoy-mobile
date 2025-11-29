package com.akiestoy.beacons.service

import android.annotation.SuppressLint
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
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.akiestoy.beacons.MainActivity
import com.akiestoy.beacons.R
import com.akiestoy.beacons.api.ApiClient
import com.akiestoy.beacons.data.AppDatabase
import com.akiestoy.beacons.data.FavoritesRepository
import com.akiestoy.beacons.model.RegisteredBeacon
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

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    companion object {
        private const val CHANNEL_ID = "ProximityServiceChannel"
        private const val NOTIFICATION_ID = 1002

        @Volatile
        private var isRunning = false

        fun startService(context: Context) {
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
        Log.i(TAG, "🚀 ProximityForegroundService created")

        // Adquirir Wake Lock para mantener el CPU activo con pantalla bloqueada
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AkiEstoy::BeaconScanningWakeLock"
        ).apply {
            acquire()
            Log.i(TAG, "🔋 Wake Lock adquirido - el CPU se mantendrá activo")
        }

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Inicializando..."))

        // Inicializar componentes
        initializeComponents()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "▶️ Service started (startId: $startId)")

        if (intent == null) {
            Log.w(TAG, "⚠️ Service restarted by system (intent is null)")
        }

        // Iniciar escaneo
        startProximityScanning()

        // START_STICKY: Si el sistema mata el servicio, lo reiniciará automáticamente
        // pero sin reenviar el Intent original (será null)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.w(TAG, "⚠️ Task removed - scheduling service restart")

        // Enviar broadcast para reiniciar el servicio
        val restartServiceIntent = Intent("com.akiestoy.beacons.RESTART_SERVICE")
        restartServiceIntent.setPackage(packageName)
        sendBroadcast(restartServiceIntent)

        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        isRunning = false
        Log.i(TAG, "🛑 Service destroyed")

        // Liberar Wake Lock
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.i(TAG, "🔋 Wake Lock liberado")
            }
        }

        // Detener escaneo
        try {
            proximityScanner.stopScanning()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping scanner", e)
        }

        // Limpiar tracking service
        try {
            trackingService.cleanup()
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up tracking service", e)
        }

        // Limpiar ZoneManager
        try {
            zoneManager.reset()
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting zone manager", e)
        }

        // Limpiar ZoneEventService
        try {
            zoneEventService.reset()
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting zone event service", e)
        }

        // Cancelar coroutines
        serviceScope.cancel()

        Log.d(TAG, "Final state: ${trackingService.getStateInfo()}")

        // Enviar broadcast para reiniciar el servicio si fue detenido inesperadamente
        val restartServiceIntent = Intent("com.akiestoy.beacons.RESTART_SERVICE")
        restartServiceIntent.setPackage(packageName)
        sendBroadcast(restartServiceIntent)

        Log.i(TAG, "📡 Restart broadcast sent")

        super.onDestroy()
    }

    /**
     * Inicializa los componentes del servicio
     */
    @SuppressLint("HardwareIds")
    private fun initializeComponents() {
        // Obtener device ID
        val deviceId = try {
            Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        } catch (e: Exception) {
            "unknown-device"
        }

        // Obtener device name
        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"

        // Crear FavoritesRepository
        favoritesRepository = FavoritesRepository(this)

        // Obtener base de datos y DAO
        database = AppDatabase.getDatabase(this)
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
        val pendingZoneEventDao = database.pendingZoneEventDao()
        zoneEventService = ZoneEventService(
            api = ApiClient.proximityApi,
            deviceId = deviceId,
            pendingZoneEventDao = pendingZoneEventDao
        )

        // Cargar usuario logueado y configurarlo en ZoneEventService
        serviceScope.launch {
            try {
                val user = database.userDao().getCurrentUserOnce()
                if (user != null) {
                    zoneEventService.setUser(user.rut, user.name)
                    Log.i(TAG, "👤 Usuario cargado: ${user.name} (RUT: ${user.rut})")
                } else {
                    Log.w(TAG, "⚠️ No hay usuario logueado")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error cargando usuario", e)
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
                updateNotification("Zona: $zoneName", rssi)

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

            updateNotification("Escaneando...", 0)
            Log.i(TAG, "🔍 Proximity scanning started with OPTIMIZED events (ZoneEventService)")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting proximity scanning", e)
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
     * Crea el canal de notificación
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Servicio de Proximidad",
                NotificationManager.IMPORTANCE_DEFAULT  // Cambiado a DEFAULT para que sea más visible
            ).apply {
                description = "Notificaciones del servicio de proximidad BLE - mantiene el escaneo activo en segundo plano"
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
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
            .setContentTitle("AkiEstoy - Escaneo Activo")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)  // No se puede deslizar para cerrar
            .setAutoCancel(false)  // No se cierra automáticamente
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)  // Prioridad normal para que sea visible
            .setCategory(NotificationCompat.CATEGORY_SERVICE)  // Categoría de servicio
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)  // Visible en lockscreen
            .setShowWhen(true)  // Mostrar cuándo se inició
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
}
