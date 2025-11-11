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
import com.akiestoy.beacons.viewmodel.RegistrationState
import com.akiestoy.beacons.viewmodel.UserRegistrationViewModel

/** Pantalla para actualizar datos del trabajador */
@Composable
fun UpdateWorkerScreen(
    userViewModel: UserRegistrationViewModel,
    onNavigateBack: () -> Unit
) {
    val currentUser by userViewModel.currentUser.collectAsState()
    val registrationState by userViewModel.registrationState.collectAsState()
    
    // Estados locales para los campos
    var name by remember { mutableStateOf(currentUser?.name ?: "") }
    var email by remember { mutableStateOf(currentUser?.email ?: "") }
    var active by remember { mutableStateOf(currentUser?.active ?: true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Actualizar campos cuando cambia el usuario
    LaunchedEffect(currentUser) {
        currentUser?.let {
            name = it.name
            email = it.email
            active = it.active
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
                errorMessage = (registrationState as RegistrationState.Error).message
            }
            else -> {
                errorMessage = null
            }
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
            text = "Actualizar trabajador",
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
                errorMessage = null
            },
            label = { Text("Nombre") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            isError = errorMessage?.contains("nombre", ignoreCase = true) == true,
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Campo de Email
        OutlinedTextField(
            value = email,
            onValueChange = { 
                email = it
                errorMessage = null
            },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
            isError = errorMessage?.contains("email", ignoreCase = true) == true,
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Switch de Activo
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Trabajador activo",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Switch(
                checked = active,
                onCheckedChange = { active = it }
            )
        }
        
        // Mensaje de error
        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Botón Actualizar
        Button(
            onClick = {
                errorMessage = null
                userViewModel.updateEmployee(name, email, active)
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

