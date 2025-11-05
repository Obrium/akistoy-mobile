package com.akiestoy.beacons.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/** Destinos de navegación de la app */
sealed class NavDestination(val route: String, val title: String, val icon: ImageVector) {
    data object Home : NavDestination(route = "home", title = "Home", icon = Icons.Default.Home)

    data object Scanner :
            NavDestination(
                    route = "scanner",
                    title = "Scanner",
                    icon = Icons.AutoMirrored.Filled.BluetoothSearching
            )

    data object Favorites :
            NavDestination(route = "favorites", title = "Favoritos", icon = Icons.Default.Favorite)

    data object Packets :
            NavDestination(route = "packets", title = "Paquetes", icon = Icons.Default.DataObject)

    data object Proximity :
            NavDestination(route = "proximity", title = "Proximidad", icon = Icons.Default.NearMe)

    data object Settings :
            NavDestination(route = "settings", title = "Ajustes", icon = Icons.Default.Settings)

    data object Configuration :
            NavDestination(
                    route = "configuration",
                    title = "Configuración",
                    icon = Icons.Default.AdminPanelSettings
            )

    data object LinkedBeacons :
            NavDestination(
                    route = "linked_beacons",
                    title = "Beacons Vinculados",
                    icon = Icons.Default.Favorite
            )

    data object AddBeacons :
            NavDestination(
                    route = "add_beacons",
                    title = "Agregar Beacons",
                    icon = Icons.Default.Add
            )

    companion object {
        // Items base sin Settings (oculto) y sin Configuration (solo para super admin)
        private val baseItems = listOf(Home, Scanner, Proximity)

        /**
         * Retorna los items de navegación según el estado de autenticación del super admin
         * @param isSuperAdminAuthenticated true si el super admin está autenticado
         */
        fun getItems(isSuperAdminAuthenticated: Boolean): List<NavDestination> {
            return if (isSuperAdminAuthenticated) {
                baseItems + Configuration
            } else {
                baseItems
            }
        }
    }
}
