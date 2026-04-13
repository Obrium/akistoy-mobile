package com.akiestoy.beacons.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.akiestoy.beacons.api.ApiClient
import com.akiestoy.beacons.data.AppDatabase
import com.akiestoy.beacons.data.FavoritesRepository
import com.akiestoy.beacons.config.AppConfig
import com.akiestoy.beacons.model.BLEScanLog
import com.akiestoy.beacons.model.BeaconDetection
import com.akiestoy.beacons.model.Zone
import com.akiestoy.beacons.model.api.ConfigureBeaconRequest
import com.akiestoy.beacons.model.api.CreateZoneRequest
import com.akiestoy.beacons.scanner.BeaconScanner
import com.akiestoy.beacons.scanner.GenericBLEScanner
import com.akiestoy.beacons.service.BeaconReadingService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * ViewModel que maneja el estado de la detección de beacons
 */
class BeaconViewModel(application: Application) : AndroidViewModel(application) {

    private val beaconScanner = BeaconScanner(application)
    private val genericScanner = GenericBLEScanner(application)
    private val favoritesRepository = FavoritesRepository(application)

    // Estado de la UI
    private val _uiState = MutableStateFlow<BeaconUiState>(BeaconUiState.Idle)
    val uiState: StateFlow<BeaconUiState> = _uiState.asStateFlow()

    // Lista de beacons detectados
    private val _detections = MutableStateFlow<List<BeaconDetection>>(emptyList())
    val detections: StateFlow<List<BeaconDetection>> = _detections.asStateFlow()

    // Lista de logs de escaneo BLE (todos los paquetes - interna)
    private val _scanLogsInternal = MutableStateFlow<List<BLEScanLog>>(emptyList())

    // Lista de logs expuesta a la UI (actualizada cada 2 segundos)
    private val _scanLogs = MutableStateFlow<List<BLEScanLog>>(emptyList())
    val scanLogs: StateFlow<List<BLEScanLog>> = _scanLogs.asStateFlow()
    
    // Lista de dispositivos únicos (solo el más reciente de cada MAC)
    private val _uniqueDevices = MutableStateFlow<List<BLEScanLog>>(emptyList())
    val uniqueDevices: StateFlow<List<BLEScanLog>> = _uniqueDevices.asStateFlow()
    
    // Filtro de búsqueda
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    // Estado del filtro de favoritos (manual)
    private val _showOnlyFavorites = MutableStateFlow(false)
    val showOnlyFavorites: StateFlow<Boolean> = _showOnlyFavorites.asStateFlow()
    
    // Lista filtrada de dispositivos únicos
    private val _filteredScanLogs = MutableStateFlow<List<BLEScanLog>>(emptyList())
    val filteredScanLogs: StateFlow<List<BLEScanLog>> = _filteredScanLogs.asStateFlow()

    // Favoritos (BeaconIdentifiers con UUID + major + minor)
    val favorites: StateFlow<Set<com.akiestoy.beacons.model.BeaconIdentifier>> = favoritesRepository.favorites

    // Lista de beacons favoritos
    private val _favoriteBeacons = MutableStateFlow<List<BLEScanLog>>(emptyList())
    val favoriteBeacons: StateFlow<List<BLEScanLog>> = _favoriteBeacons.asStateFlow()

    // Mapa de beacons registrados (MAC -> zoneName) para mostrar zona en Home
    private val _registeredBeaconsZoneMap = MutableStateFlow<Map<String, String>>(emptyMap())
    val registeredBeaconsZoneMap: StateFlow<Map<String, String>> = _registeredBeaconsZoneMap.asStateFlow()

    // Estado para diálogo de configuración de beacon
    private val _beaconToConfigureFlow = MutableStateFlow<BLEScanLog?>(null)
    val beaconToConfigure: StateFlow<BLEScanLog?> = _beaconToConfigureFlow.asStateFlow()

    // Lista de zonas disponibles
    private val _zones = MutableStateFlow<List<Zone>>(emptyList())
    val zones: StateFlow<List<Zone>> = _zones.asStateFlow()

    // Estado de carga para configuración
    private val _isConfiguringBeacon = MutableStateFlow(false)
    val isConfiguringBeacon: StateFlow<Boolean> = _isConfiguringBeacon.asStateFlow()

    // Estado de carga para crear zona
    private val _isCreatingZone = MutableStateFlow(false)
    val isCreatingZone: StateFlow<Boolean> = _isCreatingZone.asStateFlow()

    // Mensaje de resultado de configuración
    private val _configurationResult = MutableStateFlow<ConfigurationResult?>(null)
    val configurationResult: StateFlow<ConfigurationResult?> = _configurationResult.asStateFlow()

    // Database
    private val database = AppDatabase.getDatabase(application)

    // Servicio para enviar lecturas de beacons cada 10 segundos
    private val beaconReadingService = BeaconReadingService(
        context = application,
        serviceScope = viewModelScope,
        favoriteBeaconsFlow = favoriteBeacons
    )

    // Jobs de las coroutines de escaneo
    private var scanLogsJob: Job? = null
    private var beaconScanJob: Job? = null
    private var autoStopJob: Job? = null
    private var filterUpdateJob: Job? = null  // Job para actualizar filtros y paquetes cada 1s
    
    // Control de logs (solo cada 5 segundos)
    private var lastLogTime = 0L

    companion object {
        private const val AUTO_STOP_DELAY_MS = 5000L // 5 segundos
    }

    init {
        // Sincronizar beacons registrados de la BD a favoritos al iniciar
        // Esto asegura que el estado ACTIVO se muestre inmediatamente si hay beacons cerca
        viewModelScope.launch {
            syncRegisteredBeaconsToFavorites()
        }

        // Observar cambios en dispositivos únicos para actualizar la lista de favoritos
        viewModelScope.launch {
            _uniqueDevices.collect { devices ->
                updateFavoriteBeacons(devices)
            }
        }

        // También observar cambios en favoritos para re-filtrar dispositivos
        viewModelScope.launch {
            favoritesRepository.favorites.collect { _ ->
                // Cuando cambian los favoritos, re-filtrar con los dispositivos actuales
                updateFavoriteBeacons(_uniqueDevices.value)
            }
        }
    }

    /**
     * Actualiza la lista de beacons favoritos (solo dispositivos únicos)
     */
    private fun updateFavoriteBeacons(devices: List<BLEScanLog>) {
        val favoriteBeacons = favoritesRepository.getFavoriteBeacons(devices)
        _favoriteBeacons.value = favoriteBeacons

        // Log para debug
        if (favoriteBeacons.isNotEmpty()) {
            Log.d("BeaconViewModel", "📍 updateFavoriteBeacons: ${favoriteBeacons.size} beacons favoritos activos")
        }
    }

    /**
     * Verifica si un beacon es favorito
     */
    fun isFavorite(macAddress: String): Boolean {
        return favoritesRepository.isFavorite(macAddress)
    }

    /**
     * Alterna el estado de favorito de un beacon usando BeaconIdentifier
     * Si se quita de favoritos, también elimina del servidor
     */
    fun toggleFavorite(identifier: com.akiestoy.beacons.model.BeaconIdentifier) {
        val wasFavorite = favorites.value.any { it.matches(identifier) }
        favoritesRepository.toggleFavorite(identifier)
        updateFavoriteBeacons(_uniqueDevices.value)

        // Si se quitó de favoritos, eliminar del servidor
        if (wasFavorite) {
            deleteBeaconFromServer(identifier)
        }
    }

    /**
     * Alterna el estado de favorito de un beacon usando MAC address (compatibilidad)
     * NOTA: Este método es menos preciso, se recomienda usar toggleFavorite(BeaconIdentifier)
     */
    fun toggleFavorite(macAddress: String) {
        favoritesRepository.toggleFavorite(macAddress)
        updateFavoriteBeacons(_uniqueDevices.value)
    }

    /**
     * Elimina un beacon del servidor usando sus identifiers (UUID + major + minor)
     */
    private fun deleteBeaconFromServer(identifier: com.akiestoy.beacons.model.BeaconIdentifier) {
        viewModelScope.launch {
            try {
                // Obtener usuario actual para token y tenantId
                val user = database.userDao().getCurrentUserOnce()
                if (user == null) {
                    Log.w("BeaconViewModel", "⚠️ No hay usuario autenticado para eliminar beacon")
                    return@launch
                }

                Log.i("BeaconViewModel", "🗑️ Eliminando beacon del servidor: uuid=${identifier.uuid}, major=${identifier.major}, minor=${identifier.minor}")

                val response = ApiClient.authApi.deleteBeaconByIdentifiers(
                    tenantId = user.tenantId,
                    uuid = identifier.uuid,
                    major = identifier.major,
                    minor = identifier.minor,
                    authorization = "Bearer ${user.accessToken}"
                )

                if (response.isSuccessful) {
                    Log.i("BeaconViewModel", "✅ Beacon eliminado del servidor exitosamente")
                    _configurationResult.value = ConfigurationResult.Success("Beacon eliminado del servidor")
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Error desconocido"
                    Log.e("BeaconViewModel", "❌ Error al eliminar beacon del servidor: ${response.code()} - $errorBody")
                    // No mostrar error al usuario, el favorito local ya se quitó
                }
            } catch (e: Exception) {
                Log.e("BeaconViewModel", "❌ Excepción al eliminar beacon del servidor", e)
                // No mostrar error al usuario, el favorito local ya se quitó
            }
        }
    }

    /**
     * Inicia el escaneo de beacons
     * El escaneo se mantiene activo hasta que se llame a stopScanning()
     */
    fun startScanning() {
        if (beaconScanner.isScanning()) {
            Log.d("BeaconViewModel", "Scanner already running, skipping...")
            return
        }

        // Cancelar jobs anteriores si existen
        scanLogsJob?.cancel()
        beaconScanJob?.cancel()
        autoStopJob?.cancel()

        // NO limpiar logs - mantenerlos para historial continuo
        // Los logs solo se limpian con el botón "Eliminar"

        // Iniciar scanner genérico para debug
        Log.d("BeaconViewModel", "Starting GENERIC BLE scanner (continuous mode)...")
        genericScanner.startScanning()

        // Recopilar TODOS los paquetes en tiempo real (no actualizar, sino agregar)
        scanLogsJob = viewModelScope.launch {
            genericScanner.scanLogs.collect { newLog ->
                // 1. Agregar a todos los paquetes (interno)
                val currentLogs = _scanLogsInternal.value.toMutableList()
                currentLogs.add(0, newLog)
                
                // Limitar a 500 paquetes totales para mantener historial amplio
                if (currentLogs.size > 500) {
                    currentLogs.removeAt(currentLogs.size - 1)
                }
                _scanLogsInternal.value = currentLogs
                
                // 2. Actualizar dispositivos únicos (para pantalla Scanner)
                val currentDevices = _uniqueDevices.value.toMutableList()
                val existingIndex = currentDevices.indexOfFirst { it.macAddress == newLog.macAddress }
                
                if (existingIndex >= 0) {
                    // Actualizar dispositivo existente con el paquete más reciente
                    currentDevices[existingIndex] = newLog
                } else {
                    // Agregar nuevo dispositivo al principio
                    currentDevices.add(0, newLog)
                }
                
                _uniqueDevices.value = currentDevices
                // NO aplicar filtro aquí - se aplica cada 2s en otro job
                
                // Log solo cada 5 segundos para no saturar la consola
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastLogTime >= AppConfig.LOG_INTERVAL_MS) {
                    Log.d("BeaconViewModel", "📊 Scan status: ${currentLogs.size} packets | ${currentDevices.size} devices | Latest: ${newLog.macAddress}")
                    lastLogTime = currentTime
                }
            }
        }
        
        // Job separado para actualizar filtros y paquetes cada 2 segundos
        filterUpdateJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(AppConfig.FILTER_UPDATE_INTERVAL_MS)
                // Actualizar filtros de Scanner
                applySearchFilter()
                // Actualizar paquetes para pantalla Packets
                _scanLogs.value = _scanLogsInternal.value
            }
        }

        beaconScanJob = viewModelScope.launch {
            _uiState.value = BeaconUiState.Scanning

            beaconScanner.startScanning()
                .catch { e ->
                    _uiState.value = BeaconUiState.Error(e.message ?: "Error desconocido")
                }
                .collect { beaconList ->
                    _detections.value = beaconList
                    // Actualizar estado basado en dispositivos únicos detectados o beacons filtrados
                    val totalDevices = _uniqueDevices.value.size
                    _uiState.value = when {
                        beaconList.isNotEmpty() -> BeaconUiState.DetectingBeacons(beaconList.size)
                        totalDevices > 0 -> BeaconUiState.ScanningWithDevices(totalDevices)
                        else -> BeaconUiState.Scanning
                    }
                }
        }

        // YA NO hay detención automática - el escaneo continúa hasta que se detenga manualmente
        Log.d("BeaconViewModel", "Scanner started in CONTINUOUS mode - will run until manually stopped")
    }

    /**
     * Detiene el escaneo de beacons
     */
    fun stopScanning() {
        Log.d("BeaconViewModel", "Stopping scanners...")

        // Cancelar las coroutines de escaneo, el timer automático y el filtro
        scanLogsJob?.cancel()
        beaconScanJob?.cancel()
        autoStopJob?.cancel()
        filterUpdateJob?.cancel()
        scanLogsJob = null
        beaconScanJob = null
        autoStopJob = null
        filterUpdateJob = null

        // Detener los scanners
        genericScanner.stopScanning()

        // Detener el envío de lecturas
        beaconReadingService.stopSending()

        // Actualizar el estado
        _uiState.value = BeaconUiState.Idle
        _detections.value = emptyList()
        // No limpiamos los logs para que puedan ser revisados después de detener

        Log.d("BeaconViewModel", "Scanners stopped successfully")
    }

    /**
     * Limpia todos los logs de escaneo
     */
    fun clearLogs() {
        _scanLogsInternal.value = emptyList()
        _scanLogs.value = emptyList()
        _uniqueDevices.value = emptyList()
        _filteredScanLogs.value = emptyList()
    }

    /**
     * Actualiza el filtro de búsqueda
     */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }
    
    /**
     * Alterna el filtro de solo favoritos
     */
    fun toggleFavoritesFilter() {
        _showOnlyFavorites.value = !_showOnlyFavorites.value
        Log.d("BeaconViewModel", "Filtro de favoritos: ${if (_showOnlyFavorites.value) "ACTIVADO" else "DESACTIVADO"}")
    }

    /**
     * Inicia el proceso de configuración de un beacon
     * Carga las zonas del servidor y muestra el diálogo
     */
    fun startBeaconConfiguration(scanLog: BLEScanLog) {
        Log.i("BeaconViewModel", "🔧 startBeaconConfiguration llamado para MAC: ${scanLog.macAddress}")
        Log.i("BeaconViewModel", "🔧 iBeaconData: ${scanLog.iBeaconData}")

        // Permitir configuración incluso sin iBeaconData (para dispositivos BLE genéricos)
        if (scanLog.iBeaconData == null) {
            Log.w("BeaconViewModel", "⚠️ Dispositivo sin datos iBeacon, configurando como BLE genérico")
        }

        viewModelScope.launch {
            try {
                Log.i("BeaconViewModel", "🔍 Cargando zonas desde el servidor...")

                // Usar IDs del usuario o los fijos de testing
                val user = database.userDao().getCurrentUserOnce()
                val tenantId = user?.tenantId
                val companyId = user?.companyId
                if (tenantId.isNullOrBlank() || companyId.isNullOrBlank()) {
                    Log.e("BeaconViewModel", "❌ No hay sesión activa (tenantId/companyId faltante)")
                    _configurationResult.value = ConfigurationResult.Error("Inicie sesión antes de usar esta función")
                    return@launch
                }

                Log.i("BeaconViewModel", "📍 Usando tenantId: $tenantId, companyId: $companyId")

                // Cargar zonas directamente del servidor
                val response = ApiClient.authApi.getZones(tenantId, companyId)

                if (response.isSuccessful && response.body() != null) {
                    val zonesFromServer = response.body()!!.map { zoneResponse ->
                        Zone(
                            id = zoneResponse.id,
                            tenantId = zoneResponse.tenantId,
                            companyId = zoneResponse.companyId,
                            name = zoneResponse.name,
                            type = zoneResponse.type,
                            rssiThresholdNear = zoneResponse.rssiThresholdNear,
                            rssiThresholdFar = zoneResponse.rssiThresholdFar,
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                    }
                    _zones.value = zonesFromServer
                    Log.i("BeaconViewModel", "✅ Cargadas ${zonesFromServer.size} zonas del servidor")
                    zonesFromServer.forEach { zone ->
                        Log.d("BeaconViewModel", "   📍 Zona: ${zone.name} (${zone.id})")
                    }
                } else {
                    Log.w("BeaconViewModel", "⚠️ Error al cargar zonas del servidor: ${response.code()}")
                    // Intentar cargar de la base de datos local como fallback
                    val zonesFromDb = database.zoneDao().getAllZonesOnce()
                    _zones.value = zonesFromDb
                    Log.i("BeaconViewModel", "📦 Usando ${zonesFromDb.size} zonas de la base de datos local")
                }

                // Mostrar el diálogo
                Log.i("BeaconViewModel", "📱 Mostrando diálogo de configuración...")
                _beaconToConfigureFlow.value = scanLog
            } catch (e: Exception) {
                Log.e("BeaconViewModel", "❌ Error al cargar zonas", e)
                // Intentar cargar de la base de datos local como fallback
                try {
                    val zonesFromDb = database.zoneDao().getAllZonesOnce()
                    _zones.value = zonesFromDb
                    Log.i("BeaconViewModel", "📦 Fallback: ${zonesFromDb.size} zonas de la base de datos local")
                    _beaconToConfigureFlow.value = scanLog
                } catch (dbError: Exception) {
                    _configurationResult.value = ConfigurationResult.Error("Error al cargar zonas: ${e.message}")
                }
            }
        }
    }

    /**
     * Crea una nueva zona en el servidor
     */
    fun createZone(zoneName: String) {
        viewModelScope.launch {
            _isCreatingZone.value = true

            try {
                val user = database.userDao().getCurrentUserOnce()
                val tenantId = user?.tenantId
                val companyId = user?.companyId
                if (tenantId.isNullOrBlank() || companyId.isNullOrBlank()) {
                    Log.e("BeaconViewModel", "❌ No hay sesión activa (tenantId/companyId faltante)")
                    _configurationResult.value = ConfigurationResult.Error("Inicie sesión antes de usar esta función")
                    return@launch
                }
                val token = user?.accessToken

                Log.i("BeaconViewModel", "🏗️ Creando zona: $zoneName")
                Log.i("BeaconViewModel", "   tenantId: $tenantId, companyId: $companyId")

                // Si no hay token, intentar crear sin autenticación (puede fallar dependiendo del backend)
                val authHeader = if (token != null) "Bearer $token" else ""

                val request = CreateZoneRequest(
                    tenantId = tenantId,
                    companyId = companyId,
                    name = zoneName,
                    type = "room"
                )

                val response = ApiClient.authApi.createZone(
                    tenantId = tenantId,
                    authorization = authHeader,
                    request = request
                )

                if (response.isSuccessful && response.body() != null) {
                    val createdZone = response.body()!!
                    Log.i("BeaconViewModel", "✅ Zona creada: ${createdZone.zone.name} (${createdZone.zone.id})")

                    // Agregar la nueva zona a la lista
                    val newZone = Zone(
                        id = createdZone.zone.id,
                        tenantId = tenantId,
                        companyId = companyId,
                        name = createdZone.zone.name,
                        type = "room",
                        rssiThresholdNear = -65,
                        rssiThresholdFar = -80,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                    _zones.value = _zones.value + newZone

                    _configurationResult.value = ConfigurationResult.Success("Zona '${zoneName}' creada exitosamente")
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Error desconocido"
                    Log.e("BeaconViewModel", "❌ Error al crear zona: ${response.code()} - $errorBody")
                    _configurationResult.value = ConfigurationResult.Error("Error al crear zona: $errorBody")
                }
            } catch (e: Exception) {
                Log.e("BeaconViewModel", "❌ Excepción al crear zona", e)
                _configurationResult.value = ConfigurationResult.Error("Error de conexión: ${e.message}")
            } finally {
                _isCreatingZone.value = false
            }
        }
    }

    /**
     * Cancela el diálogo de configuración
     */
    fun cancelBeaconConfiguration() {
        _beaconToConfigureFlow.value = null
        _isConfiguringBeacon.value = false
    }

    /**
     * Confirma y envía la configuración del beacon al servidor
     */
    fun confirmBeaconConfiguration(beaconName: String, zoneName: String) {
        val scanLog = _beaconToConfigureFlow.value ?: return
        val iBeacon = scanLog.iBeaconData

        viewModelScope.launch {
            _isConfiguringBeacon.value = true

            try {
                // Obtener usuario actual o usar IDs fijos de testing
                val user = database.userDao().getCurrentUserOnce()
                val tenantId = user?.tenantId
                val companyId = user?.companyId
                if (tenantId.isNullOrBlank() || companyId.isNullOrBlank()) {
                    Log.e("BeaconViewModel", "❌ No hay sesión activa (tenantId/companyId faltante)")
                    _configurationResult.value = ConfigurationResult.Error("Inicie sesión antes de usar esta función")
                    return@launch
                }
                val authHeader = if (user?.accessToken != null) "Bearer ${user.accessToken}" else ""

                Log.i("BeaconViewModel", "📍 Configurando beacon con tenantId: $tenantId, companyId: $companyId")

                // Para dispositivos sin iBeaconData, generar UUID basado en MAC y usar valores por defecto
                val uuid = iBeacon?.uuid ?: java.util.UUID.nameUUIDFromBytes(scanLog.macAddress.toByteArray()).toString()
                val major = iBeacon?.major ?: 0
                val minor = iBeacon?.minor ?: 0
                val txPower = iBeacon?.txPower ?: -59

                Log.i("BeaconViewModel", "📍 Beacon: uuid=$uuid, major=$major, minor=$minor, mac=${scanLog.macAddress}")

                val request = ConfigureBeaconRequest(
                    mac = scanLog.macAddress,
                    uuid = uuid,
                    major = major,
                    minor = minor,
                    beaconName = beaconName,
                    zoneName = zoneName,
                    beaconType = "tracking",
                    tenantId = tenantId,
                    companyId = companyId,
                    txPower = txPower,
                    model = "ESP32"
                )

                Log.d("BeaconViewModel", "Enviando configuración de beacon: $request")

                val response = ApiClient.authApi.configureBeacon(
                    tenantId = tenantId,
                    authorization = authHeader,
                    request = request
                )

                if (response.isSuccessful) {
                    Log.i("BeaconViewModel", "Beacon configurado exitosamente: ${response.body()?.message}")
                    _configurationResult.value = ConfigurationResult.Success("Beacon '$beaconName' configurado en zona '$zoneName'")

                    // Agregar a favoritos automáticamente
                    val identifier = com.akiestoy.beacons.model.BeaconIdentifier(
                        uuid = uuid,
                        major = major,
                        minor = minor,
                        macAddress = scanLog.macAddress
                    )
                    favoritesRepository.addFavorites(listOf(identifier))
                    updateFavoriteBeacons(_uniqueDevices.value)

                    // Cerrar el diálogo
                    _beaconToConfigureFlow.value = null
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Error desconocido"
                    Log.e("BeaconViewModel", "Error al configurar beacon: ${response.code()} - $errorBody")
                    _configurationResult.value = ConfigurationResult.Error("Error ${response.code()}: $errorBody")
                }
            } catch (e: Exception) {
                Log.e("BeaconViewModel", "Excepción al configurar beacon", e)
                _configurationResult.value = ConfigurationResult.Error("Error de conexión: ${e.message}")
            } finally {
                _isConfiguringBeacon.value = false
            }
        }
    }

    /**
     * Limpia el resultado de configuración
     */
    fun clearConfigurationResult() {
        _configurationResult.value = null
    }

    /**
     * Aplica el filtro de búsqueda a los dispositivos únicos
     * Considera: iBeacons, favoritos y búsqueda de texto
     */
    private fun applySearchFilter() {
        val query = _searchQuery.value.trim().lowercase()
        val showOnlyFavs = _showOnlyFavorites.value
        val favoriteIdentifiers = favorites.value

        // 1. Empezar con solo iBeacons
        var filtered = _uniqueDevices.value.filter { log ->
            log.iBeaconData != null
        }

        // 2. Aplicar filtro de favoritos si está activo
        if (showOnlyFavs) {
            filtered = filtered.filter { log ->
                val identifier = com.akiestoy.beacons.model.BeaconIdentifier.fromScanLog(log)
                identifier != null && favoriteIdentifiers.any { it.matches(identifier) }
            }
        }
        
        // 3. Aplicar búsqueda de texto si hay query
        if (query.isNotEmpty()) {
            filtered = filtered.filter { log ->
                log.deviceName.lowercase().contains(query) ||
                log.macAddress.lowercase().contains(query) ||
                log.iBeaconData?.uuid?.lowercase()?.contains(query) == true ||
                log.iBeaconData?.major?.toString()?.contains(query) == true ||
                log.iBeaconData?.minor?.toString()?.contains(query) == true
            }
        }
        
        _filteredScanLogs.value = filtered
    }

    /**
     * Sincroniza los beacons registrados de la BD a los favoritos locales
     * Esto se llama al iniciar el ViewModel para asegurar que los favoritos estén poblados
     */
    private suspend fun syncRegisteredBeaconsToFavorites() {
        try {
            val registeredBeacons = database.registeredBeaconDao().getAllActiveBeaconsOnce()

            if (registeredBeacons.isEmpty()) {
                Log.d("BeaconViewModel", "📍 No hay beacons registrados en la BD para sincronizar")
                return
            }

            Log.i("BeaconViewModel", "🔄 Sincronizando ${registeredBeacons.size} beacons registrados a favoritos...")

            val identifiersToAdd = registeredBeacons.map { beacon ->
                com.akiestoy.beacons.model.BeaconIdentifier(
                    uuid = beacon.advUuid,
                    major = beacon.major,
                    minor = beacon.minor,
                    macAddress = beacon.mac ?: ""
                )
            }

            if (identifiersToAdd.isNotEmpty()) {
                favoritesRepository.addFavorites(identifiersToAdd)
                Log.i("BeaconViewModel", "✅ Sincronizados ${identifiersToAdd.size} beacons registrados a favoritos en init")
            }
        } catch (e: Exception) {
            Log.e("BeaconViewModel", "❌ Error al sincronizar beacons registrados", e)
        }
    }

    /**
     * Inicia el escaneo automático de beacons registrados
     * Se conectará automáticamente al beacon más cercano
     */
    fun startAutoScanning() {
        viewModelScope.launch {
            try {
                val database = com.akiestoy.beacons.data.AppDatabase.getDatabase(getApplication())
                val registeredBeacons = database.registeredBeaconDao().getAllActiveBeaconsOnce()

                if (registeredBeacons.isEmpty()) {
                    Log.w("BeaconViewModel", "⚠️ No hay beacons registrados para escanear")
                    return@launch
                }

                Log.i("BeaconViewModel", "🔍 Iniciando escaneo automático de ${registeredBeacons.size} beacons registrados")

                // Sincronizar beacons registrados a favoritos locales
                // Esto asegura que si se reinstala la app, los favoritos se recuperan
                val identifiersToAdd = registeredBeacons.map { beacon ->
                    Log.i("BeaconViewModel", "   📍 ${beacon.zoneName}: UUID=${beacon.advUuid}, major=${beacon.major}, minor=${beacon.minor}")
                    com.akiestoy.beacons.model.BeaconIdentifier(
                        uuid = beacon.advUuid,
                        major = beacon.major,
                        minor = beacon.minor,
                        macAddress = "" // No tenemos MAC en el registro, se actualizará cuando se detecte
                    )
                }
                if (identifiersToAdd.isNotEmpty()) {
                    favoritesRepository.addFavorites(identifiersToAdd)
                    Log.i("BeaconViewModel", "✅ Sincronizados ${identifiersToAdd.size} beacons registrados a favoritos")
                }

                // Iniciar el escaneo
                startScanning()

                // Iniciar el envío periódico de lecturas
                beaconReadingService.startSending()

                // Monitorear beacons detectados y actualizar dinámicamente el beacon activo
                var lastActiveBeaconMac: String? = null
                var lastActiveBeaconTimestamp: Long = 0L
                val BEACON_TIMEOUT_MS = 10_000L // 10 segundos de tolerancia antes de desconectar

                viewModelScope.launch {
                    uniqueDevices.collect { devices ->
                        val currentTime = System.currentTimeMillis()

                        if (devices.isEmpty()) {
                            // Si no hay dispositivos pero aún estamos dentro del timeout, mantener estado activo
                            if (lastActiveBeaconMac != null && (currentTime - lastActiveBeaconTimestamp) > BEACON_TIMEOUT_MS) {
                                Log.i("BeaconViewModel", "⚠️ Timeout de ${BEACON_TIMEOUT_MS/1000}s alcanzado sin señal, desconectando...")
                                lastActiveBeaconMac = null
                                viewModelScope.launch {
                                    favoritesRepository.clearFavorites()
                                }
                            }
                            return@collect
                        }

                        // Filtrar solo dispositivos iBeacon
                        val iBeaconDevices = devices.filter { it.iBeaconData != null }

                        // Si no hay iBeacons detectados, verificar timeout
                        if (iBeaconDevices.isEmpty()) {
                            if (lastActiveBeaconMac != null && (currentTime - lastActiveBeaconTimestamp) > BEACON_TIMEOUT_MS) {
                                Log.i("BeaconViewModel", "⚠️ Timeout de ${BEACON_TIMEOUT_MS/1000}s sin iBeacons, desconectando...")
                                lastActiveBeaconMac = null
                                viewModelScope.launch {
                                    favoritesRepository.clearFavorites()
                                }
                            }
                            return@collect
                        }

                        // Buscar beacons registrados en los dispositivos detectados
                        // Comparar por UUID + major + minor para identificación precisa
                        // Filtrar solo beacons vistos en los últimos 10 segundos
                        val tenSecondsAgo = currentTime - BEACON_TIMEOUT_MS
                        val recentIBeaconDevices = iBeaconDevices.filter { it.timestamp > tenSecondsAgo }

                        val detectedRegisteredBeacons = recentIBeaconDevices.mapNotNull { device ->
                            val iBeacon = device.iBeaconData!!

                            // PRIORIDAD 1: Buscar por MAC address (más confiable)
                            var matchingBeacon = registeredBeacons.find { beacon ->
                                !beacon.mac.isNullOrEmpty() &&
                                device.macAddress.equals(beacon.mac, ignoreCase = true)
                            }

                            if (matchingBeacon != null) {
                                Log.d("BeaconViewModel", "✅ Match por MAC: ${matchingBeacon.zoneName} (MAC=${device.macAddress}, RSSI=${device.rssi})")
                            } else {
                                // PRIORIDAD 2: Buscar por UUID + major + minor
                                matchingBeacon = registeredBeacons.find { beacon ->
                                    iBeacon.uuid.lowercase() == beacon.advUuid.lowercase() &&
                                    iBeacon.major == beacon.major &&
                                    iBeacon.minor == beacon.minor
                                }
                                if (matchingBeacon != null) {
                                    Log.d("BeaconViewModel", "✅ Match por UUID+major+minor: ${matchingBeacon.zoneName} (UUID=${iBeacon.uuid}, major=${iBeacon.major}, minor=${iBeacon.minor}, MAC=${device.macAddress}, RSSI=${device.rssi})")
                                }
                            }

                            if (matchingBeacon != null) {
                                Pair(device, matchingBeacon)
                            } else {
                                null
                            }
                        }

                        if (detectedRegisteredBeacons.isNotEmpty()) {
                            // Ordenar por RSSI (señal más fuerte primero) para encontrar el más cercano
                            val closestBeacon = detectedRegisteredBeacons.maxByOrNull { it.first.rssi }

                            if (closestBeacon != null) {
                                val (device, registeredBeacon) = closestBeacon

                                // Actualizar timestamp de última señal válida
                                lastActiveBeaconTimestamp = device.timestamp

                                // Solo loguear si cambió el beacon activo
                                if (lastActiveBeaconMac != device.macAddress) {
                                    Log.i("BeaconViewModel", "🔄 Cambiando a beacon más cercano: ${registeredBeacon.zoneName} (UUID: ${registeredBeacon.advUuid}, major: ${registeredBeacon.major}, minor: ${registeredBeacon.minor}, MAC: ${device.macAddress}, RSSI: ${device.rssi})")
                                    lastActiveBeaconMac = device.macAddress
                                }

                                // IMPORTANTE: Actualizar favoritos para que solo el beacon más cercano esté activo
                                // Usar UUID + major + minor para identificar el beacon correctamente
                                viewModelScope.launch {
                                    val beaconIdentifier = com.akiestoy.beacons.model.BeaconIdentifier.fromScanLog(device)
                                    if (beaconIdentifier != null) {
                                        // Limpiar favoritos anteriores
                                        favoritesRepository.clearFavorites()
                                        // Marcar solo el beacon más cercano como favorito usando UUID+major+minor
                                        favoritesRepository.addFavorites(listOf(beaconIdentifier))
                                        Log.d("BeaconViewModel", "⭐ Beacon activo: ${registeredBeacon.zoneName} (UUID: ${beaconIdentifier.uuid}, major: ${beaconIdentifier.major}, minor: ${beaconIdentifier.minor}, RSSI: ${device.rssi})")
                                    }
                                }
                            }
                        } else {
                            // Hay iBeacons pero ninguno coincide con los registrados
                            // Solo desconectar si pasó el timeout desde la última señal válida
                            if (lastActiveBeaconMac != null && (currentTime - lastActiveBeaconTimestamp) > BEACON_TIMEOUT_MS) {
                                Log.i("BeaconViewModel", "⚠️ Timeout de ${BEACON_TIMEOUT_MS/1000}s sin beacons registrados cerca, desconectando...")
                                lastActiveBeaconMac = null
                                viewModelScope.launch {
                                    favoritesRepository.clearFavorites()
                                }
                            }
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e("BeaconViewModel", "❌ Error al iniciar escaneo automático", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopScanning()
    }
}

/**
 * Estados posibles de la UI
 */
sealed class BeaconUiState {
    data object Idle : BeaconUiState()
    data object Scanning : BeaconUiState()
    data class ScanningWithDevices(val deviceCount: Int) : BeaconUiState()
    data class DetectingBeacons(val count: Int) : BeaconUiState()
    data class Error(val message: String) : BeaconUiState()
}

/**
 * Resultado de la configuración de un beacon
 */
sealed class ConfigurationResult {
    data class Success(val message: String) : ConfigurationResult()
    data class Error(val message: String) : ConfigurationResult()
}
