package com.bestcallerapp.smswebhook.domain.usecase

import com.bestcallerapp.smswebhook.domain.port.WorkScheduler
import com.bestcallerapp.smswebhook.domain.repository.DeliveryRepository
import com.bestcallerapp.smswebhook.domain.repository.SettingsRepository
import com.bestcallerapp.smswebhook.domain.repository.WebhookRepository
import javax.inject.Inject

/** Changes the "Only Wi-Fi" setting and applies it to deliveries that are already queued. */
class SetWifiOnlyUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val deliveryRepository: DeliveryRepository,
    private val webhookRepository: WebhookRepository,
    private val scheduler: WorkScheduler,
) {
    suspend operator fun invoke(wifiOnly: Boolean) {
        settingsRepository.update { it.copy(wifiOnly = wifiOnly) }
        val delays = mutableMapOf<Long, Int>()
        deliveryRepository.activeDeliveryIds().forEach { id ->
            val webhookId = deliveryRepository.getDelivery(id)?.webhookId ?: return@forEach
            val delay = delays.getOrPut(webhookId) { webhookRepository.getWebhook(webhookId)?.retryDelaySeconds ?: DEFAULT_DELAY }
            runCatching { scheduler.refreshDeliveryConstraints(id, delay) }
        }
    }

    private companion object {
        const val DEFAULT_DELAY = 30
    }
}
