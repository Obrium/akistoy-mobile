package com.akistoy.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.akistoy.app.domain.model.User
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "akistoy_prefs")

@Singleton
class UserPreferencesDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val USER_ID = stringPreferencesKey("user_id")
        val NAME = stringPreferencesKey("name")
        val TOKEN = stringPreferencesKey("token")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val UUIDS = stringPreferencesKey("tracked_uuids")
        val SERVICE_ENABLED = booleanPreferencesKey("service_enabled")
    }

    val userFlow: Flow<User?> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            val userId = preferences[Keys.USER_ID]
            val name = preferences[Keys.NAME]
            val token = preferences[Keys.TOKEN]
            val deviceId = preferences[Keys.DEVICE_ID]
            if (userId != null && name != null && token != null && deviceId != null) {
                User(
                    id = userId,
                    name = name,
                    token = token,
                    deviceId = deviceId
                )
            } else {
                null
            }
        }

    val serviceEnabledFlow: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences -> preferences[Keys.SERVICE_ENABLED] ?: false }

    suspend fun setUser(user: User) {
        context.dataStore.edit { preferences ->
            preferences[Keys.USER_ID] = user.id
            preferences[Keys.NAME] = user.name
            preferences[Keys.TOKEN] = user.token
            preferences[Keys.DEVICE_ID] = user.deviceId
        }
    }

    suspend fun clearUser() {
        context.dataStore.edit { preferences ->
            preferences.remove(Keys.USER_ID)
            preferences.remove(Keys.NAME)
            preferences.remove(Keys.TOKEN)
            preferences.remove(Keys.DEVICE_ID)
        }
    }

    suspend fun getTrackedUuids(): List<String> {
        val preferences = context.dataStore.data.first()
        val raw = preferences[Keys.UUIDS]
        return raw?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
    }

    suspend fun setTrackedUuids(uuids: List<String>) {
        context.dataStore.edit { preferences ->
            preferences[Keys.UUIDS] = uuids.joinToString(",")
        }
    }

    suspend fun setServiceEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[Keys.SERVICE_ENABLED] = enabled
        }
    }

    suspend fun isServiceEnabled(): Boolean {
        val preferences = context.dataStore.data.first()
        return preferences[Keys.SERVICE_ENABLED] ?: false
    }
}
