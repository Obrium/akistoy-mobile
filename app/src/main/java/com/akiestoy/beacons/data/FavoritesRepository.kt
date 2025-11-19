package com.akiestoy.beacons.data

import android.content.Context
import android.content.SharedPreferences
import com.akiestoy.beacons.model.BLEScanLog
import com.akiestoy.beacons.model.BeaconIdentifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repository para gestionar beacons favoritos
 * Usa SharedPreferences para persistir la lista
 * Los beacons se identifican por UUID + major + minor
 */
class FavoritesRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "favorites_prefs",
        Context.MODE_PRIVATE
    )

    private val _favorites = MutableStateFlow<Set<BeaconIdentifier>>(loadFavorites())
    val favorites: StateFlow<Set<BeaconIdentifier>> = _favorites.asStateFlow()

    private fun loadFavorites(): Set<BeaconIdentifier> {
        val json = prefs.getString(KEY_FAVORITES_V2, null)
        return if (json != null) {
            BeaconIdentifier.fromJson(json)
        } else {
            // Intentar migrar desde el formato anterior (solo MAC addresses)
            val oldFavorites = prefs.getStringSet(KEY_FAVORITES_OLD, emptySet()) ?: emptySet()
            if (oldFavorites.isNotEmpty()) {
                // Limpiar favoritos antiguos ya que no podemos migrarlos sin UUID+major+minor
                prefs.edit().remove(KEY_FAVORITES_OLD).apply()
            }
            emptySet()
        }
    }

    /**
     * Verifica si un beacon es favorito usando UUID + major + minor
     */
    fun isFavorite(uuid: String, major: Int, minor: Int): Boolean {
        return _favorites.value.any { it.matches(uuid, major, minor) }
    }

    /**
     * Verifica si un beacon es favorito usando BeaconIdentifier
     */
    fun isFavorite(identifier: BeaconIdentifier): Boolean {
        return _favorites.value.any { it.matches(identifier) }
    }

    /**
     * Verifica si un beacon es favorito usando solo MAC address (compatibilidad)
     * NOTA: Este método es menos preciso y solo debería usarse cuando no se tiene UUID+major+minor
     */
    fun isFavorite(macAddress: String): Boolean {
        return _favorites.value.any { it.macAddress == macAddress }
    }

    /**
     * Alterna un beacon como favorito usando BeaconIdentifier
     */
    fun toggleFavorite(identifier: BeaconIdentifier) {
        val currentFavorites = _favorites.value.toMutableSet()
        val existing = currentFavorites.find { it.matches(identifier) }

        if (existing != null) {
            currentFavorites.remove(existing)
        } else {
            currentFavorites.add(identifier)
        }
        saveFavorites(currentFavorites)
        _favorites.value = currentFavorites
    }

    /**
     * Alterna un beacon como favorito usando solo MAC address (compatibilidad)
     */
    fun toggleFavorite(macAddress: String) {
        val currentFavorites = _favorites.value.toMutableSet()
        val existing = currentFavorites.find { it.macAddress == macAddress }

        if (existing != null) {
            currentFavorites.remove(existing)
        }
        // No podemos agregar un favorito solo con MAC address, necesitamos UUID+major+minor
        saveFavorites(currentFavorites)
        _favorites.value = currentFavorites
    }

    /**
     * Añade múltiples beacons como favoritos sin remover los existentes
     */
    fun addFavorites(identifiers: List<BeaconIdentifier>) {
        val currentFavorites = _favorites.value.toMutableSet()
        // Remover duplicados (mismo UUID+major+minor) antes de agregar
        identifiers.forEach { newIdentifier ->
            // Remover cualquier coincidencia anterior
            currentFavorites.removeAll { it.matches(newIdentifier) }
            // Agregar el nuevo
            currentFavorites.add(newIdentifier)
        }
        saveFavorites(currentFavorites)
        _favorites.value = currentFavorites
    }

    /**
     * Añade múltiples beacons como favoritos usando solo MAC addresses (compatibilidad)
     * OBSOLETO: Usar addFavorites(List<BeaconIdentifier>) en su lugar
     */
    @Deprecated("Use addFavorites(List<BeaconIdentifier>) instead", ReplaceWith("addFavorites(identifiers)"))
    fun addFavoritesByMac(macAddresses: List<String>) {
        // No hacer nada, ya que necesitamos UUID+major+minor para identificar beacons correctamente
        // Esta función se mantiene solo por compatibilidad pero no hace nada
    }

    /**
     * Limpia todos los beacons favoritos
     */
    fun clearFavorites() {
        saveFavorites(emptySet())
        _favorites.value = emptySet()
    }

    private fun saveFavorites(favorites: Set<BeaconIdentifier>) {
        val json = BeaconIdentifier.toJson(favorites)
        prefs.edit().putString(KEY_FAVORITES_V2, json).apply()
    }

    /**
     * Obtiene los beacons favoritos de una lista de escaneos
     * Compara por UUID + major + minor
     */
    fun getFavoriteBeacons(allLogs: List<BLEScanLog>): List<BLEScanLog> {
        val favoriteIdentifiers = _favorites.value
        return allLogs.filter { log ->
            val identifier = BeaconIdentifier.fromScanLog(log)
            identifier != null && favoriteIdentifiers.any { it.matches(identifier) }
        }
    }

    companion object {
        private const val KEY_FAVORITES_V2 = "favorite_beacons_v2"  // Nuevo formato con UUID+major+minor
        private const val KEY_FAVORITES_OLD = "favorite_beacons"     // Formato antiguo (solo MAC)
    }
}

