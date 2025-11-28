package com.akiestoy.beacons.network

import com.akiestoy.beacons.model.api.ConfigureBeaconRequest
import com.akiestoy.beacons.model.api.ConfigureBeaconResponse
import com.akiestoy.beacons.model.api.LoginRequest
import com.akiestoy.beacons.model.api.LoginResponse
import com.akiestoy.beacons.model.api.UpdateEmployeeRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * API Service para autenticación
 */
interface AuthApiService {

    @Headers(
        "accept: application/json",
        "Content-Type: application/json"
    )
    @POST("v1/auth/mobile-login")
    suspend fun mobileLogin(@Body request: LoginRequest): Response<LoginResponse>

    @Headers(
        "accept: application/json",
        "Content-Type: application/json"
    )
    @PUT("v1/admin/employees/{employeeId}")
    suspend fun updateEmployee(
        @Path("employeeId") employeeId: String,
        @Body request: UpdateEmployeeRequest
    ): Response<Unit>

    @Headers(
        "accept: application/json",
        "Content-Type: application/json"
    )
    @POST("v1/admin/beacons/configure")
    suspend fun configureBeacon(
        @Header("X-Tenant-ID") tenantId: String,
        @Header("Authorization") authorization: String,
        @Body request: ConfigureBeaconRequest
    ): Response<ConfigureBeaconResponse>

    @Headers(
        "accept: application/json",
        "Content-Type: application/json"
    )
    @DELETE("v1/admin/beacons/by-identifiers")
    suspend fun deleteBeaconByIdentifiers(
        @Query("tenantId") tenantId: String,
        @Query("uuid") uuid: String,
        @Query("major") major: Int,
        @Query("minor") minor: Int,
        @Header("Authorization") authorization: String
    ): Response<Unit>
}
