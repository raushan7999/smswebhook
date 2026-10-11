package com.bestcallerapp.smswebhook.domain.usecase

import com.bestcallerapp.smswebhook.domain.port.SmsListenerController
import com.bestcallerapp.smswebhook.domain.repository.SettingsRepository
import javax.inject.Inject

/** Starts/stops reacting to incoming SMS (persisted setting + receiver component state). */
class SetForwardingEnabledUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val listenerController: SmsListenerController,
) {
    suspend operator fun invoke(enabled: Boolean) {
        settingsRepository.update { it.copy(listenerEnabled = enabled) }
        listenerController.setListening(enabled)
    }
}
