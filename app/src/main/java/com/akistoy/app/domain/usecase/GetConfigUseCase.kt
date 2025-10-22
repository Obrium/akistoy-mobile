package com.akistoy.app.domain.usecase

import com.akistoy.app.domain.model.Zone
import com.akistoy.app.domain.repo.ConfigRepository
import javax.inject.Inject

class GetConfigUseCase @Inject constructor(
    private val configRepository: ConfigRepository
) {
    suspend operator fun invoke(): Result<List<Zone>> = configRepository.fetchRemoteConfig()
}
