package com.bestcallerapp.smswebhook.data.repository

import com.bestcallerapp.smswebhook.data.local.entity.DeliveryAttemptEntity
import com.bestcallerapp.smswebhook.data.local.entity.DeliveryWithWebhookName
import com.bestcallerapp.smswebhook.data.local.entity.MessageSummaryRow
import com.bestcallerapp.smswebhook.data.local.entity.SmsMessageEntity
import com.bestcallerapp.smswebhook.data.local.entity.WebhookOverviewRow
import com.bestcallerapp.smswebhook.domain.model.Delivery
import com.bestcallerapp.smswebhook.domain.model.DeliveryAttempt
import com.bestcallerapp.smswebhook.domain.model.MessageSummary
import com.bestcallerapp.smswebhook.domain.model.SmsMessage
import com.bestcallerapp.smswebhook.domain.model.WebhookOverview

internal fun SmsMessageEntity.toDomain() = SmsMessage(
    id = id,
    fingerprint = fingerprint,
    sender = sender,
    body = body,
    sentAtMillis = sentAt,
    receivedAtMillis = receivedAt,
    subscriptionId = subscriptionId,
    simSlot = simSlot,
    partCount = partCount,
    status = status,
    createdAtMillis = createdAt,
)

internal fun MessageSummaryRow.toDomain() = MessageSummary(id, sender, preview, receivedAt, status)

internal fun DeliveryWithWebhookName.toDomain() = Delivery(
    id = id,
    messageId = messageId,
    webhookId = webhookId,
    webhookName = webhookName,
    status = status,
    attemptCount = attemptCount,
    httpStatus = httpStatus,
    responseBody = response,
    errorType = errorType,
    errorMessage = error,
    payloadBody = payloadBody,
    contentType = contentType,
    lastAttemptAtMillis = lastAttemptAt,
    nextAttemptAtMillis = nextAttemptAt,
    createdAtMillis = createdAt,
    updatedAtMillis = updatedAt,
)

internal fun DeliveryAttemptEntity.toDomain() = DeliveryAttempt(
    id = id,
    deliveryId = deliveryId,
    messageId = messageId,
    webhookId = webhookId,
    webhookName = webhookName,
    attemptNumber = attemptNumber,
    requestMethod = requestMethod,
    requestUrl = requestUrl,
    httpStatus = httpStatus,
    durationMillis = durationMs,
    outcome = outcome,
    errorType = errorType,
    errorMessage = error,
    responseBody = response,
    startedAtMillis = startedAt,
)

internal fun DeliveryAttempt.toEntity() = DeliveryAttemptEntity(
    deliveryId = deliveryId,
    messageId = messageId,
    webhookId = webhookId,
    webhookName = webhookName,
    attemptNumber = attemptNumber,
    requestMethod = requestMethod,
    requestUrl = requestUrl,
    httpStatus = httpStatus,
    durationMs = durationMillis,
    outcome = outcome,
    errorType = errorType,
    error = errorMessage,
    response = responseBody,
    startedAt = startedAtMillis,
)

internal fun WebhookOverviewRow.toDomain() =
    WebhookOverview(id, name, url, enabled, scope, sentCount, failedCount, pendingCount)
