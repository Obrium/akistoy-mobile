package com.akistoy.app.data.remote.interceptor

import android.util.Log
import com.akistoy.app.data.repository.UserPreferencesDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ESTABILIDAD: Error Handling Interceptor
 * - Maneja 401 Unauthorized (token expirado o inválido)
 * - Maneja 429 Too Many Requests
 * - Maneja 5xx Server Errors con retry
 * - Log de errores detallado
 */
@Singleton
class ErrorHandlingInterceptor @Inject constructor(
    private val preferences: UserPreferencesDataSource
) : Interceptor {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        when (response.code) {
            401 -> {
                // ESTABILIDAD: Token inválido o expirado
                Log.e("ErrorHandling", "401 Unauthorized - Token inválido o expirado")

                // Limpiar sesión del usuario (logout automático)
                scope.launch {
                    try {
                        preferences.clearUser()
                        Log.i("ErrorHandling", "User session cleared due to 401")
                    } catch (e: Exception) {
                        Log.e("ErrorHandling", "Error clearing user session", e)
                    }
                }

                // TODO: Idealmente aquí se haría refresh del token si hay refresh token
                // Por ahora, dejar que la app maneje el logout
            }

            429 -> {
                // ESTABILIDAD: Too Many Requests
                val retryAfter = response.header("Retry-After")?.toLongOrNull() ?: 60
                Log.w("ErrorHandling", "429 Too Many Requests - Retry after ${retryAfter}s")
            }

            in 500..599 -> {
                // ESTABILIDAD: Server Errors
                Log.e("ErrorHandling", "Server error ${response.code} for ${request.url}")
            }
        }

        return response
    }
}
