package com.akiestoy.beacons.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.History
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

    data object History : NavDestination(
        route = "history",
        title = "Historial",
        icon = Icons.Default.History
    )

    data object Settings : NavDestination(
        route = "settings",
        title = "Ajustes",
        icon = Icons.Default.Settings
    )

    companion object {
        val items = listOf(Scanner, History, Settings)
    }
}

