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

    // Lista de logs de escaneo BLE (todos)
    private val _scanLogs = MutableStateFlow<List<BLEScanLog>>(emptyList())
    val scanLogs: StateFlow<List<BLEScanLog>> = _scanLogs.asStateFlow()
    
    // Filtro de búsqueda
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    // Lista filtrada de logs
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

    companion object {
        private const val AUTO_STOP_DELAY_MS = 5000L // 5 segundos
    }

    init {
        // Observar cambios en logs o favoritos para actualizar la lista de favoritos
        viewModelScope.launch {
            _scanLogs.collect { logs ->
                updateFavoriteBeacons(logs)
            }
        }
    }

    /**
     * Actualiza la lista de beacons favoritos
     */
    private fun updateFavoriteBeacons(logs: List<BLEScanLog>) {
        _favoriteBeacons.value = favoritesRepository.getFavoriteBeacons(logs)
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
        updateFavoriteBeacons(_scanLogs.value)
    }

    /**
     * Inicia el escaneo de beacons
     */
    fun startScanning() {
        if (beaconScanner.isScanning()) {
            return
        }

        // Cancelar jobs anteriores si existen
        scanLogsJob?.cancel()
        beaconScanJob?.cancel()
        autoStopJob?.cancel()

        // Limpiar logs anteriores
        _scanLogs.value = emptyList()
        _filteredScanLogs.value = emptyList()

        // Iniciar scanner genérico para debug
        Log.d("BeaconViewModel", "Starting GENERIC BLE scanner for debugging...")
        genericScanner.startScanning()

        // Recopilar logs del scanner genérico
        scanLogsJob = viewModelScope.launch {
            genericScanner.scanLogs.collect { newLog ->
                val currentLogs = _scanLogs.value.toMutableList()
                
                // Buscar si ya existe un log de este dispositivo (por MAC address)
                val existingIndex = currentLogs.indexOfFirst { it.macAddress == newLog.macAddress }
                
                if (existingIndex != -1) {
                    // Actualizar el log existente (reemplazar con los datos más recientes)
                    currentLogs[existingIndex] = newLog
                    Log.d("BeaconViewModel", "Device updated: ${newLog.macAddress}")
                } else {
                    // Agregar nuevo dispositivo al principio
                    currentLogs.add(0, newLog)
                    Log.d("BeaconViewModel", "New device added: ${newLog.macAddress}")
                }
                
                // Limitar a 50 dispositivos únicos para mejor rendimiento
                if (currentLogs.size > 50) {
                    currentLogs.removeAt(currentLogs.size - 1)
                }
                
                _scanLogs.value = currentLogs
                applySearchFilter()
                Log.d("BeaconViewModel", "Total unique devices: ${currentLogs.size}")
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
                    // Actualizar estado basado en logs detectados o beacons filtrados
                    val totalDevices = _scanLogs.value.size
                    _uiState.value = when {
                        beaconList.isNotEmpty() -> BeaconUiState.DetectingBeacons(beaconList.size)
                        totalDevices > 0 -> BeaconUiState.ScanningWithDevices(totalDevices)
                        else -> BeaconUiState.Scanning
                    }
                }
        }

        // Programar detención automática después de 5 segundos
        autoStopJob = viewModelScope.launch {
            kotlinx.coroutines.delay(AUTO_STOP_DELAY_MS)
            Log.d("BeaconViewModel", "Auto-stopping scan after ${AUTO_STOP_DELAY_MS}ms")
            stopScanning()
        }
    }

    /**
     * Detiene el escaneo de beacons
     */
    fun stopScanning() {
        Log.d("BeaconViewModel", "Stopping scanners...")
        
        // Cancelar las coroutines de escaneo y el timer automático
        scanLogsJob?.cancel()
        beaconScanJob?.cancel()
        autoStopJob?.cancel()
        scanLogsJob = null
        beaconScanJob = null
        autoStopJob = null
        
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
        _scanLogs.value = emptyList()
        _filteredScanLogs.value = emptyList()
    }

    /**
     * Actualiza el filtro de búsqueda
     */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        applySearchFilter()
    }

    /**
     * Aplica el filtro de búsqueda a los logs
     * Por defecto solo muestra iBeacons, con búsqueda filtra dentro de los iBeacons
     */
    private fun applySearchFilter() {
        val query = _searchQuery.value.trim().lowercase()
        
        // Primero filtrar solo iBeacons
        val onlyBeacons = _scanLogs.value.filter { log ->
            log.iBeaconData != null
        }
        
        // Luego aplicar la búsqueda si hay query
        _filteredScanLogs.value = if (query.isEmpty()) {
            onlyBeacons
        } else {
            onlyBeacons.filter { log ->
                log.deviceName.lowercase().contains(query) ||
                log.macAddress.lowercase().contains(query) ||
                log.iBeaconData?.uuid?.lowercase()?.contains(query) == true ||
                log.iBeaconData?.major?.toString()?.contains(query) == true ||
                log.iBeaconData?.minor?.toString()?.contains(query) == true
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
