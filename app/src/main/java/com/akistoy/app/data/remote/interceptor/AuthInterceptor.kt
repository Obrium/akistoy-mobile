package com.akistoy.app.data.remote.interceptor

import com.akistoy.app.data.repository.UserPreferencesDataSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.atomic.AtomicReference

/**
 * ESTABILIDAD: AuthInterceptor mejorado con cache de token
 * - Mantiene token en memoria para evitar llamadas blocking repetidas
 * - Timeout de 2s para prevenir ANR
 * - Actualiza cache automáticamente cuando el token cambia
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val preferences: UserPreferencesDataSource
) : Interceptor {

    // Cache atómico del token para lectura thread-safe
    private val cachedToken = AtomicReference<String?>(null)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        // Observar cambios del token y actualizar cache
        scope.launch {
            preferences.userFlow.collect { user ->
                cachedToken.set(user?.token)
            }
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        // ESTABILIDAD: Leer de cache primero (no blocking)
        var token = cachedToken.get()

        // Si no hay cache, cargar una vez con timeout
        if (token == null) {
            try {
                token = runBlocking {
                    withTimeout(2000) { // 2 segundos timeout
                        preferences.userFlow.firstOrNull()?.token
                    }
                }
                cachedToken.set(token)
            } catch (e: Exception) {
                // Si falla, continuar sin token (no crashear)
                android.util.Log.w("AuthInterceptor", "Error getting token: ${e.message}")
            }
        }

        val request = if (token != null) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }

        return chain.proceed(request)
    }
}
