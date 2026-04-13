package com.akiestoy.beacons.api

import com.akiestoy.beacons.BuildConfig
import com.akiestoy.beacons.network.AuthApiService
import com.akiestoy.beacons.network.BeaconReadingApiService
import com.akiestoy.beacons.network.ZonesApiService
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Cliente Retrofit para comunicación con el backend
 */
object ApiClient {
    // URL del backend (desde .env o secrets.properties)
    private val BASE_URL = BuildConfig.API_BASE_URL

    // En release no se loguean bodies (evita fugar tokens/payloads en logcat).
    // En debug sí, para facilitar diagnóstico durante desarrollo.
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val proximityApi: BeaconProximityApi = retrofit.create(BeaconProximityApi::class.java)

    // Todas las APIs usan la misma URL base
    val userApi: UserApi = retrofit.create(UserApi::class.java)
    val authApi: AuthApiService = retrofit.create(AuthApiService::class.java)
    val zonesApi: ZonesApiService = retrofit.create(ZonesApiService::class.java)
    val beaconReadingApi: BeaconReadingApiService = retrofit.create(BeaconReadingApiService::class.java)

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
