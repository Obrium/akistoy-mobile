package com.akistoy.app.data.repository

import com.akistoy.app.data.remote.api.AkistoyApi
import com.akistoy.app.domain.model.Zone
import com.akistoy.app.domain.repo.ConfigRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfigRepositoryImpl @Inject constructor(
    private val api: AkistoyApi,
    private val preferences: UserPreferencesDataSource
) : ConfigRepository {
    override suspend fun getTrackedUuids(): List<String> = preferences.getTrackedUuids()

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
