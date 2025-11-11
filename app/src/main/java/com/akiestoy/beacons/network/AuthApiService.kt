package com.akiestoy.beacons.network

import com.akiestoy.beacons.model.api.LoginRequest
import com.akiestoy.beacons.model.api.LoginResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

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
}
