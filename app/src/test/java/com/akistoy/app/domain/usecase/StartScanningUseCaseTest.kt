package com.akistoy.app.domain.usecase

import com.akistoy.app.domain.repo.BeaconRepository
import com.akistoy.app.domain.repo.ConfigRepository
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test

private class FakeBeaconRepository : BeaconRepository {
    var lastStarted: List<String>? = null
    override val detections = kotlinx.coroutines.flow.emptyFlow<List<com.akistoy.app.domain.model.BeaconEvent>>()
    override suspend fun startScanning(uuids: List<String>) { lastStarted = uuids }
    override suspend fun stopScanning() {}
}

private class FakeConfigRepository(private val uuids: List<String>) : ConfigRepository {
    override suspend fun getTrackedUuids(): List<String> = uuids
    override suspend fun saveTrackedUuids(uuids: List<String>) {}
    override suspend fun fetchRemoteConfig(): Result<List<com.akistoy.app.domain.model.Zone>> = Result.success(emptyList())
}

@OptIn(ExperimentalCoroutinesApi::class)
class StartScanningUseCaseTest {
    @Test
    fun invokes_repository_with_tracked_uuids() = runTest {
        val beaconRepository = FakeBeaconRepository()
        val configRepository = FakeConfigRepository(listOf("uuid1", "uuid2"))
        val useCase = StartScanningUseCase(beaconRepository, configRepository)

        useCase()

        assertEquals(listOf("uuid1", "uuid2"), beaconRepository.lastStarted)
    }
}
