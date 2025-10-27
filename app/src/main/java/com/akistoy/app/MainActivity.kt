package com.akistoy.app

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.akistoy.app.R
import com.akistoy.app.ui.components.PermissionRationaleCard
import com.akistoy.app.ui.navigation.AkistoyNavHost
import com.akistoy.app.ui.theme.AkistoyTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            AkistoyTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val permissionsGranted = remember { mutableStateOf(false) }
                    val bluetoothEnabled = remember { mutableStateOf(false) }

                    // Gate 1: Verificar permisos
                    PermissionGate(permissionsGranted)

                    // Gate 2: Verificar Bluetooth activado (solo si permisos ok)
                    if (permissionsGranted.value) {
                        BluetoothGate(bluetoothEnabled)

                        // Solo mostrar app si todo está ok
                        if (bluetoothEnabled.value) {
                            AkistoyNavHost()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionGate(permissionsGranted: MutableState<Boolean>) {
    val permissions = remember { requiredPermissions() }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        permissionsGranted.value = result.all { it.value }
    }

    LaunchedEffect(Unit) {
        launcher.launch(permissions.toTypedArray())
    }

    if (!permissionsGranted.value) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PermissionRationaleCard(message = stringResource(id = R.string.permission_rationale))
            Button(onClick = { launcher.launch(permissions.toTypedArray()) }) {
                Text(text = stringResource(id = R.string.grant_permissions))
            }
        }
    }
}

@Composable
private fun BluetoothGate(bluetoothEnabled: MutableState<Boolean>) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var checkTrigger by remember { mutableStateOf(0) }

    // Launcher para activar Bluetooth
    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        // Forzar re-check después de volver
        checkTrigger++
    }

    // Verificar estado de Bluetooth en cada ciclo de vida
    DisposableEffect(lifecycleOwner, checkTrigger) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // Re-verificar cada vez que la app vuelve al foreground
                val manager = lifecycleOwner as? ComponentActivity
                manager?.let {
                    val btManager = it.getSystemService(BluetoothManager::class.java)
                    val adapter = btManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
                    bluetoothEnabled.value = adapter?.isEnabled == true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Check inicial
    LaunchedEffect(Unit) {
        val manager = (lifecycleOwner as? ComponentActivity)?.getSystemService(BluetoothManager::class.java)
        val adapter = manager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
        bluetoothEnabled.value = adapter?.isEnabled == true
    }

    // Mostrar pantalla de bloqueo si Bluetooth está desactivado
    if (!bluetoothEnabled.value) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Bluetooth Requerido",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Esta aplicación requiere Bluetooth activado para funcionar correctamente. Por favor, activa el Bluetooth para continuar.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = {
                    // Intent para abrir configuración de Bluetooth
                    val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    enableBluetoothLauncher.launch(intent)
                }
            ) {
                Text(text = "Activar Bluetooth")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { checkTrigger++ }
            ) {
                Text(text = "Verificar de nuevo")
            }
        }
    }
}

private fun requiredPermissions(): List<String> {
    val permissions = mutableListOf<String>()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        permissions += Manifest.permission.BLUETOOTH_SCAN
        permissions += Manifest.permission.BLUETOOTH_CONNECT
    } else {
        permissions += Manifest.permission.ACCESS_FINE_LOCATION
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions += Manifest.permission.POST_NOTIFICATIONS
    }
    return permissions
}
