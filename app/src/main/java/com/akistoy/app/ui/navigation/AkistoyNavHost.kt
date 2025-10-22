package com.akistoy.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.akistoy.app.ui.screens.home.HomeRoute
import com.akistoy.app.ui.screens.login.LoginRoute
import com.akistoy.app.ui.screens.settings.SettingsRoute

@Composable
fun AkistoyNavHost(navController: NavHostController = rememberNavController()) {
    val sessionViewModel: SessionViewModel = hiltViewModel()
    val user by sessionViewModel.user.collectAsState()

    LaunchedEffect(user) {
        if (user != null) {
            navController.navigate(NavRoutes.HOME) {
                popUpTo(NavRoutes.LOGIN) { inclusive = true }
            }
        }
    }

    NavHost(navController = navController, startDestination = NavRoutes.LOGIN) {
        composable(NavRoutes.LOGIN) {
            LoginRoute(onSuccess = {
                navController.navigate(NavRoutes.HOME) {
                    popUpTo(NavRoutes.LOGIN) { inclusive = true }
                }
            })
        }
        composable(NavRoutes.HOME) {
            HomeRoute(onOpenSettings = { navController.navigate(NavRoutes.SETTINGS) })
        }
        composable(NavRoutes.SETTINGS) {
            SettingsRoute()
        }
    }
}
