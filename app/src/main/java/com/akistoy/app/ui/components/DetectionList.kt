package com.akistoy.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
    Column(modifier = modifier) {
        detections.forEach { event ->
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
            // NOMBRE DEL DISPOSITIVO - Grande y destacado
            Text(
                text = event.zoneName ?: "Dispositivo desconocido",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF212121)
            )

            Spacer(modifier = Modifier.width(4.dp))

            // Etiqueta de evento (ENTRADA/SALIDA/DETECCIÓN)
            Row {
                Text(
                    text = eventLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = eventColor,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // MAC Address / ID del beacon
            Text(
                text = "ID: ${event.beaconId.takeLast(17).uppercase()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.width(4.dp))

            // Métricas (RSSI, Proximidad, Distancia)
            Row {
                Text(
                    text = "Señal: ${event.rssi} dBm",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = event.proximity.name,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "%.2f m".format(event.distanceMeters),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Hora de detección
            Text(
                text = "${timestamp.time}  •  ${timestamp.date}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
