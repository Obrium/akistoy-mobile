package com.akiestoy.beacons.network

import com.akiestoy.beacons.model.api.ZoneResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

/**
 * API Service para obtener zonas y beacons
 */
interface ZonesApiService {

    @Headers("accept: */*")
    @GET("v1/mobile/zones")
    suspend fun getZones(
        @Query("tenantId") tenantId: String,
        @Query("companyId") companyId: String
    ): Response<List<ZoneResponse>>
}
