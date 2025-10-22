package com.akistoy.app.domain.usecase

import com.akistoy.app.domain.repo.BeaconRepository
import com.akistoy.app.domain.repo.ConfigRepository
import javax.inject.Inject

class StartScanningUseCase @Inject constructor(
    private val beaconRepository: BeaconRepository,
    private val configRepository: ConfigRepository
) {
    suspend operator fun invoke() {
        val uuids = configRepository.getTrackedUuids()
        beaconRepository.startScanning(uuids)
    }
}
