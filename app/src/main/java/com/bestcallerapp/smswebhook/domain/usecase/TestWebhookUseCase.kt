package com.bestcallerapp.smswebhook.domain.usecase

import com.bestcallerapp.smswebhook.core.constants.AppConstants
import com.bestcallerapp.smswebhook.core.extensions.truncate
import com.bestcallerapp.smswebhook.core.utils.TimeProvider
import com.bestcallerapp.smswebhook.domain.engine.DeliveryResultClassifier
import com.bestcallerapp.smswebhook.domain.engine.PayloadBuilder
import com.bestcallerapp.smswebhook.domain.engine.WebhookRequestFactory
import com.bestcallerapp.smswebhook.domain.engine.WebhookValidator
import com.bestcallerapp.smswebhook.domain.model.AttemptOutcome
import com.bestcallerapp.smswebhook.domain.model.MessageStatus
import com.bestcallerapp.smswebhook.domain.model.SmsMessage
import com.bestcallerapp.smswebhook.domain.model.Webhook
import com.bestcallerapp.smswebhook.domain.port.HttpResult
import com.bestcallerapp.smswebhook.domain.port.WebhookSender
import javax.inject.Inject

data class WebhookTestResult(
    val success: Boolean,
    val httpStatus: Int?,
    val durationMillis: Long,
    val responseBody: String?,
    val errorMessage: String?,
    val requestBody: String,
)

/** Sends a sample SMS to a (possibly unsaved) webhook configuration. Nothing is persisted. */
class TestWebhookUseCase @Inject constructor(
    private val sender: WebhookSender,
    private val payloadBuilder: PayloadBuilder,
    private val requestFactory: WebhookRequestFactory,
    private val classifier: DeliveryResultClassifier,
    private val validator: WebhookValidator,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(
        webhook: Webhook,
        sampleSender: String = SAMPLE_SENDER,
        sampleBody: String = SAMPLE_BODY,
    ): WebhookTestResult {
        val now = time.nowMillis()
        val sample = sampleMessage(sampleSender, sampleBody, now)
        val payload = payloadBuilder.build(webhook.payload, sample, time.zone())
        validator.validateUrl(webhook.url)?.let { error ->
            return WebhookTestResult(false, null, 0, null, error, payload.body)
        }
        val request = requestFactory.create(
            webhook, payload.body, payload.contentType, listOf(TEST_HEADER to "true"),
        )
        val result = sender.send(request)
        val classification = classifier.classify(result)
        val response = result as? HttpResult.Response
        return WebhookTestResult(
            success = classification.outcome == AttemptOutcome.SUCCESS,
            httpStatus = response?.code,
            durationMillis = result.durationMillis,
            responseBody = response?.body?.truncate(AppConstants.MAX_STORED_RESPONSE_CHARS),
            errorMessage = classification.errorMessage,
            requestBody = payload.body,
        )
    }

    companion object {
        const val SAMPLE_SENDER = "TEST"
        const val SAMPLE_BODY = "This is a test SMS"
        private const val TEST_HEADER = "X-SmsWebhook-Test"

        fun sampleMessage(sender: String, body: String, nowMillis: Long) = SmsMessage(
            id = 0,
            fingerprint = "test-$nowMillis",
            sender = sender,
            body = body,
            sentAtMillis = nowMillis,
            receivedAtMillis = nowMillis,
            subscriptionId = null,
            simSlot = 0,
            partCount = 1,
            status = MessageStatus.RECEIVED,
            createdAtMillis = nowMillis,
        )
    }
}
