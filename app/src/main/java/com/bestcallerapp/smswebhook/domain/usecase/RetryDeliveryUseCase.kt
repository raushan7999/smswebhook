package com.bestcallerapp.smswebhook.domain.usecase

import com.bestcallerapp.smswebhook.core.utils.TimeProvider
import com.bestcallerapp.smswebhook.domain.port.WorkScheduler
import com.bestcallerapp.smswebhook.domain.repository.DeliveryRepository
import com.bestcallerapp.smswebhook.domain.repository.WebhookRepository
import javax.inject.Inject

/** User-initiated retry of FAILED deliveries. Each retry starts a fresh retry budget. */
class RetryDeliveryUseCase @Inject constructor(
    private val deliveryRepository: DeliveryRepository,
    private val webhookRepository: WebhookRepository,
    private val scheduler: WorkScheduler,
    private val time: TimeProvider,
) {
    /** @return true if the delivery was re-queued. */
    suspend fun retry(deliveryId: Long): Boolean {
        val delivery = deliveryRepository.getDelivery(deliveryId) ?: return false
        if (!deliveryRepository.resetFailedForRetry(deliveryId, time.nowMillis())) return false
        val delay = webhookRepository.getWebhook(delivery.webhookId)?.retryDelaySeconds ?: DEFAULT_DELAY
        scheduler.scheduleDelivery(deliveryId, delay, replaceExisting = true)
        return true
    }

    /** @return number of deliveries re-queued. */
    suspend fun retryAllFailed(webhookId: Long): Int =
        deliveryRepository.failedIdsForWebhook(webhookId).count { retry(it) }

    private companion object {
        const val DEFAULT_DELAY = 30
    }
}
