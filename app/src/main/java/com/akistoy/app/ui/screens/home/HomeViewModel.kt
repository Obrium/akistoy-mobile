package com.akistoy.app.ui.screens.home

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akistoy.app.data.repository.UserPreferencesDataSource
import com.akistoy.app.domain.model.BeaconEvent
import com.akistoy.app.domain.usecase.ObserveDetectionsUseCase
import com.akistoy.app.domain.usecase.SendMarkUseCase
import com.akistoy.app.service.ServiceController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant

@HiltViewModel
class HomeViewModel @Inject constructor(
    observeDetectionsUseCase: ObserveDetectionsUseCase,
    private val sendMarkUseCase: SendMarkUseCase,
    private val serviceController: ServiceController,
    preferences: UserPreferencesDataSource,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                observeDetectionsUseCase(),
                preferences.serviceEnabledFlow
            ) { detections, serviceEnabled ->
                detections to serviceEnabled
            }.collect { (detections, serviceEnabled) ->
                // El beacon activo es el que tiene mejor señal (RSSI más alto)
                val activeBeacon = detections.maxByOrNull { it.rssi }

                _state.value = _state.value.copy(
                    isServiceRunning = serviceEnabled,
                    detections = detections,
                    detectionCount = detections.size,
                    lastDetectionTime = activeBeacon?.timestamp,
                    lastRssi = activeBeacon?.rssi,
                    activeBeaconId = activeBeacon?.beaconId,
                    activeZoneName = activeBeacon?.zoneName,  // Nombre de la zona
                    activeBeaconProximity = activeBeacon?.proximity?.name,
                    distanceMeters = activeBeacon?.distanceMeters,
                    bluetoothEnabled = isBluetoothEnabled()
                )
                activeBeacon?.let { event ->
                    viewModelScope.launch { sendMarkUseCase(event) }
                }
            }
        }
    }

    fun ensureServiceStarted() {
        viewModelScope.launch {
            // Siempre iniciar el servicio si no está corriendo
            if (!_state.value.isServiceRunning) {
                serviceController.startService()
            }
        }
    }

    fun toggleService() {
        viewModelScope.launch {
            val current = _state.value.isServiceRunning
            if (current) {
                serviceController.stopService()
            } else {
                serviceController.startService()
            }
        }
    }

    fun refreshBluetoothState() {
        _state.value = _state.value.copy(bluetoothEnabled = isBluetoothEnabled())
    }

    private fun isBluetoothEnabled(): Boolean {
        val manager = context.getSystemService(BluetoothManager::class.java)
        val adapter = manager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
        return adapter?.isEnabled == true
    }
}

data class HomeUiState(
    val isServiceRunning: Boolean = false,
    val detections: List<BeaconEvent> = emptyList(),
    val detectionCount: Int = 0,
    val lastDetectionTime: Instant? = null,
    val lastRssi: Int? = null,
    val activeBeaconId: String? = null,
    val activeZoneName: String? = null,  // Nombre de la zona o beacon
    val activeBeaconProximity: String? = null,
    val distanceMeters: Double? = null,
    val bluetoothEnabled: Boolean = true
)
