package com.bestcallerapp.smswebhook.domain.usecase

import com.bestcallerapp.smswebhook.core.constants.AppConstants
import com.bestcallerapp.smswebhook.core.extensions.truncate
import com.bestcallerapp.smswebhook.core.logging.AppLogger
import com.bestcallerapp.smswebhook.core.network.UrlRedactor
import com.bestcallerapp.smswebhook.core.utils.TimeProvider
import com.bestcallerapp.smswebhook.domain.engine.Classification
import com.bestcallerapp.smswebhook.domain.engine.DeliveryResultClassifier
import com.bestcallerapp.smswebhook.domain.engine.RetryDecision
import com.bestcallerapp.smswebhook.domain.engine.RetryPolicy
import com.bestcallerapp.smswebhook.domain.engine.WebhookRequestFactory
import com.bestcallerapp.smswebhook.domain.model.AttemptOutcome
import com.bestcallerapp.smswebhook.domain.model.Delivery
import com.bestcallerapp.smswebhook.domain.model.DeliveryAttempt
import com.bestcallerapp.smswebhook.domain.model.DeliveryErrorType
import com.bestcallerapp.smswebhook.domain.model.DeliveryStatus
import com.bestcallerapp.smswebhook.domain.model.Webhook
import com.bestcallerapp.smswebhook.domain.port.HttpResult
import com.bestcallerapp.smswebhook.domain.port.WebhookSender
import com.bestcallerapp.smswebhook.domain.repository.DeliveryLogRepository
import com.bestcallerapp.smswebhook.domain.repository.DeliveryRepository
import com.bestcallerapp.smswebhook.domain.repository.SettingsRepository
import com.bestcallerapp.smswebhook.domain.repository.WebhookRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

enum class DeliveryRunResult {
    /** Delivered, or nothing left to do (already sent, removed, filtered). */
    DONE,

    /** Transient failure; the scheduler should run this delivery again with backoff. */
    RETRY,

    /** Permanent failure or retries exhausted. */
    FAILED,
}

/**
 * Sends one delivery. Idempotent: a delivery already marked SENT is never sent again, and the
 * SENT state is only recorded after a response that meets the success criteria.
 */
class DeliverWebhookUseCase @Inject constructor(
    private val deliveryRepository: DeliveryRepository,
    private val webhookRepository: WebhookRepository,
    private val logRepository: DeliveryLogRepository,
    private val settingsRepository: SettingsRepository,
    private val sender: WebhookSender,
    private val requestFactory: WebhookRequestFactory,
    private val classifier: DeliveryResultClassifier,
    private val retryPolicy: RetryPolicy,
    private val smsDeletion: SmsDeletionCoordinator,
    private val time: TimeProvider,
    private val logger: AppLogger,
) {

    /**
     * @param runAttempt zero-based attempt index within the current retry chain (WorkManager's
     *   runAttemptCount). A manual retry starts a fresh chain with a fresh retry budget.
     */
    suspend operator fun invoke(deliveryId: Long, runAttempt: Int): DeliveryRunResult {
        val delivery = deliveryRepository.getDelivery(deliveryId) ?: return DeliveryRunResult.DONE
        if (delivery.status == DeliveryStatus.SENT || delivery.status == DeliveryStatus.FILTERED) {
            return DeliveryRunResult.DONE
        }

        val webhook = webhookRepository.getWebhook(delivery.webhookId)
        precondition(delivery, webhook)?.let { (type, message) ->
            deliveryRepository.markFailed(deliveryId, null, null, type, message, time.nowMillis())
            logger.w(TAG, "Delivery $deliveryId not sent: $type")
            return DeliveryRunResult.FAILED
        }
        webhook!!

        if (!deliveryRepository.markProcessing(deliveryId, time.nowMillis())) return DeliveryRunResult.DONE

        val request = requestFactory.create(
            webhook = webhook,
            body = delivery.payloadBody.orEmpty(),
            payloadContentType = delivery.contentType.orEmpty(),
            extraHeaders = listOf(
                AppConstants.IDEMPOTENCY_HEADER to "${delivery.messageId}-${delivery.webhookId}-${delivery.createdAtMillis}",
                AppConstants.DELIVERY_ID_HEADER to delivery.id.toString(),
            ),
        )
        val startedAt = time.nowMillis()
        val result = sender.send(request)
        val classification = classifier.classify(result)
        val now = time.nowMillis()
        val httpStatus = (result as? HttpResult.Response)?.code
        val responseBody = (result as? HttpResult.Response)?.body?.truncate(AppConstants.MAX_STORED_RESPONSE_CHARS)

        // Persist the outcome before anything else can fail, so a successful send is never repeated.
        val outcome = applyOutcome(deliveryId, runAttempt, webhook, classification, result, httpStatus, responseBody, now)
        recordAttempt(delivery, webhook, request.method.name, startedAt, result, classification, responseBody)
        if (classification.outcome == AttemptOutcome.SUCCESS) smsDeletion.onDeliverySucceeded(delivery.messageId)
        return outcome
    }

    private suspend fun applyOutcome(
        deliveryId: Long,
        runAttempt: Int,
        webhook: Webhook,
        classification: Classification,
        result: HttpResult,
        httpStatus: Int?,
        responseBody: String?,
        now: Long,
    ): DeliveryRunResult {
        return when (classification.outcome) {
            AttemptOutcome.SUCCESS -> {
                deliveryRepository.markSent(deliveryId, httpStatus, responseBody, now)
                logger.i(TAG, "Delivery $deliveryId sent (HTTP $httpStatus, ${result.durationMillis} ms)")
                DeliveryRunResult.DONE
            }
            AttemptOutcome.RETRYABLE_FAILURE -> {
                when (val decision = retryPolicy.decide(runAttempt + 1, webhook.maxRetries, webhook.retryDelaySeconds)) {
                    is RetryDecision.Retry -> {
                        deliveryRepository.markRetrying(
                            deliveryId, httpStatus, responseBody, classification.errorType,
                            classification.errorMessage, now + decision.delayMillis, now,
                        )
                        logger.w(TAG, "Delivery $deliveryId failed (${classification.errorType}); retry scheduled")
                        DeliveryRunResult.RETRY
                    }
                    RetryDecision.GiveUp -> {
                        deliveryRepository.markFailed(
                            deliveryId, httpStatus, responseBody, classification.errorType,
                            "${classification.errorMessage} (retries exhausted)", now,
                        )
                        logger.w(TAG, "Delivery $deliveryId failed after ${runAttempt + 1} attempt(s)")
                        DeliveryRunResult.FAILED
                    }
                }
            }
            AttemptOutcome.PERMANENT_FAILURE -> {
                deliveryRepository.markFailed(
                    deliveryId, httpStatus, responseBody, classification.errorType, classification.errorMessage, now,
                )
                logger.w(TAG, "Delivery $deliveryId failed permanently (${classification.errorType})")
                DeliveryRunResult.FAILED
            }
        }
    }

    private fun precondition(delivery: Delivery, webhook: Webhook?): Pair<DeliveryErrorType, String>? = when {
        webhook == null -> DeliveryErrorType.WEBHOOK_REMOVED to "Webhook no longer exists"
        !webhook.enabled -> DeliveryErrorType.WEBHOOK_DISABLED to "Webhook was disabled before delivery"
        webhook.headers.any { it.needsReentry } ->
            DeliveryErrorType.CREDENTIAL_UNAVAILABLE to "A secret header could not be decrypted; re-enter it in the webhook settings"
        delivery.payloadBody == null -> DeliveryErrorType.INVALID_CONFIGURATION to "Delivery has no payload"
        else -> null
    }

    private suspend fun recordAttempt(
        delivery: Delivery,
        webhook: Webhook,
        method: String,
        startedAt: Long,
        result: HttpResult,
        classification: Classification,
        responseBody: String?,
    ) {
        if (!settingsRepository.current().keepDeliveryLogs) return
        // Best effort: the log must never turn a recorded delivery outcome into a retry.
        try {
            logRepository.record(
            DeliveryAttempt(
                deliveryId = delivery.id,
                messageId = delivery.messageId,
                webhookId = webhook.id,
                webhookName = webhook.name,
                attemptNumber = delivery.attemptCount + 1,
                requestMethod = method,
                requestUrl = UrlRedactor.redact(webhook.url),
                httpStatus = (result as? HttpResult.Response)?.code,
                durationMillis = result.durationMillis,
                outcome = classification.outcome,
                errorType = classification.errorType,
                errorMessage = classification.errorMessage,
                responseBody = responseBody,
                startedAtMillis = startedAt,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w(TAG, "Could not record attempt for delivery ${delivery.id}", e)
        }
    }

    private companion object {
        const val TAG = "Delivery"
    }
}
