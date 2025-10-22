package com.akistoy.app.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.akistoy.app.ui.components.BeaconStatusIndicator
import com.akistoy.app.ui.components.DetectionList
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.periodUntil

@Composable
fun HomeRoute(
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    HomeScreen(
        state = state,
        onToggle = viewModel::toggleService,
        onOpenSettings = onOpenSettings
    )
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    onToggle: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.bluetoothEnabled) {
        if (!state.bluetoothEnabled) {
            snackbarHostState.showSnackbar("Bluetooth desactivado. Actívalo para continuar")
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BeaconStatusIndicator(
                isDetecting = state.isServiceRunning && state.detections.isNotEmpty(),
                lastRssi = state.lastRssi,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Detecciones: ${state.detectionCount}")
                Text(text = "Último: ${state.lastDetectionTime.toReadableTime()}")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
                Text(text = if (state.isServiceRunning) "Detener escaneo" else "Activar escaneo")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Configuración")
            }
            Spacer(modifier = Modifier.height(16.dp))
            DetectionList(
                detections = state.detections,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}

private fun Instant?.toReadableTime(): String = this?.let {
    val period = it.periodUntil(Clock.System.now(), TimeZone.UTC)
    if (period.minutes == 0 && period.seconds == 0) "Ahora" else "hace ${period.minutes}m ${period.seconds}s"
} ?: "N/A"
