package com.akiestoy.beacons.model.proximity

import com.google.gson.annotations.SerializedName

/**
 * Métricas de proximidad incluidas en cada evento
 */
data class ProximityMetrics(
    /**
     * RSSI promedio (media móvil de las últimas 3-5 lecturas)
     */
    @SerializedName("rssiAvg")
    val rssiAvg: Double,

    /**
     * Número de muestras usadas para calcular el promedio
     */
    @SerializedName("samples")
    val samples: Int
)
