package com.akistoy.app.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
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

@Composable
fun SettingsRoute(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    SettingsScreen(
        state = state,
        onUuidsChange = viewModel::onUuidsChange,
        onSave = viewModel::saveUuids,
        onFetchRemote = viewModel::fetchRemote,
        onMessageConsumed = viewModel::clearMessage
    )
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onUuidsChange: (String) -> Unit,
    onSave: () -> Unit,
    onFetchRemote: () -> Unit,
    onMessageConsumed: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            onMessageConsumed()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(hostState = snackbarHostState) }) { paddingValues ->
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
            Text(text = "Bluetooth: ${if (state.bluetoothEnabled) "Activo" else "Inactivo"}")
            Text(text = "Servicio en primer plano: ${if (state.serviceEnabled) "Activo" else "Inactivo"}")
        }
    }
}
