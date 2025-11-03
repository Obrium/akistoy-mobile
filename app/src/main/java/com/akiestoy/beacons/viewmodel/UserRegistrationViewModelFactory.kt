package com.akiestoy.beacons.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.akiestoy.beacons.data.UserRepository

/**
 * Factory para crear UserRegistrationViewModel con dependencias
 */
class UserRegistrationViewModelFactory(
    private val userRepository: UserRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(UserRegistrationViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return UserRegistrationViewModel(userRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
