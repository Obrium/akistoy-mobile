package com.akistoy.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akistoy.app.domain.model.BeaconEvent
import com.akistoy.app.domain.model.BeaconEventType
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun DetectionList(
    detections: List<BeaconEvent>,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier) {
        items(detections) { event ->
            DetectionItem(event)
        }
    }
}

@Composable
private fun DetectionItem(event: BeaconEvent) {
    val timestamp = event.timestamp.toLocalDateTime(TimeZone.UTC)

    // Determinar color y etiqueta según tipo de evento
    val (eventLabel, eventColor) = when (event.eventType) {
        BeaconEventType.ENTRY -> "ENTRADA" to Color(0xFF4CAF50)  // Verde
        BeaconEventType.EXIT -> "SALIDA" to Color(0xFFF44336)    // Rojo
        BeaconEventType.DETECTION -> "DETECCIÓN" to Color(0xFF2196F3)  // Azul
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row {
                Text(
                    text = eventLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = eventColor,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Mostrar nombre de zona de forma destacada
                Text(
                    text = event.zoneName ?: event.beaconId.takeLast(12).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            // Mostrar el beacon ID como información secundaria solo si hay zona
            if (event.zoneName != null) {
                Text(
                    text = "Beacon: ${event.beaconId.takeLast(8).uppercase()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row {
                Text(text = "RSSI: ${event.rssi} dBm", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = event.proximity.name)
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = "%.2f m".format(event.distanceMeters))
            }
            // Mostrar fecha y hora de forma más clara
            Text(
                text = "Hora: ${timestamp.time}  •  ${timestamp.date}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
