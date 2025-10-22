package com.akistoy.app.domain.repo

import com.akistoy.app.domain.model.BeaconEvent
import kotlinx.coroutines.flow.Flow

interface MarkRepository {
    suspend fun bufferMark(event: BeaconEvent)
    suspend fun sendBufferedMarks(): Result<Unit>
    fun pendingCount(): Flow<Int>
}
