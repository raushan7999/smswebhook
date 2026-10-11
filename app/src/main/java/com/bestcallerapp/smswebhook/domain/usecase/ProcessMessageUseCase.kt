package com.bestcallerapp.smswebhook.domain.usecase

import com.bestcallerapp.smswebhook.core.logging.AppLogger
import com.bestcallerapp.smswebhook.core.utils.TimeProvider
import com.bestcallerapp.smswebhook.domain.engine.FilterEngine
import com.bestcallerapp.smswebhook.domain.engine.FilterSubject
import com.bestcallerapp.smswebhook.domain.engine.PayloadBuilder
import com.bestcallerapp.smswebhook.domain.model.DeliveryStatus
import com.bestcallerapp.smswebhook.domain.model.MessageStatus
import com.bestcallerapp.smswebhook.domain.model.NewDelivery
import com.bestcallerapp.smswebhook.domain.port.WorkScheduler
import com.bestcallerapp.smswebhook.domain.repository.DeliveryRepository
import com.bestcallerapp.smswebhook.domain.repository.SmsRepository
import com.bestcallerapp.smswebhook.domain.repository.WebhookRepository
import javax.inject.Inject

/**
 * Evaluates a stored message against every enabled webhook, renders the payload for each match
 * and queues deliveries. Safe to run more than once for the same message: deliveries are unique
 * per (message, webhook) and already-processed messages are skipped.
 */
class ProcessMessageUseCase @Inject constructor(
    private val smsRepository: SmsRepository,
    private val webhookRepository: WebhookRepository,
    private val deliveryRepository: DeliveryRepository,
    private val filterEngine: FilterEngine,
    private val payloadBuilder: PayloadBuilder,
    private val scheduler: WorkScheduler,
    private val time: TimeProvider,
    private val logger: AppLogger,
) {
    suspend operator fun invoke(messageId: Long) {
        val message = smsRepository.getMessage(messageId) ?: run {
            logger.w(TAG, "Message $messageId no longer exists")
            return
        }
        if (message.status != MessageStatus.RECEIVED) {
            // Already processed. If an earlier run committed deliveries but failed before queueing
            // them, queue them now (unique work makes this a no-op when they are queued).
            deliveryRepository.forMessage(messageId).filter { it.status.isActive }.forEach { delivery ->
                val delay = webhookRepository.getWebhook(delivery.webhookId)?.retryDelaySeconds ?: DEFAULT_DELAY
                scheduler.scheduleDelivery(delivery.id, delay)
            }
            return
        }

        val zone = time.zone()
        val subject = FilterSubject.from(message)
        val webhooks = webhookRepository.getEnabledWebhooks()
        val newDeliveries = webhooks.map { webhook ->
            if (filterEngine.shouldReceive(webhook, subject, zone)) {
                val payload = payloadBuilder.build(webhook.payload, message, zone)
                NewDelivery(webhook.id, DeliveryStatus.PENDING, payload.body, payload.contentType)
            } else {
                NewDelivery(webhook.id, DeliveryStatus.FILTERED, payloadBody = null, contentType = null)
            }
        }

        val pendingIds = deliveryRepository.createDeliveries(messageId, newDeliveries, time.nowMillis())
        val delayByWebhook = webhooks.associate { it.id to it.retryDelaySeconds }
        val created = deliveryRepository.forMessage(messageId).filter { it.id in pendingIds }
        created.forEach { delivery ->
            scheduler.scheduleDelivery(delivery.id, delayByWebhook[delivery.webhookId] ?: DEFAULT_DELAY)
        }
        logger.i(TAG, "Message $messageId: ${webhooks.size} webhook(s) evaluated, ${created.size} matched")
    }

    private companion object {
        const val TAG = "Processor"
        const val DEFAULT_DELAY = 30
    }
}
