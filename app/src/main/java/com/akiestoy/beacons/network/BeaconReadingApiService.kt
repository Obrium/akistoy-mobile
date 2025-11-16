package com.akiestoy.beacons.network

import com.akiestoy.beacons.model.api.BeaconReadingRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * API Service para enviar lecturas de beacons
 */
interface BeaconReadingApiService {

    @POST("v1/mobile/beacon-reading")
    suspend fun sendBeaconReading(@Body reading: BeaconReadingRequest): Response<Void>
}
