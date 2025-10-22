package com.akistoy.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akistoy.app.ui.theme.BeaconGray
import com.akistoy.app.ui.theme.BeaconGreen

@Composable
fun BeaconStatusIndicator(
    modifier: Modifier = Modifier,
    isDetecting: Boolean,
    lastRssi: Int?
) {
    val color = if (isDetecting) BeaconGreen else BeaconGray
    val label = if (isDetecting) "Detectando" else "Sin señal"
    Box(
        modifier = modifier
            .size(160.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = lastRssi?.let { "$label\n${it} dBm" } ?: label,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}
