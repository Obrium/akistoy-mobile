package com.akiestoy.beacons.api

import com.akiestoy.beacons.model.proximity.BeaconProximityRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * API para enviar eventos de proximidad al backend
 */
interface BeaconProximityApi {
    /**
     * Envía una lectura de beacon al backend
     *
     * @param request Datos del beacon más cercano
     * @return Response con el resultado del envío
     */
    @POST("v1/mobile/beacon-reading")
    suspend fun sendProximityEvent(
        @Body request: BeaconProximityRequest
    ): Response<ProximityEventResponse>
}

/**
 * Respuesta del backend al enviar un evento
 */
data class ProximityEventResponse(
    val success: Boolean,
    val message: String? = null
)
