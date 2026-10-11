package com.bestcallerapp.smswebhook.domain.repository

import androidx.paging.PagingData
import com.bestcallerapp.smswebhook.domain.model.AppSettings
import com.bestcallerapp.smswebhook.domain.model.DashboardStats
import com.bestcallerapp.smswebhook.domain.model.Delivery
import com.bestcallerapp.smswebhook.domain.model.DeliveryAttempt
import com.bestcallerapp.smswebhook.domain.model.DeliveryErrorType
import com.bestcallerapp.smswebhook.domain.model.IncomingSms
import com.bestcallerapp.smswebhook.domain.model.LogFilter
import com.bestcallerapp.smswebhook.domain.model.MessageQuery
import com.bestcallerapp.smswebhook.domain.model.MessageSummary
import com.bestcallerapp.smswebhook.domain.model.NewDelivery
import com.bestcallerapp.smswebhook.domain.model.SmsMessage
import com.bestcallerapp.smswebhook.domain.model.Webhook
import com.bestcallerapp.smswebhook.domain.model.WebhookOverview
import kotlinx.coroutines.flow.Flow

interface SmsRepository {
    /** Persists [sms] unless a message with the same fingerprint exists. Returns the new id or null for a duplicate. */
    suspend fun insertIfAbsent(sms: IncomingSms, fingerprint: String, createdAtMillis: Long): Long?

    suspend fun getMessage(id: Long): SmsMessage?

    /** Id of the message with [fingerprint] if it has not been processed yet. */
    suspend fun unprocessedIdForFingerprint(fingerprint: String): Long?
    fun observeMessage(id: Long): Flow<SmsMessage?>
    fun pagedMessages(query: MessageQuery): Flow<PagingData<MessageSummary>>
    fun observeRecent(limit: Int): Flow<List<MessageSummary>>
    fun observeCount(): Flow<Int>

    /** Messages stored but never evaluated against webhook rules (e.g. process died). */
    suspend fun unprocessedMessageIds(): List<Long>

    /** Deletes inactive messages received before [cutoffMillis]; returns the number removed. */
    suspend fun deleteInactiveOlderThan(cutoffMillis: Long): Int

    /** Keeps the newest [keep] messages, deleting older inactive ones; returns the number removed. */
    suspend fun deleteInactiveBeyondNewest(keep: Int): Int

    suspend fun deleteMessage(id: Long)
    suspend fun clearInactive()
}

interface DeliveryRepository {
    /**
     * Atomically records the deliveries for a processed message (ignoring message/webhook pairs
     * that already exist) and updates the message status. Returns ids of newly created PENDING deliveries.
     */
    suspend fun createDeliveries(messageId: Long, deliveries: List<NewDelivery>, nowMillis: Long): List<Long>

    suspend fun getDelivery(id: Long): Delivery?
    fun observeForMessage(messageId: Long): Flow<List<Delivery>>
    suspend fun forMessage(messageId: Long): List<Delivery>
    fun pagedForWebhook(webhookId: Long, failedOnly: Boolean): Flow<PagingData<Delivery>>

    /** Claims a delivery for sending. Returns false when it was already sent or no longer exists. */
    suspend fun markProcessing(id: Long, nowMillis: Long): Boolean

    suspend fun markSent(id: Long, httpStatus: Int?, response: String?, nowMillis: Long)

    suspend fun markRetrying(
        id: Long,
        httpStatus: Int?,
        response: String?,
        errorType: DeliveryErrorType?,
        errorMessage: String?,
        nextAttemptAtMillis: Long,
        nowMillis: Long,
    )

    suspend fun markFailed(
        id: Long,
        httpStatus: Int?,
        response: String?,
        errorType: DeliveryErrorType?,
        errorMessage: String?,
        nowMillis: Long,
    )

    /** FAILED → PENDING for a manual retry. Returns false if the delivery is not in FAILED state. */
    suspend fun resetFailedForRetry(id: Long, nowMillis: Long): Boolean

    suspend fun failedIdsForWebhook(webhookId: Long): List<Long>

    /** Deliveries that should have queued work (PENDING / RETRYING / PROCESSING). */
    suspend fun activeDeliveryIds(): List<Long>

    fun observeDashboardStats(): Flow<DashboardStats>
}

interface DeliveryLogRepository {
    suspend fun record(attempt: DeliveryAttempt)
    fun paged(filter: LogFilter): Flow<PagingData<DeliveryAttempt>>
    fun observeForMessage(messageId: Long): Flow<List<DeliveryAttempt>>
    suspend fun deleteOlderThan(cutoffMillis: Long): Int
    suspend fun clear()
}

interface WebhookRepository {
    fun observeOverviews(): Flow<List<WebhookOverview>>
    fun observeWebhook(id: Long): Flow<Webhook?>
    suspend fun getWebhook(id: Long): Webhook?
    suspend fun getEnabledWebhooks(): List<Webhook>
    suspend fun getAll(): List<Webhook>

    /** Inserts when [Webhook.id] is 0, otherwise replaces the whole configuration. Returns the id. */
    suspend fun save(webhook: Webhook): Long

    suspend fun setEnabled(id: Long, enabled: Boolean, nowMillis: Long)
    suspend fun delete(id: Long)
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun current(): AppSettings
    suspend fun update(transform: (AppSettings) -> AppSettings)
}
