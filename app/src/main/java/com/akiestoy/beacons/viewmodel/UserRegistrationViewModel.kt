package com.akiestoy.beacons.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akiestoy.beacons.api.ApiClient
import com.akiestoy.beacons.data.UserRepository
import com.akiestoy.beacons.model.user.User
import com.akiestoy.beacons.utils.RutValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Estados del proceso de registro
 */
sealed class RegistrationState {
    data object Idle : RegistrationState()
    data object Loading : RegistrationState()
    data object Success : RegistrationState()
    data class Error(val message: String) : RegistrationState()
}

/**
 * ViewModel para gestionar el registro de usuario
 */
class UserRegistrationViewModel(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _registrationState = MutableStateFlow<RegistrationState>(RegistrationState.Idle)
    val registrationState: StateFlow<RegistrationState> = _registrationState.asStateFlow()

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _rutInput = MutableStateFlow("")
    val rutInput: StateFlow<String> = _rutInput.asStateFlow()

    private val _rutError = MutableStateFlow<String?>(null)
    val rutError: StateFlow<String?> = _rutError.asStateFlow()

    init {
        // Observar el usuario actual en la base de datos
        viewModelScope.launch {
            userRepository.currentUser.collect { user ->
                _currentUser.value = user
                Log.d("UserRegistrationVM", "Usuario actual: $user")
            }
        }
    }

    fun updateRutInput(rut: String) {
        // Solo guardar el RUT limpio (sin formatear)
        val cleaned = RutValidator.cleanRut(rut)
        if (cleaned.length <= 9) {
            _rutInput.value = cleaned
        }
        _rutError.value = null
    }

    fun validateAndRegister() {
        val rut = _rutInput.value

        // Validar formato
        if (!RutValidator.isValidFormat(rut)) {
            _rutError.value = "Formato de RUT inválido"
            return
        }

        // Validar dígito verificador
        if (!RutValidator.isValid(rut)) {
            _rutError.value = "RUT inválido (verificador incorrecto)"
            return
        }

        // Proceder con el registro
        registerUser(rut)
    }

    private fun registerUser(rut: String) {
        viewModelScope.launch {
            _registrationState.value = RegistrationState.Loading
            try {
                val cleanRut = RutValidator.cleanRut(rut)
                Log.d("UserRegistrationVM", "Registrando usuario con RUT: $cleanRut")

                // Llamar al servicio de registro
                val response = ApiClient.userApi.registerUser(cleanRut)
                Log.d("UserRegistrationVM", "Respuesta del servidor: $response")

                // Guardar usuario en la base de datos
                val user = User(
                    id = response.id,
                    rut = cleanRut,
                    name = response.name,
                    phone = response.phone
                )
                userRepository.saveUser(user)

                _registrationState.value = RegistrationState.Success
                Log.d("UserRegistrationVM", "Usuario registrado exitosamente: ${user.name}")
            } catch (e: Exception) {
                Log.e("UserRegistrationVM", "Error al registrar usuario", e)
                _registrationState.value = RegistrationState.Error(
                    e.message ?: "Error desconocido al registrar usuario"
                )
            }
        }
    }

    fun resetRegistrationState() {
        _registrationState.value = RegistrationState.Idle
    }

    fun clearUser() {
        viewModelScope.launch {
            userRepository.clearUser()
            _rutInput.value = ""
            _rutError.value = null
        }
    }
}
