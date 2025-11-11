package com.akiestoy.beacons.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.akiestoy.beacons.viewmodel.RegistrationState

/**
 * Dialog para el registro inicial del usuario
 * Este dialog no se puede cerrar hasta que se complete el registro
 */
@Composable
fun UserRegistrationDialog(
    rutInput: String,
    rutError: String?,
    rutEmpresaInput: String,
    rutEmpresaError: String?,
    registrationState: RegistrationState,
    onRutChange: (String) -> Unit,
    onRutEmpresaChange: (String) -> Unit,
    onRegisterClick: () -> Unit
) {
    // Dialog que no se puede descartar
    Dialog(
        onDismissRequest = { /* No permite cerrar */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Título
                Text(
                    text = "Bienvenido",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "Para comenzar, ingresa tu RUT y el RUT de tu empresa",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Campo de RUT
                OutlinedTextField(
                    value = rutInput,
                    onValueChange = onRutChange,
                    label = { Text("Tu RUT") },
                    placeholder = { Text("12.345.678-9") },
                    visualTransformation = RutVisualTransformation(),
                    isError = rutError != null,
                    supportingText = {
                        if (rutError != null) {
                            Text(
                                text = rutError,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Text("Ingresa tu RUT")
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    enabled = registrationState !is RegistrationState.Loading,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Campo de RUT Empresa
                OutlinedTextField(
                    value = rutEmpresaInput,
                    onValueChange = onRutEmpresaChange,
                    label = { Text("RUT Empresa") },
                    placeholder = { Text("76.123.456-7") },
                    visualTransformation = RutVisualTransformation(),
                    isError = rutEmpresaError != null,
                    supportingText = {
                        if (rutEmpresaError != null) {
                            Text(
                                text = rutEmpresaError,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Text("Ingresa el RUT de tu empresa")
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { onRegisterClick() }
                    ),
                    enabled = registrationState !is RegistrationState.Loading,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Mensaje de error de registro
                if (registrationState is RegistrationState.Error) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = registrationState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Botón de registro
                Button(
                    onClick = onRegisterClick,
                    enabled = registrationState !is RegistrationState.Loading && rutInput.isNotBlank() && rutEmpresaInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (registrationState is RegistrationState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = if (registrationState is RegistrationState.Loading)
                            "Verificando..."
                        else
                            "Continuar"
                    )
                }
            }
        }
    }
}
