package com.akiestoy.beacons.data

import android.content.Context
import android.content.SharedPreferences
import com.akiestoy.beacons.model.BLEScanLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repository para gestionar beacons favoritos
 * Usa SharedPreferences para persistir la lista
 */
class FavoritesRepository(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "favorites_prefs",
        Context.MODE_PRIVATE
    )
    
    private val _favorites = MutableStateFlow<Set<String>>(loadFavorites())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()
    
    private fun loadFavorites(): Set<String> {
        return prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
    }
    
    fun isFavorite(macAddress: String): Boolean {
        return _favorites.value.contains(macAddress)
    }
    
    fun toggleFavorite(macAddress: String) {
        val currentFavorites = _favorites.value.toMutableSet()
        if (currentFavorites.contains(macAddress)) {
            currentFavorites.remove(macAddress)
        } else {
            currentFavorites.add(macAddress)
        }
        saveFavorites(currentFavorites)
        _favorites.value = currentFavorites
    }

    /**
     * Añade múltiples beacons como favoritos sin remover los existentes
     */
    fun addFavorites(macAddresses: List<String>) {
        val currentFavorites = _favorites.value.toMutableSet()
        currentFavorites.addAll(macAddresses)
        saveFavorites(currentFavorites)
        _favorites.value = currentFavorites
    }

    /**
     * Limpia todos los beacons favoritos
     */
    fun clearFavorites() {
        saveFavorites(emptySet())
        _favorites.value = emptySet()
    }
    
    private fun saveFavorites(favorites: Set<String>) {
        prefs.edit().putStringSet(KEY_FAVORITES, favorites).apply()
    }
    
    fun getFavoriteBeacons(allLogs: List<BLEScanLog>): List<BLEScanLog> {
        val favoriteMACs = _favorites.value
        return allLogs.filter { it.macAddress in favoriteMACs }
    }
    
    companion object {
        private const val KEY_FAVORITES = "favorite_beacons"
    }
}

