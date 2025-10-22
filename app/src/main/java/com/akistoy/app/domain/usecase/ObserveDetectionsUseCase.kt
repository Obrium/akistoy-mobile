package com.akistoy.app.domain.usecase

import com.akistoy.app.domain.model.BeaconEvent
import com.akistoy.app.domain.repo.BeaconRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveDetectionsUseCase @Inject constructor(
    private val beaconRepository: BeaconRepository
) {
    operator fun invoke(): Flow<List<BeaconEvent>> = beaconRepository.detections
}
