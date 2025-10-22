package com.akistoy.app.data.remote.interceptor

import com.akistoy.app.data.repository.UserPreferencesDataSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

@Singleton
class AuthInterceptor @Inject constructor(
    private val preferences: UserPreferencesDataSource
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val user = runBlocking { preferences.userFlow.firstOrNull() }
        val request = if (user?.token != null) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer ${user.token}")
                .build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}
