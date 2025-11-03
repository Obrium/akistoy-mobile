package com.akiestoy.beacons.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.akiestoy.beacons.data.FavoritesRepository
import com.akiestoy.beacons.model.BLEScanLog
import com.akiestoy.beacons.model.BeaconDetection
import com.akiestoy.beacons.scanner.BeaconScanner
import com.akiestoy.beacons.scanner.GenericBLEScanner
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
    
    // Lista de logs expuesta a la UI (actualizada cada 1 segundo)
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

    // Favoritos
    val favorites: StateFlow<Set<String>> = favoritesRepository.favorites
    
    // Lista de beacons favoritos
    private val _favoriteBeacons = MutableStateFlow<List<BLEScanLog>>(emptyList())
    val favoriteBeacons: StateFlow<List<BLEScanLog>> = _favoriteBeacons.asStateFlow()

    // Jobs de las coroutines de escaneo
    private var scanLogsJob: Job? = null
    private var beaconScanJob: Job? = null
    private var autoStopJob: Job? = null
    private var filterUpdateJob: Job? = null  // Job para actualizar filtros y paquetes cada 1s
    
    // Control de logs (solo cada 5 segundos)
    private var lastLogTime = 0L

    companion object {
        private const val AUTO_STOP_DELAY_MS = 5000L // 5 segundos
        private const val LOG_INTERVAL_MS = 5000L // Intervalo entre logs
        private const val FILTER_UPDATE_INTERVAL_MS = 1000L // Actualizar filtros cada 1 segundo
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
     * Alterna el estado de favorito de un beacon
     */
    fun toggleFavorite(macAddress: String) {
        favoritesRepository.toggleFavorite(macAddress)
        updateFavoriteBeacons(_uniqueDevices.value)
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
                // NO aplicar filtro aquí - se aplica cada 0.5s en otro job
                
                // Log solo cada 5 segundos para no saturar la consola
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastLogTime >= LOG_INTERVAL_MS) {
                    Log.d("BeaconViewModel", "📊 Scan status: ${currentLogs.size} packets | ${currentDevices.size} devices | Latest: ${newLog.macAddress}")
                    lastLogTime = currentTime
                }
            }
        }
        
        // Job separado para actualizar filtros y paquetes cada 1 segundo
        filterUpdateJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(FILTER_UPDATE_INTERVAL_MS)
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
     * Aplica el filtro de búsqueda a los dispositivos únicos
     * Considera: iBeacons, favoritos y búsqueda de texto
     */
    private fun applySearchFilter() {
        val query = _searchQuery.value.trim().lowercase()
        val showOnlyFavs = _showOnlyFavorites.value
        val favoritesMacs = favorites.value
        
        // 1. Empezar con solo iBeacons
        var filtered = _uniqueDevices.value.filter { log ->
            log.iBeaconData != null
        }
        
        // 2. Aplicar filtro de favoritos si está activo
        if (showOnlyFavs) {
            filtered = filtered.filter { log ->
                favoritesMacs.contains(log.macAddress)
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
