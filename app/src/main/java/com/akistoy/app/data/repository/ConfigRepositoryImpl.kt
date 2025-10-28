package com.akistoy.app.data.repository

import com.akistoy.app.data.local.dao.TrustedBeaconDao
import com.akistoy.app.data.remote.api.AkistoyApi
import com.akistoy.app.domain.model.Zone
import com.akistoy.app.domain.repo.ConfigRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfigRepositoryImpl @Inject constructor(
    private val api: AkistoyApi,
    private val preferences: UserPreferencesDataSource,
    private val trustedBeaconDao: TrustedBeaconDao
) : ConfigRepository {
    override suspend fun getTrackedUuids(): List<String> {
        // Primero intenta obtener UUIDs de la base de datos de beacons confiables
        val dbUuids = trustedBeaconDao.getEnabledBeaconUuids()
        return if (dbUuids.isNotEmpty()) {
            dbUuids
        } else {
            // Fallback a las preferencias si no hay beacons confiables configurados
            preferences.getTrackedUuids()
        }
    }

    override suspend fun saveTrackedUuids(uuids: List<String>) {
        preferences.setTrackedUuids(uuids)
    }

    override suspend fun fetchRemoteConfig(): Result<List<Zone>> = runCatching {
        val config = api.getConfig()
        preferences.setTrackedUuids(config.uuids)
        config.zones.map { dto ->
            Zone(
                id = dto.id,
                name = dto.name,
                beaconUuids = dto.beaconIds
            )
        }
    }
}
