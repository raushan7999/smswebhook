package com.bestcallerapp.smswebhook.fakes

import androidx.paging.PagingData
import com.bestcallerapp.smswebhook.core.logging.AppLogger
import com.bestcallerapp.smswebhook.core.utils.TimeProvider
import com.bestcallerapp.smswebhook.domain.engine.MessageStatusAggregator
import com.bestcallerapp.smswebhook.domain.model.AppSettings
import com.bestcallerapp.smswebhook.domain.model.DashboardStats
import com.bestcallerapp.smswebhook.domain.model.Delivery
import com.bestcallerapp.smswebhook.domain.model.DeliveryAttempt
import com.bestcallerapp.smswebhook.domain.model.DeliveryErrorType
import com.bestcallerapp.smswebhook.domain.model.DeliveryStatus
import com.bestcallerapp.smswebhook.domain.model.IncomingSms
import com.bestcallerapp.smswebhook.domain.model.LogFilter
import com.bestcallerapp.smswebhook.domain.model.MessageQuery
import com.bestcallerapp.smswebhook.domain.model.MessageStatus
import com.bestcallerapp.smswebhook.domain.model.MessageSummary
import com.bestcallerapp.smswebhook.domain.model.NewDelivery
import com.bestcallerapp.smswebhook.domain.model.SmsMessage
import com.bestcallerapp.smswebhook.domain.model.Webhook
import com.bestcallerapp.smswebhook.domain.model.WebhookOverview
import com.bestcallerapp.smswebhook.domain.port.DeviceInfoProvider
import com.bestcallerapp.smswebhook.domain.port.HttpRequestSpec
import com.bestcallerapp.smswebhook.domain.port.HttpResult
import com.bestcallerapp.smswebhook.domain.port.SmsDeleter
import com.bestcallerapp.smswebhook.domain.port.SmsDeletionResult
import com.bestcallerapp.smswebhook.domain.port.WebhookSender
import com.bestcallerapp.smswebhook.domain.port.WorkScheduler
import com.bestcallerapp.smswebhook.domain.repository.DeliveryLogRepository
import com.bestcallerapp.smswebhook.domain.repository.DeliveryRepository
import com.bestcallerapp.smswebhook.domain.repository.SettingsRepository
import com.bestcallerapp.smswebhook.domain.repository.SmsRepository
import com.bestcallerapp.smswebhook.domain.repository.WebhookRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import java.time.ZoneId
import java.time.ZoneOffset

class FakeTime(var now: Long = 1_791_212_400_000L, private val zoneId: ZoneId = ZoneOffset.ofHoursMinutes(5, 30)) : TimeProvider {
    override fun nowMillis() = now
    override fun zone() = zoneId
}

object NoopLogger : AppLogger {
    override fun d(tag: String, message: String) = Unit
    override fun i(tag: String, message: String) = Unit
    override fun w(tag: String, message: String, throwable: Throwable?) = Unit
    override fun e(tag: String, message: String, throwable: Throwable?) = Unit
}

object FakeDeviceInfo : DeviceInfoProvider {
    override fun description() = "Test Device (Android 15)"
}

class FakeSmsDeleter(var supported: Boolean = false) : SmsDeleter {
    val deleted = mutableListOf<Long>()
    override fun isSupported() = supported
    override suspend fun delete(message: SmsMessage): SmsDeletionResult {
        deleted += message.id
        return SmsDeletionResult.Deleted
    }
}

/** Records scheduling calls; tests drive the "workers" explicitly for determinism. */
class RecordingScheduler : WorkScheduler {
    val processing = mutableListOf<Long>()
    val deliveries = mutableListOf<Long>()
    var cleanups = 0
    override fun scheduleMessageProcessing(messageId: Long) { processing += messageId }
    override suspend fun scheduleDelivery(deliveryId: Long, retryDelaySeconds: Int, replaceExisting: Boolean) { deliveries += deliveryId }
    val refreshed = mutableListOf<Long>()
    override suspend fun refreshDeliveryConstraints(deliveryId: Long, retryDelaySeconds: Int) { refreshed += deliveryId }
    override fun scheduleCleanupNow() { cleanups++ }
    override fun ensurePeriodicMaintenance() = Unit
    override fun scheduleReconcile() = Unit
}

class ScriptedSender(vararg results: HttpResult) : WebhookSender {
    private val queue = ArrayDeque(results.toList())
    val requests = mutableListOf<HttpRequestSpec>()
    override suspend fun send(request: HttpRequestSpec): HttpResult {
        requests += request
        return queue.removeFirstOrNull() ?: HttpResult.Response(200, "ok", 5)
    }
}

class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = state
    override suspend fun current() = state.value
    override suspend fun update(transform: (AppSettings) -> AppSettings) { state.value = transform(state.value) }
}

class InMemorySmsRepository : SmsRepository {
    val messages = linkedMapOf<Long, SmsMessage>()
    private var nextId = 1L

    override suspend fun insertIfAbsent(sms: IncomingSms, fingerprint: String, createdAtMillis: Long): Long? {
        if (messages.values.any { it.fingerprint == fingerprint }) return null
        val id = nextId++
        messages[id] = SmsMessage(
            id, fingerprint, sms.sender, sms.body, sms.sentAtMillis, sms.receivedAtMillis,
            sms.subscriptionId, sms.simSlot, sms.partCount, MessageStatus.RECEIVED, createdAtMillis,
        )
        return id
    }

    override suspend fun getMessage(id: Long) = messages[id]
    override suspend fun unprocessedIdForFingerprint(fingerprint: String) =
        messages.values.firstOrNull { it.fingerprint == fingerprint && it.status == MessageStatus.RECEIVED }?.id
    override fun observeMessage(id: Long): Flow<SmsMessage?> = MutableStateFlow(messages[id])
    override fun pagedMessages(query: MessageQuery): Flow<PagingData<MessageSummary>> = emptyFlow()
    override fun observeRecent(limit: Int): Flow<List<MessageSummary>> = emptyFlow()
    override fun observeCount(): Flow<Int> = MutableStateFlow(messages.size)
    override suspend fun unprocessedMessageIds() = messages.values.filter { it.status == MessageStatus.RECEIVED }.map { it.id }

    override suspend fun deleteInactiveOlderThan(cutoffMillis: Long): Int {
        val doomed = messages.values.filter { it.receivedAtMillis < cutoffMillis && !it.status.isActive }.map { it.id }
        doomed.forEach { messages.remove(it) }
        return doomed.size
    }

    override suspend fun deleteInactiveBeyondNewest(keep: Int): Int {
        val keepIds = messages.keys.sortedDescending().take(keep).toSet()
        val doomed = messages.values.filter { it.id !in keepIds && !it.status.isActive }.map { it.id }
        doomed.forEach { messages.remove(it) }
        return doomed.size
    }

    override suspend fun deleteMessage(id: Long) { messages.remove(id) }
    override suspend fun clearInactive() { messages.values.filter { !it.status.isActive }.map { it.id }.forEach { messages.remove(it) } }

    fun setStatus(id: Long, status: MessageStatus) {
        messages[id]?.let { messages[id] = it.copy(status = status) }
    }
}

class InMemoryWebhookRepository : WebhookRepository {
    val webhooks = linkedMapOf<Long, Webhook>()
    private var nextId = 1L
    override fun observeOverviews(): Flow<List<WebhookOverview>> = emptyFlow()
    override fun observeWebhook(id: Long): Flow<Webhook?> = MutableStateFlow(webhooks[id])
    override suspend fun getWebhook(id: Long) = webhooks[id]
    override suspend fun getEnabledWebhooks() = webhooks.values.filter { it.enabled }
    override suspend fun getAll() = webhooks.values.toList()
    override suspend fun save(webhook: Webhook): Long {
        val id = if (webhook.id == 0L) nextId++ else webhook.id
        webhooks[id] = webhook.copy(id = id)
        return id
    }
    override suspend fun setEnabled(id: Long, enabled: Boolean, nowMillis: Long) {
        webhooks[id]?.let { webhooks[id] = it.copy(enabled = enabled) }
    }
    override suspend fun delete(id: Long) { webhooks.remove(id) }
}

class InMemoryDeliveryRepository(
    private val sms: InMemorySmsRepository,
    private val webhooks: InMemoryWebhookRepository,
) : DeliveryRepository {
    val deliveries = linkedMapOf<Long, Delivery>()
    private var nextId = 1L

    override suspend fun createDeliveries(messageId: Long, deliveries: List<NewDelivery>, nowMillis: Long): List<Long> {
        val created = deliveries.mapNotNull { new ->
            if (this.deliveries.values.any { it.messageId == messageId && it.webhookId == new.webhookId }) return@mapNotNull null
            val id = nextId++
            this.deliveries[id] = Delivery(
                id, messageId, new.webhookId, webhooks.webhooks[new.webhookId]?.name.orEmpty(), new.status, 0, null, null,
                null, null, new.payloadBody, new.contentType, null, null, nowMillis, nowMillis,
            )
            if (new.status == DeliveryStatus.PENDING) id else null
        }
        recompute(messageId)
        return created
    }

    override suspend fun getDelivery(id: Long) = deliveries[id]
    override fun observeForMessage(messageId: Long): Flow<List<Delivery>> = MutableStateFlow(deliveries.values.toList()).map { list -> list.filter { it.messageId == messageId } }
    override suspend fun forMessage(messageId: Long) = deliveries.values.filter { it.messageId == messageId }
    override fun pagedForWebhook(webhookId: Long, failedOnly: Boolean): Flow<PagingData<Delivery>> = emptyFlow()

    override suspend fun markProcessing(id: Long, nowMillis: Long): Boolean {
        val d = deliveries[id] ?: return false
        if (!d.status.isActive) return false
        set(d.copy(status = DeliveryStatus.PROCESSING, attemptCount = d.attemptCount + 1, lastAttemptAtMillis = nowMillis))
        return true
    }

    override suspend fun markSent(id: Long, httpStatus: Int?, response: String?, nowMillis: Long) {
        deliveries[id]?.let { set(it.copy(status = DeliveryStatus.SENT, httpStatus = httpStatus, responseBody = response, errorType = null, errorMessage = null)) }
    }

    override suspend fun markRetrying(id: Long, httpStatus: Int?, response: String?, errorType: DeliveryErrorType?, errorMessage: String?, nextAttemptAtMillis: Long, nowMillis: Long) {
        deliveries[id]?.let { set(it.copy(status = DeliveryStatus.RETRYING, httpStatus = httpStatus, errorType = errorType, errorMessage = errorMessage, nextAttemptAtMillis = nextAttemptAtMillis)) }
    }

    override suspend fun markFailed(id: Long, httpStatus: Int?, response: String?, errorType: DeliveryErrorType?, errorMessage: String?, nowMillis: Long) {
        deliveries[id]?.let { set(it.copy(status = DeliveryStatus.FAILED, httpStatus = httpStatus, errorType = errorType, errorMessage = errorMessage)) }
    }

    override suspend fun resetFailedForRetry(id: Long, nowMillis: Long): Boolean {
        val d = deliveries[id] ?: return false
        if (d.status != DeliveryStatus.FAILED) return false
        set(d.copy(status = DeliveryStatus.PENDING))
        return true
    }

    override suspend fun failedIdsForWebhook(webhookId: Long) =
        deliveries.values.filter { it.webhookId == webhookId && it.status == DeliveryStatus.FAILED }.map { it.id }

    override suspend fun activeDeliveryIds() = deliveries.values.filter { it.status.isActive }.map { it.id }
    override fun observeDashboardStats(): Flow<DashboardStats> = emptyFlow()

    private fun set(delivery: Delivery) {
        deliveries[delivery.id] = delivery
        recompute(delivery.messageId)
    }

    private fun recompute(messageId: Long) {
        sms.setStatus(messageId, MessageStatusAggregator.aggregate(forMessageSync(messageId).map { it.status }))
    }

    private fun forMessageSync(messageId: Long) = deliveries.values.filter { it.messageId == messageId }
}

open class InMemoryLogRepository : DeliveryLogRepository {
    val attempts = mutableListOf<DeliveryAttempt>()
    override suspend fun record(attempt: DeliveryAttempt) { attempts += attempt.copy(id = attempts.size + 1L) }
    override fun paged(filter: LogFilter): Flow<PagingData<DeliveryAttempt>> = emptyFlow()
    override fun observeForMessage(messageId: Long): Flow<List<DeliveryAttempt>> = emptyFlow()
    override suspend fun deleteOlderThan(cutoffMillis: Long): Int {
        val before = attempts.size
        attempts.removeAll { it.startedAtMillis < cutoffMillis }
        return before - attempts.size
    }
    override suspend fun clear() = attempts.clear()
}
