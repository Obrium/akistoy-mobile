package com.akiestoy.beacons

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.akiestoy.beacons.ui.BeaconScreen
import com.akiestoy.beacons.ui.BeaconViewModel
import com.akiestoy.beacons.ui.theme.AkiEstoyTheme

class MainActivity : ComponentActivity() {

    private val viewModel: BeaconViewModel by viewModels()
    private var hasPermissions by mutableStateOf(false)

    // Registro de permisos
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        hasPermissions = allGranted
        // No iniciar automáticamente - dejar que el usuario presione el botón
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Verificar permisos iniciales
        hasPermissions = checkPermissions()

        setContent {
            AkiEstoyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BeaconScreen(
                        viewModel = viewModel,
                        onRequestPermissions = { requestPermissions() },
                        hasPermissions = hasPermissions
                    )
                }
            }
        }

        // Solicitar permisos automáticamente al inicio si no los tiene
        if (!hasPermissions) {
            requestPermissions()
        }
    }

    /**
     * Verifica si todos los permisos necesarios están otorgados
     */
    private fun checkPermissions(): Boolean {
        val permissions = getRequiredPermissions()
        return permissions.all {
            checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Solicita los permisos necesarios
     */
    private fun requestPermissions() {
        permissionLauncher.launch(getRequiredPermissions())
    }

    /**
     * Obtiene la lista de permisos necesarios según la versión de Android
     */
    private fun getRequiredPermissions(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                // Android 12+ (API 31+)
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                // Android 10-11 (API 29-30)
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            }
            else -> {
                // Android 9 y anteriores (API 28-)
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            }
        }
    }
}
