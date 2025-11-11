package com.akiestoy.beacons.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.akiestoy.beacons.data.ZoneDao

/**
 * Factory para crear ZoneViewModel con dependencias
 */
class ZoneViewModelFactory(
    private val zoneDao: ZoneDao
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ZoneViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ZoneViewModel(zoneDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

