package com.akiestoy.beacons.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akiestoy.beacons.data.ZoneDao
import com.akiestoy.beacons.model.Zone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel para gestionar las zonas
 */
class ZoneViewModel(private val zoneDao: ZoneDao) : ViewModel() {

    private val _zones = MutableStateFlow<List<Zone>>(emptyList())
    val zones: StateFlow<List<Zone>> = _zones.asStateFlow()

    init {
        // Observar zonas de la base de datos
        viewModelScope.launch {
            zoneDao.getAllZones().collect { zonesList ->
                _zones.value = zonesList
            }
        }
    }

    /**
     * Elimina una zona de la base de datos
     */
    fun deleteZone(zoneId: String) {
        viewModelScope.launch {
            zoneDao.deleteZone(zoneId)
        }
    }

    /**
     * Obtiene todas las zonas una vez (sin observar)
     */
    suspend fun getAllZonesOnce(): List<Zone> {
        return zoneDao.getAllZonesOnce()
    }
}

