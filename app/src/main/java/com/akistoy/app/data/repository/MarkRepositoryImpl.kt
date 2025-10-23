package com.akistoy.app.data.repository

import com.akistoy.app.core.util.AppDispatchers
import com.akistoy.app.data.remote.api.AkistoyApi
import com.akistoy.app.data.remote.dto.BeaconMarkRequest
import com.akistoy.app.domain.model.BeaconEvent
import com.akistoy.app.domain.repo.MarkRepository
import com.akistoy.app.domain.repo.AuthRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant

@Singleton
class MarkRepositoryImpl @Inject constructor(
    private val api: AkistoyApi,
    private val authRepository: AuthRepository,
    private val dispatchers: AppDispatchers
) : MarkRepository {

    private val mutex = Mutex()
    private val buffer = ArrayDeque<BeaconEvent>()
    private val pendingFlow = MutableStateFlow(0)

    override fun pendingCount(): Flow<Int> = pendingFlow.asStateFlow()

    override suspend fun bufferMark(event: BeaconEvent) {
        mutex.withLock {
            buffer.addLast(event)
            pendingFlow.value = buffer.size
        }
    }

    override suspend fun sendBufferedMarks(): Result<Unit> = withContext(dispatchers.io) {
        val user = authRepository.getLoggedInUser()
        if (user == null) {
            return@withContext Result.failure(IllegalStateException("User not logged in"))
        }
        val batch = mutex.withLock {
            val snapshot = buffer.toList()
            buffer.clear()
            pendingFlow.value = buffer.size
            snapshot
        }
        if (batch.isEmpty()) return@withContext Result.success(Unit)
        return@withContext try {
            batch.forEach { event ->
                val request = BeaconMarkRequest(
                    deviceId = user.deviceId,
                    userId = user.id,
                    beaconId = event.beaconId,
                    rssi = event.rssi,
                    timestamp = event.timestamp.toString(),
                    eventType = event.eventType.name.lowercase()
                )
                api.sendMark(request)
            }
            Result.success(Unit)
        } catch (t: Throwable) {
            mutex.withLock {
                batch.forEach { buffer.addLast(it) }
                pendingFlow.value = buffer.size
            }
            Result.failure(t)
        }
    }
}
