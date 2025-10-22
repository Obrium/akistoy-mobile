package com.akistoy.app.data.remote.api

import com.akistoy.app.data.remote.dto.BeaconMarkRequest
import com.akistoy.app.data.remote.dto.BeaconMarkResponse
import com.akistoy.app.data.remote.dto.ConfigResponse
import com.akistoy.app.data.remote.dto.LoginRequest
import com.akistoy.app.data.remote.dto.LoginResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AkistoyApi {
    @POST("v1/auth/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    @GET("v1/config")
    suspend fun getConfig(): ConfigResponse

    @POST("v1/marks")
    suspend fun sendMark(@Body body: BeaconMarkRequest): BeaconMarkResponse
}
