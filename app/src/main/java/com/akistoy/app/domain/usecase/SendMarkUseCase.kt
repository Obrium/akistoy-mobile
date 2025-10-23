package com.akistoy.app.domain.usecase

import com.akistoy.app.domain.model.BeaconEvent
import com.akistoy.app.domain.model.BeaconEventType
import com.akistoy.app.domain.repo.MarkRepository
import javax.inject.Inject

class SendMarkUseCase @Inject constructor(
    private val markRepository: MarkRepository
) {
    suspend operator fun invoke(event: BeaconEvent) {
        // Solo enviar eventos de ENTRY y EXIT al backend
        // Los eventos DETECTION se usan solo para UI/logging interno
        if (event.eventType == BeaconEventType.ENTRY || event.eventType == BeaconEventType.EXIT) {
            markRepository.bufferMark(event)
            markRepository.sendBufferedMarks()
        }
    }
}
