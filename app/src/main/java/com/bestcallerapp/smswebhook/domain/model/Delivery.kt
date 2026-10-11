package com.bestcallerapp.smswebhook.domain.model

data class Delivery(
    val id: Long,
    val messageId: Long,
    val webhookId: Long,
    val webhookName: String,
    val status: DeliveryStatus,
    val attemptCount: Int,
    val httpStatus: Int?,
    val responseBody: String?,
    val errorType: DeliveryErrorType?,
    val errorMessage: String?,
    val payloadBody: String?,
    val contentType: String?,
    val lastAttemptAtMillis: Long?,
    val nextAttemptAtMillis: Long?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

/** A delivery that should be created for a freshly processed message. */
data class NewDelivery(
    val webhookId: Long,
    val status: DeliveryStatus,
    val payloadBody: String?,
    val contentType: String?,
)

enum class AttemptOutcome { SUCCESS, RETRYABLE_FAILURE, PERMANENT_FAILURE }

/** Immutable log entry describing one HTTP attempt. Never contains header values. */
data class DeliveryAttempt(
    val id: Long = 0,
    val deliveryId: Long,
    val messageId: Long,
    val webhookId: Long,
    val webhookName: String,
    val attemptNumber: Int,
    val requestMethod: String,
    val requestUrl: String,
    val httpStatus: Int?,
    val durationMillis: Long,
    val outcome: AttemptOutcome,
    val errorType: DeliveryErrorType?,
    val errorMessage: String?,
    val responseBody: String?,
    val startedAtMillis: Long,
)

data class DashboardStats(
    val totalMessages: Int = 0,
    val pendingDeliveries: Int = 0,
    val sentDeliveries: Int = 0,
    val failedDeliveries: Int = 0,
    val enabledWebhooks: Int = 0,
    val totalWebhooks: Int = 0,
)

/** Filters exposed by the history screen; [statuses] empty means "any". */
data class MessageQuery(
    val search: String = "",
    val statuses: Set<MessageStatus> = emptySet(),
)

enum class LogFilter { ALL, SUCCESS, FAILURE }
