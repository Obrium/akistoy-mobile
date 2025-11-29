package com.akiestoy.beacons.api

import com.akiestoy.beacons.model.proximity.BeaconProximityRequest
import com.akiestoy.beacons.model.tracking.HeartbeatRequest
import com.akiestoy.beacons.model.tracking.HeartbeatResponse
import com.akiestoy.beacons.model.tracking.ImplicitEventRequest
import com.akiestoy.beacons.model.tracking.ImplicitEventResponse
import com.akiestoy.beacons.model.tracking.ZoneEventRequest
import com.akiestoy.beacons.model.tracking.ZoneEventResponse
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

    /**
     * Envía evento implícito de entrada/salida
     *
     * @param request Datos del evento implícito
     * @return Response con el resultado
     */
    @POST("v1/mobile/implicit-event")
    suspend fun sendImplicitEvent(
        @Body request: ImplicitEventRequest
    ): Response<ImplicitEventResponse>

    /**
     * Envía heartbeat para mantener señal de vida
     *
     * @param request Datos del heartbeat
     * @return Response con el resultado
     */
    @POST("v1/mobile/heartbeat")
    suspend fun sendHeartbeat(
        @Body request: HeartbeatRequest
    ): Response<HeartbeatResponse>

    /**
     * Envía evento de zona optimizado
     * Tipos: COMPANY_ENTRY, COMPANY_EXIT, ZONE_CHANGE
     *
     * Este endpoint reduce drásticamente los eventos (~115K/día → ~10-20/día por dispositivo)
     *
     * @param request Datos del evento de zona
     * @return Response con el resultado
     */
    @POST("v1/mobile/zone-event")
    suspend fun sendZoneEvent(
        @Body request: ZoneEventRequest
    ): Response<ZoneEventResponse>
}

/**
 * Respuesta del backend al enviar un evento
 */
data class ProximityEventResponse(
    val success: Boolean,
    val message: String? = null
)
