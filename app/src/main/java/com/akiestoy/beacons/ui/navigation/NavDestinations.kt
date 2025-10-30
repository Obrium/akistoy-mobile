package com.akiestoy.beacons.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Destinos de navegación de la app
 */
sealed class NavDestination(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    data object Scanner : NavDestination(
        route = "scanner",
        title = "Scanner",
        icon = Icons.AutoMirrored.Filled.BluetoothSearching
    )

    data object Favorites : NavDestination(
        route = "favorites",
        title = "Favoritos",
        icon = Icons.Default.Favorite
    )

    data object Packets : NavDestination(
        route = "packets",
        title = "Paquetes",
        icon = Icons.Default.DataObject
    )

    data object Proximity : NavDestination(
        route = "proximity",
        title = "Proximidad",
        icon = Icons.Default.NearMe
    )

    data object Settings : NavDestination(
        route = "settings",
        title = "Ajustes",
        icon = Icons.Default.Settings
    )

    companion object {
        val items = listOf(Scanner, Favorites, Packets, Proximity, Settings)
    }
}

