package com.bestcallerapp.smswebhook.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.room.withTransaction
import com.bestcallerapp.smswebhook.data.local.dao.DeliveryDao
import com.bestcallerapp.smswebhook.data.local.dao.SmsMessageDao
import com.bestcallerapp.smswebhook.data.local.dao.WebhookDao
import com.bestcallerapp.smswebhook.data.local.database.AppDatabase
import com.bestcallerapp.smswebhook.data.local.entity.DeliveryEntity
import com.bestcallerapp.smswebhook.domain.model.DashboardStats
import com.bestcallerapp.smswebhook.domain.model.Delivery
import com.bestcallerapp.smswebhook.domain.model.DeliveryErrorType
import com.bestcallerapp.smswebhook.domain.model.DeliveryStatus
import com.bestcallerapp.smswebhook.domain.model.NewDelivery
import com.bestcallerapp.smswebhook.domain.repository.DeliveryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Every state change of a delivery and the derived message status are written in one
 * transaction so the history screen never shows a status that disagrees with its deliveries.
 */
@Singleton
class DeliveryRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val deliveryDao: DeliveryDao,
    private val messageDao: SmsMessageDao,
    private val webhookDao: WebhookDao,
    private val statusUpdater: MessageStatusUpdater,
) : DeliveryRepository {

    override suspend fun createDeliveries(messageId: Long, deliveries: List<NewDelivery>, nowMillis: Long): List<Long> =
        database.withTransaction {
            val entities = deliveries.map {
                DeliveryEntity(
                    messageId = messageId,
                    webhookId = it.webhookId,
                    status = it.status,
                    payloadBody = it.payloadBody,
                    contentType = it.contentType,
                    createdAt = nowMillis,
                    updatedAt = nowMillis,
                )
            }
            val ids = if (entities.isEmpty()) emptyList() else deliveryDao.insertAll(entities)
            recomputeMessageStatus(messageId)
            ids.zip(entities).filter { (id, entity) -> id > 0 && entity.status == DeliveryStatus.PENDING }.map { it.first }
        }

    override suspend fun getDelivery(id: Long): Delivery? = deliveryDao.getById(id)?.toDomain()

    override fun observeForMessage(messageId: Long): Flow<List<Delivery>> =
        deliveryDao.observeForMessage(messageId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun forMessage(messageId: Long): List<Delivery> =
        deliveryDao.forMessage(messageId).map { it.toDomain() }

    override fun pagedForWebhook(webhookId: Long, failedOnly: Boolean): Flow<PagingData<Delivery>> {
        return Pager(PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false)) {
            if (failedOnly) {
                deliveryDao.pagingForWebhook(webhookId, listOf(DeliveryStatus.FAILED.name))
            } else {
                deliveryDao.pagingAllForWebhook(webhookId)
            }
        }.flow.map { data -> data.map { it.toDomain() } }
    }

    override suspend fun markProcessing(id: Long, nowMillis: Long): Boolean = transitionAndRecompute(id) {
        deliveryDao.claim(id, DeliveryStatus.PROCESSING.name, CLAIMABLE, nowMillis) > 0
    }

    override suspend fun markSent(id: Long, httpStatus: Int?, response: String?, nowMillis: Long) {
        transitionAndRecompute(id) {
            deliveryDao.updateResult(id, DeliveryStatus.SENT.name, httpStatus, response, null, null, null, nowMillis)
            true
        }
    }

    override suspend fun markRetrying(
        id: Long,
        httpStatus: Int?,
        response: String?,
        errorType: DeliveryErrorType?,
        errorMessage: String?,
        nextAttemptAtMillis: Long,
        nowMillis: Long,
    ) {
        transitionAndRecompute(id) {
            deliveryDao.updateResult(
                id, DeliveryStatus.RETRYING.name, httpStatus, response, errorType?.name, errorMessage,
                nextAttemptAtMillis, nowMillis,
            )
            true
        }
    }

    override suspend fun markFailed(
        id: Long,
        httpStatus: Int?,
        response: String?,
        errorType: DeliveryErrorType?,
        errorMessage: String?,
        nowMillis: Long,
    ) {
        transitionAndRecompute(id) {
            deliveryDao.updateResult(
                id, DeliveryStatus.FAILED.name, httpStatus, response, errorType?.name, errorMessage, null, nowMillis,
            )
            true
        }
    }

    override suspend fun resetFailedForRetry(id: Long, nowMillis: Long): Boolean = transitionAndRecompute(id) {
        deliveryDao.resetFailed(id, DeliveryStatus.PENDING.name, DeliveryStatus.FAILED.name, nowMillis) > 0
    }

    override suspend fun failedIdsForWebhook(webhookId: Long): List<Long> =
        deliveryDao.idsForWebhookWithStatus(webhookId, DeliveryStatus.FAILED.name)

    override suspend fun activeDeliveryIds(): List<Long> = deliveryDao.idsWithStatuses(ACTIVE)

    override fun observeDashboardStats(): Flow<DashboardStats> = combine(
        messageDao.observeCount(),
        deliveryDao.observeCountWithStatuses(ACTIVE),
        deliveryDao.observeCountWithStatuses(listOf(DeliveryStatus.SENT.name)),
        deliveryDao.observeCountWithStatuses(listOf(DeliveryStatus.FAILED.name)),
        combine(webhookDao.observeEnabledCount(), webhookDao.observeTotalCount()) { enabled, total -> enabled to total },
    ) { messages, pending, sent, failed, (enabledWebhooks, totalWebhooks) ->
        DashboardStats(messages, pending, sent, failed, enabledWebhooks, totalWebhooks)
    }

    private suspend fun transitionAndRecompute(id: Long, change: suspend () -> Boolean): Boolean =
        database.withTransaction {
            val changed = change()
            if (changed) deliveryDao.messageIdOf(id)?.let { recomputeMessageStatus(it) }
            changed
        }

    private suspend fun recomputeMessageStatus(messageId: Long) = statusUpdater.recompute(messageId)

    private companion object {
        const val PAGE_SIZE = 50
        val ACTIVE = DeliveryStatus.entries.filter { it.isActive }.map { it.name }
        val CLAIMABLE = ACTIVE
    }
}
