package com.akistoy.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akistoy.app.domain.model.BeaconEvent
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = event.beaconId, style = MaterialTheme.typography.titleMedium)
            Text(text = "RSSI: ${event.rssi} dBm", fontWeight = FontWeight.Bold)
            Text(text = "${timestamp.date} ${timestamp.time}")
        }
    }
}
