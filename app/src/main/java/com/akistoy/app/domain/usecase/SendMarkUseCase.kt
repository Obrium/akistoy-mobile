package com.akistoy.app.domain.usecase

import com.akistoy.app.domain.model.BeaconEvent
import com.akistoy.app.domain.repo.MarkRepository
import javax.inject.Inject

class SendMarkUseCase @Inject constructor(
    private val markRepository: MarkRepository
) {
    suspend operator fun invoke(event: BeaconEvent) {
        markRepository.bufferMark(event)
        markRepository.sendBufferedMarks()
    }
}
