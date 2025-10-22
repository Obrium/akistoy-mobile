package com.akistoy.app.data.repository

import com.akistoy.app.core.util.AppDispatchers
import com.akistoy.app.data.beacon.BeaconScanner
import com.akistoy.app.domain.model.BeaconEvent
import com.akistoy.app.domain.repo.BeaconRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

@Singleton
class BeaconRepositoryImpl @Inject constructor(
    private val scanner: BeaconScanner,
    private val dispatchers: AppDispatchers
) : BeaconRepository {

    private val scope = CoroutineScope(dispatchers.io + SupervisorJob())
    private val _detections = MutableStateFlow<List<BeaconEvent>>(emptyList())
    override val detections: Flow<List<BeaconEvent>> = _detections.asStateFlow()

    private val recent = mutableMapOf<String, Instant>()

    init {
        scope.launch {
            scanner.detections.collect { event ->
                val key = "${event.beaconId}-${event.namespace ?: ""}"
                val now = Clock.System.now()
                val lastSeen = recent[key]
                if (lastSeen == null || now.minus(lastSeen) > DEDUP_WINDOW_MS) {
                    recent[key] = now
                    val updated = (_detections.value + event)
                        .takeLast(MAX_BUFFER_SIZE)
                    _detections.value = updated
                } else {
                    recent[key] = now
                }
            }
        }
    }

    override suspend fun startScanning(uuids: List<String>) {
        withContext(dispatchers.io) {
            scanner.startScanning(uuids)
        }
    }

    override suspend fun stopScanning() {
        withContext(dispatchers.io) {
            scanner.stopScanning()
        }
    }

    private fun Instant.minus(other: Instant): Long =
        this.toEpochMilliseconds() - other.toEpochMilliseconds()

    companion object {
        private const val MAX_BUFFER_SIZE = 50
        private const val DEDUP_WINDOW_MS = 10_000L
    }
}
