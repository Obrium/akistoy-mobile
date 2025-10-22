package com.akistoy.app.ui.screens.settings

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akistoy.app.data.repository.UserPreferencesDataSource
import com.akistoy.app.domain.repo.ConfigRepository
import com.akistoy.app.domain.usecase.GetConfigUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val configRepository: ConfigRepository,
    private val getConfigUseCase: GetConfigUseCase,
    preferences: UserPreferencesDataSource,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val tracked = configRepository.getTrackedUuids()
            _state.value = _state.value.copy(
                uuidsText = tracked.joinToString(","),
                bluetoothEnabled = isBluetoothEnabled()
            )
        }
        viewModelScope.launch {
            preferences.serviceEnabledFlow.collect { enabled ->
                _state.value = _state.value.copy(serviceEnabled = enabled)
            }
        }
    }

    fun onUuidsChange(value: String) {
        _state.value = _state.value.copy(uuidsText = value)
    }

    fun saveUuids() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true)
            val list = _state.value.uuidsText.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            configRepository.saveTrackedUuids(list)
            _state.value = _state.value.copy(isSaving = false, message = "Guardado")
        }
    }

    fun fetchRemote() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true)
            getConfigUseCase().onSuccess { zones ->
                val uuids = zones.flatMap { it.beaconUuids }.distinct()
                configRepository.saveTrackedUuids(uuids)
                _state.value = _state.value.copy(
                    uuidsText = uuids.joinToString(","),
                    isSaving = false,
                    message = "Config remota aplicada"
                )
            }.onFailure { throwable ->
                _state.value = _state.value.copy(
                    isSaving = false,
                    message = throwable.message ?: "Error al cargar configuración"
                )
            }
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    private fun isBluetoothEnabled(): Boolean {
        val manager = context.getSystemService(BluetoothManager::class.java)
        val adapter = manager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
        return adapter?.isEnabled == true
    }
}

data class SettingsUiState(
    val uuidsText: String = "",
    val isSaving: Boolean = false,
    val message: String? = null,
    val bluetoothEnabled: Boolean = true,
    val serviceEnabled: Boolean = false
)
