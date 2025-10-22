package com.akistoy.app.domain.repo

import com.akistoy.app.domain.model.Zone

interface ConfigRepository {
    suspend fun getTrackedUuids(): List<String>
    suspend fun saveTrackedUuids(uuids: List<String>)
    suspend fun fetchRemoteConfig(): Result<List<Zone>>
}
