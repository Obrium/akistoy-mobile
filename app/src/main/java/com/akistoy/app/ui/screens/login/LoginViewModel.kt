package com.akistoy.app.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akistoy.app.domain.usecase.LoginUseCase
import com.akistoy.app.service.ServiceController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase,
    private val serviceController: ServiceController
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private val deviceId: String = UUID.randomUUID().toString()

    fun onNameChange(name: String) {
        _state.value = _state.value.copy(name = name, error = null)
    }

    fun login() {
        // El nombre es opcional, si está vacío se usará el nombre del dispositivo
        val name = _state.value.name.trim()

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            loginUseCase(name, deviceId)
                .onSuccess {
                    _state.value = _state.value.copy(isLoading = false, success = true)
                    // Auto-iniciar el servicio de escaneo después de login exitoso
                    serviceController.startService()
                }
                .onFailure { throwable ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = throwable.message ?: "Error al iniciar sesión"
                    )
                }
        }
    }
}

data class LoginUiState(
    val name: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false
)
