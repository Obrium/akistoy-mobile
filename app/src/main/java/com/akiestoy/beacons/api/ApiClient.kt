package com.akiestoy.beacons.api

import com.akiestoy.beacons.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Cliente Retrofit para comunicación con el backend
 */
object ApiClient {
    // URL del backend principal (desde .env o local.properties)
    private val BASE_URL = BuildConfig.API_BASE_URL

    // URL para el servicio de registro de usuarios (desde .env o local.properties)
    private val USER_API_URL = BuildConfig.USER_API_BASE_URL

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val proximityApi: BeaconProximityApi = retrofit.create(BeaconProximityApi::class.java)

    // Retrofit para el servicio de usuarios
    private val userRetrofit = Retrofit.Builder()
        .baseUrl(USER_API_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val userApi: UserApi = userRetrofit.create(UserApi::class.java)

    /**
     * Permite configurar la URL base dinámicamente
     */
    fun createApi(baseUrl: String): BeaconProximityApi {
        val customRetrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return customRetrofit.create(BeaconProximityApi::class.java)
    }
}
