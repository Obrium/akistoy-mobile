package com.akistoy.app.data.remote.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ESTABILIDAD: Rate Limiting Interceptor
 * - Limita requests a 30 por minuto (promedio de 2 segundos entre requests)
 * - Previene sobrecarga del servidor
 * - Usa sliding window para tracking
 */
@Singleton
class RateLimitInterceptor @Inject constructor() : Interceptor {

    private val requestTimestamps = ConcurrentHashMap<String, MutableList<Long>>()
    private val lastRequestTime = AtomicLong(0L)

    companion object {
        private const val MAX_REQUESTS_PER_MINUTE = 30
        private const val MINUTE_IN_MILLIS = 60_000L
        private const val MIN_REQUEST_INTERVAL_MS = 100L // Mínimo 100ms entre requests
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val now = System.currentTimeMillis()
        val endpoint = chain.request().url.encodedPath

        // ESTABILIDAD: Prevenir requests en ráfaga (mínimo 100ms entre cada uno)
        val lastTime = lastRequestTime.get()
        val timeSinceLastRequest = now - lastTime
        if (timeSinceLastRequest < MIN_REQUEST_INTERVAL_MS) {
            val sleepTime = MIN_REQUEST_INTERVAL_MS - timeSinceLastRequest
            Thread.sleep(sleepTime)
        }

        // ESTABILIDAD: Sliding window rate limiting por endpoint
        val timestamps = requestTimestamps.getOrPut(endpoint) { mutableListOf() }

        synchronized(timestamps) {
            // Limpiar timestamps viejos (más de 1 minuto)
            timestamps.removeAll { it < now - MINUTE_IN_MILLIS }

            // Verificar límite
            if (timestamps.size >= MAX_REQUESTS_PER_MINUTE) {
                android.util.Log.w(
                    "RateLimitInterceptor",
                    "Rate limit exceeded for $endpoint (${timestamps.size} requests in last minute)"
                )
                // Calcular tiempo de espera hasta que el request más viejo expire
                val oldestRequest = timestamps.minOrNull() ?: now
                val waitTime = MINUTE_IN_MILLIS - (now - oldestRequest) + 100
                if (waitTime > 0) {
                    Thread.sleep(waitTime)
                }
                // Limpiar nuevamente después de esperar
                timestamps.removeAll { it < System.currentTimeMillis() - MINUTE_IN_MILLIS }
            }

            // Registrar este request
            timestamps.add(System.currentTimeMillis())
        }

        lastRequestTime.set(System.currentTimeMillis())

        return chain.proceed(chain.request())
    }
}
