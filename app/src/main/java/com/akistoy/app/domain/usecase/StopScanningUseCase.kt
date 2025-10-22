package com.akistoy.app.domain.usecase

import com.akistoy.app.domain.repo.BeaconRepository
import javax.inject.Inject

class StopScanningUseCase @Inject constructor(
    private val beaconRepository: BeaconRepository
) {
    suspend operator fun invoke() {
        beaconRepository.stopScanning()
    }
}
