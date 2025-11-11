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

    // Flag para desactivar temporalmente la validación del dígito verificador
    // TODO: Cambiar a true cuando se requiera validación estricta del RUT
    private val VALIDATE_RUT_VERIFIER = false

    private val _registrationState = MutableStateFlow<RegistrationState>(RegistrationState.Idle)
    val registrationState: StateFlow<RegistrationState> = _registrationState.asStateFlow()

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    // RUTs por defecto (precargados en el popup de bienvenida)
    private val DEFAULT_RUT = "250146748" // 25014674-8 sin formato
    private val DEFAULT_RUT_EMPRESA = "761234567" // 76123456-7 sin formato

    private val _rutInput = MutableStateFlow(DEFAULT_RUT)
    val rutInput: StateFlow<String> = _rutInput.asStateFlow()

    private val _rutError = MutableStateFlow<String?>(null)
    val rutError: StateFlow<String?> = _rutError.asStateFlow()

    private val _rutEmpresaInput = MutableStateFlow(DEFAULT_RUT_EMPRESA)
    val rutEmpresaInput: StateFlow<String> = _rutEmpresaInput.asStateFlow()

    private val _rutEmpresaError = MutableStateFlow<String?>(null)
    val rutEmpresaError: StateFlow<String?> = _rutEmpresaError.asStateFlow()

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

    fun updateRutEmpresaInput(rutEmpresa: String) {
        // Solo guardar el RUT limpio (sin formatear)
        val cleaned = RutValidator.cleanRut(rutEmpresa)
        if (cleaned.length <= 9) {
            _rutEmpresaInput.value = cleaned
        }
        _rutEmpresaError.value = null
    }

    fun validateAndRegister() {
        val rut = _rutInput.value
        val rutEmpresa = _rutEmpresaInput.value

        // Validar formato del RUT personal
        if (!RutValidator.isValidFormat(rut)) {
            _rutError.value = "Formato de RUT inválido"
            return
        }

        // Validar dígito verificador del RUT personal (solo si está habilitado)
        if (VALIDATE_RUT_VERIFIER && !RutValidator.isValid(rut)) {
            _rutError.value = "RUT inválido (verificador incorrecto)"
            return
        }

        // Validar formato del RUT empresa
        if (!RutValidator.isValidFormat(rutEmpresa)) {
            _rutEmpresaError.value = "Formato de RUT inválido"
            return
        }

        // Validar dígito verificador del RUT empresa (solo si está habilitado)
        if (VALIDATE_RUT_VERIFIER && !RutValidator.isValid(rutEmpresa)) {
            _rutEmpresaError.value = "RUT inválido (verificador incorrecto)"
            return
        }

        // Proceder con el registro
        registerUser(rut, rutEmpresa)
    }

    private fun registerUser(rut: String, rutEmpresa: String) {
        viewModelScope.launch {
            _registrationState.value = RegistrationState.Loading
            try {
                val cleanRut = RutValidator.cleanRut(rut)
                val cleanRutEmpresa = RutValidator.cleanRut(rutEmpresa)

                // Formatear RUTs para el API (sin puntos, solo con guión)
                val formattedRut = formatRutForApi(cleanRut)
                val formattedRutEmpresa = formatRutForApi(cleanRutEmpresa)

                Log.d("UserRegistrationVM", "Iniciando login con RUT: $formattedRut, RUT Empresa: $formattedRutEmpresa")

                // Llamar al servicio de login
                val (success, errorMessage) = userRepository.login(
                    rut = formattedRut,
                    rutEmpresa = formattedRutEmpresa,
                    deviceId = "" // Se obtiene del API response
                )

                if (success) {
                    _registrationState.value = RegistrationState.Success
                    Log.d("UserRegistrationVM", "Login exitoso")
                } else {
                    _registrationState.value = RegistrationState.Error(errorMessage)
                    Log.e("UserRegistrationVM", "Error en login: $errorMessage")
                }
            } catch (e: Exception) {
                Log.e("UserRegistrationVM", "Excepción al intentar login", e)
                _registrationState.value = RegistrationState.Error(
                    e.message ?: "Error desconocido al iniciar sesión"
                )
            }
        }
    }

    /**
     * Formatea un RUT para el API (sin puntos, solo con guión)
     * Ejemplo: 123456789 -> 12345678-9
     */
    private fun formatRutForApi(cleanRut: String): String {
        if (cleanRut.length < 2) return cleanRut
        val number = cleanRut.dropLast(1)
        val verifier = cleanRut.last()
        return "$number-$verifier"
    }

    fun resetRegistrationState() {
        _registrationState.value = RegistrationState.Idle
    }

    fun clearUser() {
        viewModelScope.launch {
            userRepository.clearUser()
            _rutInput.value = ""
            _rutError.value = null
            _rutEmpresaInput.value = ""
            _rutEmpresaError.value = null
        }
    }

    /**
     * Actualiza el nombre y RUT del usuario actual
     */
    fun updateUser(name: String, rut: String) {
        viewModelScope.launch {
            _registrationState.value = RegistrationState.Loading
            try {
                val currentUser = _currentUser.value
                if (currentUser == null) {
                    _registrationState.value = RegistrationState.Error("No hay usuario actual")
                    return@launch
                }

                val cleanRut = RutValidator.cleanRut(rut)
                
                // Validar RUT si es diferente al actual
                if (cleanRut != currentUser.rut) {
                    if (!RutValidator.isValidFormat(cleanRut)) {
                        _registrationState.value = RegistrationState.Error("Formato de RUT inválido")
                        return@launch
                    }
                    // Validar dígito verificador solo si está habilitado
                    if (VALIDATE_RUT_VERIFIER && !RutValidator.isValid(cleanRut)) {
                        _registrationState.value = RegistrationState.Error("RUT inválido (verificador incorrecto)")
                        return@launch
                    }
                }

                // Crear usuario actualizado
                val updatedUser = currentUser.copy(
                    name = name.trim(),
                    rut = cleanRut
                )

                // Guardar en base de datos
                userRepository.saveUser(updatedUser)
                
                _registrationState.value = RegistrationState.Success
                Log.d("UserRegistrationVM", "Usuario actualizado exitosamente: ${updatedUser.name}")
            } catch (e: Exception) {
                Log.e("UserRegistrationVM", "Error al actualizar usuario", e)
                _registrationState.value = RegistrationState.Error(
                    e.message ?: "Error desconocido al actualizar usuario"
                )
            }
        }
    }
}
