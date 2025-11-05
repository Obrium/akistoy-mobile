package com.akiestoy.beacons.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.akiestoy.beacons.ui.components.RutVisualTransformation
import com.akiestoy.beacons.utils.RutValidator
import com.akiestoy.beacons.viewmodel.RegistrationState
import com.akiestoy.beacons.viewmodel.UserRegistrationViewModel

/** Pantalla para actualizar nombre y RUT del trabajador */
@Composable
fun UpdateWorkerScreen(
    userViewModel: UserRegistrationViewModel,
    onNavigateBack: () -> Unit
) {
    val currentUser by userViewModel.currentUser.collectAsState()
    val registrationState by userViewModel.registrationState.collectAsState()
    
    // Estados locales para los campos
    var name by remember { mutableStateOf(currentUser?.name ?: "") }
    var rut by remember { mutableStateOf(currentUser?.rut ?: "") }
    var nameError by remember { mutableStateOf<String?>(null) }
    var rutError by remember { mutableStateOf<String?>(null) }
    
    // Actualizar campos cuando cambia el usuario
    LaunchedEffect(currentUser) {
        currentUser?.let {
            name = it.name
            rut = it.rut
        }
    }
    
    // Manejar resultado de actualización
    LaunchedEffect(registrationState) {
        when (registrationState) {
            is RegistrationState.Success -> {
                onNavigateBack()
                userViewModel.resetRegistrationState()
            }
            is RegistrationState.Error -> {
                val message = (registrationState as RegistrationState.Error).message
                if (message.contains("RUT", ignoreCase = true)) {
                    rutError = message
                } else {
                    nameError = message
                }
            }
            else -> {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))
        
        // Título
        Text(
            text = "Trabajador",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // Campo de Nombre
        OutlinedTextField(
            value = name,
            onValueChange = { 
                name = it
                nameError = null
            },
            label = { Text("Nombre") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            isError = nameError != null,
            supportingText = if (nameError != null) {
                { Text(nameError!!, color = MaterialTheme.colorScheme.error) }
            } else null,
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Campo de RUT
        OutlinedTextField(
            value = rut,
            onValueChange = { newValue ->
                val cleaned = RutValidator.cleanRut(newValue)
                if (cleaned.length <= 9) {
                    rut = cleaned
                    rutError = null
                }
            },
            label = { Text("Rut") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = RutVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = rutError != null,
            supportingText = if (rutError != null) {
                { Text(rutError!!, color = MaterialTheme.colorScheme.error) }
            } else null,
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Botón Actualizar
        Button(
            onClick = {
                // Validaciones locales
                nameError = null
                rutError = null
                
                if (name.isBlank()) {
                    nameError = "El nombre no puede estar vacío"
                    return@Button
                }
                
                if (rut.isBlank()) {
                    rutError = "El RUT no puede estar vacío"
                    return@Button
                }
                
                if (!RutValidator.isValidFormat(rut)) {
                    rutError = "Formato de RUT inválido"
                    return@Button
                }
                
                if (!RutValidator.isValid(rut)) {
                    rutError = "RUT inválido (verificador incorrecto)"
                    return@Button
                }
                
                // Actualizar usuario
                userViewModel.updateUser(name, rut)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = registrationState !is RegistrationState.Loading,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            if (registrationState is RegistrationState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(
                    text = "Actualizar",
                    modifier = Modifier.padding(vertical = 8.dp),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Botón Cancelar
        TextButton(
            onClick = onNavigateBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Cancelar",
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

