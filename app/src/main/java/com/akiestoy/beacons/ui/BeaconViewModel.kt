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

    // Estado para diálogo de configuración de beacon
    private val _beaconToConfigureFlow = MutableStateFlow<BLEScanLog?>(null)
    val beaconToConfigure: StateFlow<BLEScanLog?> = _beaconToConfigureFlow.asStateFlow()

    // Lista de zonas disponibles
    private val _zones = MutableStateFlow<List<Zone>>(emptyList())
    val zones: StateFlow<List<Zone>> = _zones.asStateFlow()

    // Estado de carga para configuración
    private val _isConfiguringBeacon = MutableStateFlow(false)
    val isConfiguringBeacon: StateFlow<Boolean> = _isConfiguringBeacon.asStateFlow()

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
        // Observar cambios en dispositivos únicos o favoritos para actualizar la lista de favoritos
        viewModelScope.launch {
            _uniqueDevices.collect { devices ->
                updateFavoriteBeacons(devices)
            }
        }
    }

    /**
     * Actualiza la lista de beacons favoritos (solo dispositivos únicos)
     */
    private fun updateFavoriteBeacons(devices: List<BLEScanLog>) {
        _favoriteBeacons.value = favoritesRepository.getFavoriteBeacons(devices)
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
     * Carga las zonas y muestra el diálogo
     */
    fun startBeaconConfiguration(scanLog: BLEScanLog) {
        Log.i("BeaconViewModel", "🔧 startBeaconConfiguration llamado para MAC: ${scanLog.macAddress}")
        Log.i("BeaconViewModel", "🔧 iBeaconData: ${scanLog.iBeaconData}")

        if (scanLog.iBeaconData == null) {
            Log.w("BeaconViewModel", "⚠️ No se puede configurar un dispositivo sin datos iBeacon")
            _configurationResult.value = ConfigurationResult.Error("Este dispositivo no es un iBeacon válido")
            return
        }

        viewModelScope.launch {
            try {
                Log.i("BeaconViewModel", "🔍 Cargando zonas desde la base de datos...")
                // Cargar zonas desde la base de datos local
                val zonesFromDb = database.zoneDao().getAllZonesOnce()
                _zones.value = zonesFromDb
                Log.i("BeaconViewModel", "✅ Cargadas ${zonesFromDb.size} zonas para configuración")
                zonesFromDb.forEach { zone ->
                    Log.d("BeaconViewModel", "   📍 Zona: ${zone.name} (${zone.id})")
                }

                // Mostrar el diálogo
                Log.i("BeaconViewModel", "📱 Mostrando diálogo de configuración...")
                _beaconToConfigureFlow.value = scanLog
            } catch (e: Exception) {
                Log.e("BeaconViewModel", "❌ Error al cargar zonas", e)
                _configurationResult.value = ConfigurationResult.Error("Error al cargar zonas: ${e.message}")
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
        val iBeacon = scanLog.iBeaconData ?: return

        viewModelScope.launch {
            _isConfiguringBeacon.value = true

            try {
                // Obtener usuario actual para token y datos del tenant
                val user = database.userDao().getCurrentUserOnce()
                if (user == null) {
                    _configurationResult.value = ConfigurationResult.Error("No hay usuario autenticado")
                    _isConfiguringBeacon.value = false
                    return@launch
                }

                val request = ConfigureBeaconRequest(
                    mac = scanLog.macAddress,
                    uuid = iBeacon.uuid,
                    major = iBeacon.major,
                    minor = iBeacon.minor,
                    beaconName = beaconName,
                    zoneName = zoneName,
                    beaconType = "tracking",
                    tenantId = user.tenantId,
                    companyId = user.companyId,
                    txPower = iBeacon.txPower,
                    model = "ESP32"
                )

                Log.d("BeaconViewModel", "Enviando configuración de beacon: $request")

                val response = ApiClient.authApi.configureBeacon(
                    tenantId = user.tenantId,
                    authorization = "Bearer ${user.accessToken}",
                    request = request
                )

                if (response.isSuccessful) {
                    Log.i("BeaconViewModel", "Beacon configurado exitosamente: ${response.body()?.message}")
                    _configurationResult.value = ConfigurationResult.Success("Beacon '$beaconName' configurado en zona '$zoneName'")

                    // Agregar a favoritos automáticamente
                    val identifier = com.akiestoy.beacons.model.BeaconIdentifier(
                        uuid = iBeacon.uuid,
                        major = iBeacon.major,
                        minor = iBeacon.minor,
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
                registeredBeacons.forEach { beacon ->
                    Log.i("BeaconViewModel", "   📍 ${beacon.zoneName}: UUID=${beacon.advUuid}, major=${beacon.major}, minor=${beacon.minor}")
                }

                // Iniciar el escaneo
                startScanning()

                // Iniciar el envío periódico de lecturas
                beaconReadingService.startSending()

                // Monitorear beacons detectados y actualizar dinámicamente el beacon activo
                var lastActiveBeaconMac: String? = null

                viewModelScope.launch {
                    uniqueDevices.collect { devices ->
                        if (devices.isEmpty()) return@collect

                        // Buscar beacons registrados en los dispositivos detectados
                        // Comparar por UUID + major + minor para identificación precisa
                        val detectedRegisteredBeacons = devices.mapNotNull { device ->
                            // Solo procesar si tiene datos de iBeacon
                            val iBeacon = device.iBeaconData ?: return@mapNotNull null

                            // Buscar en beacons registrados si UUID + major + minor coinciden
                            val matchingBeacon = registeredBeacons.find { beacon ->
                                iBeacon.uuid.lowercase() == beacon.advUuid.lowercase() &&
                                iBeacon.major == beacon.major &&
                                iBeacon.minor == beacon.minor
                            }

                            if (matchingBeacon != null) {
                                Log.d("BeaconViewModel", "✅ Match por UUID+major+minor: ${matchingBeacon.zoneName} (UUID=${iBeacon.uuid}, major=${iBeacon.major}, minor=${iBeacon.minor}, MAC=${device.macAddress}, RSSI=${device.rssi})")
                                Pair(device, matchingBeacon)
                            } else {
                                Log.v("BeaconViewModel", "⚠️ No match: UUID=${iBeacon.uuid}, major=${iBeacon.major}, minor=${iBeacon.minor}, MAC=${device.macAddress}")
                                null
                            }
                        }

                        if (detectedRegisteredBeacons.isNotEmpty()) {
                            // Ordenar por RSSI (señal más fuerte primero) para encontrar el más cercano
                            val closestBeacon = detectedRegisteredBeacons.maxByOrNull { it.first.rssi }

                            if (closestBeacon != null) {
                                val (device, registeredBeacon) = closestBeacon

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
                            // No hay beacons registrados cerca, limpiar favoritos
                            if (lastActiveBeaconMac != null) {
                                Log.i("BeaconViewModel", "⚠️ No hay beacons registrados cerca, desconectando...")
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
