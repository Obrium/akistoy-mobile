package com.akiestoy.beacons.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.akiestoy.beacons.ui.BeaconViewModel
import com.akiestoy.beacons.viewmodel.SuperAdminViewModel

/** Pantalla de Configuración - Solo visible para Super Admin */
@Composable
fun ConfigurationScreen(
        superAdminViewModel: SuperAdminViewModel,
        beaconViewModel: BeaconViewModel,
        onNavigateToLinkedBeacons: () -> Unit,
        onNavigateToAddBeacons: () -> Unit,
        onNavigateToConnectManual: () -> Unit,
        onNavigateToUpdateWorker: () -> Unit,
        onNavigateToHome: () -> Unit
) {
    Column(
            modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        // Título "Opciones"
        Text(
                text = "Opciones",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Botón: Ver Beacons vinculados
        ConfigurationButton(text = "Ver Beacons\nvinculados", onClick = onNavigateToLinkedBeacons)

        Spacer(modifier = Modifier.height(16.dp))

        // Botón: Agregar Beacons
        ConfigurationButton(text = "Agregar Beacons", onClick = onNavigateToAddBeacons)

        Spacer(modifier = Modifier.height(16.dp))

        // Botón: Actualizar trabajador
        ConfigurationButton(text = "Actualizar trabajador", onClick = onNavigateToUpdateWorker)

        Spacer(modifier = Modifier.height(16.dp))

        // Botón: Conectar manual
        ConfigurationButton(text = "Conectar manual", onClick = onNavigateToConnectManual)

        Spacer(modifier = Modifier.height(48.dp))

        // Botón para cerrar sesión
        Button(
                onClick = {
                    superAdminViewModel.logout()
                    onNavigateToHome()
                },
                colors =
                        ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                        ),
                modifier = Modifier.fillMaxWidth()
        ) { Text(text = "Cerrar sesión", modifier = Modifier.padding(vertical = 8.dp)) }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/** Botón de configuración con estilo personalizado */
@Composable
private fun ConfigurationButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().height(60.dp),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(2.dp, Color(0xFF90CAF9)), // Azul claro
            colors =
                    ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFF1976D2) // Azul oscuro para el texto
                    )
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}
