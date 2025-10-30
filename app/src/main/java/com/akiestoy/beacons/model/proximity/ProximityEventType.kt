package com.akiestoy.beacons.model.proximity

/**
 * Tipos de eventos de proximidad que se envían al backend
 */
enum class ProximityEventType {
    /**
     * El beacon entró en el rango de proximidad (superó el umbral de entrada)
     */
    ENTER,

    /**
     * El beacon salió del rango de proximidad (bajo el umbral de salida o no detectado por 10s)
     */
    EXIT,

    /**
     * El beacon sigue en proximidad - evento periódico cada 5s
     */
    HEARTBEAT
}
