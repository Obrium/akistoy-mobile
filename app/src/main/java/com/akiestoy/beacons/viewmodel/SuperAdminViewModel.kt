package com.akiestoy.beacons.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel para manejar el estado de autenticación del Super Admin
 */
class SuperAdminViewModel : ViewModel() {
    
    // Estado de autenticación
    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()
    
    // Credenciales mock
    private val MOCK_USERNAME = "benja"
    private val MOCK_PASSWORD = "123"
    
    /**
     * Intenta autenticar con las credenciales proporcionadas
     * @return true si la autenticación fue exitosa, false en caso contrario
     */
    fun login(username: String, password: String): Boolean {
        val isValid = username == MOCK_USERNAME && password == MOCK_PASSWORD
        if (isValid) {
            _isAuthenticated.value = true
        }
        return isValid
    }
    
    /**
     * Cierra la sesión del super admin
     */
    fun logout() {
        _isAuthenticated.value = false
    }
}

