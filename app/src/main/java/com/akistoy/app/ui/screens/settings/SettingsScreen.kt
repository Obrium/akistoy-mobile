package com.akistoy.app.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun SettingsRoute(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    SettingsScreen(
        state = state,
        onUuidsChange = viewModel::onUuidsChange,
        onSave = viewModel::saveUuids,
        onFetchRemote = viewModel::fetchRemote,
        onMessageConsumed = viewModel::clearMessage,
        onNavigateBack = onNavigateBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onUuidsChange: (String) -> Unit,
    onSave: () -> Unit,
    onFetchRemote: () -> Unit,
    onMessageConsumed: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val isBatteryOptimizationDisabled = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(PowerManager::class.java)
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        } else {
            true
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            onMessageConsumed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Configuración") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "UUIDs monitorizados")
            OutlinedTextField(
                value = state.uuidsText,
                onValueChange = onUuidsChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                minLines = 3
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth(), enabled = !state.isSaving) {
                if (state.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.height(18.dp))
                } else {
                    Text(text = "Guardar")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onFetchRemote, modifier = Modifier.fillMaxWidth(), enabled = !state.isSaving) {
                Text(text = "Sincronizar configuración")
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Sección de optimización de batería
            if (!isBatteryOptimizationDisabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    )
                ) {
                    Text(text = "Desactivar optimización de batería")
                }
                Text(
                    text = "Recomendado para mantener la conexión en segundo plano",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Text(text = "Bluetooth: ${if (state.bluetoothEnabled) "Activo" else "Inactivo"}")
            Text(text = "Servicio en primer plano: ${if (state.serviceEnabled) "Activo" else "Inactivo"}")
            if (isBatteryOptimizationDisabled) {
                Text(
                    text = "Optimización de batería: Desactivada ✓",
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
