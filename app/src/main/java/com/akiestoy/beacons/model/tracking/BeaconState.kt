package com.akiestoy.beacons.model.tracking

/**
 * Estados de la máquina de estados para tracking de entrada/salida
 */
enum class BeaconState {
    /**
     * Usuario fuera del recinto - No hay beacons detectados
     */
    OUTSIDE,

    /**
     * Usuario detectó primer beacon - Posible entrada
     */
    ENTERING,

    /**
     * Usuario confirmado dentro del recinto - Detectando beacons internos
     */
    INSIDE,

    /**
     * Usuario perdió señal - Esperando confirmación de salida
     */
    EXITING
}
