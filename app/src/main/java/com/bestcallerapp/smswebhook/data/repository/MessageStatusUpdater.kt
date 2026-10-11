package com.bestcallerapp.smswebhook.data.repository

import com.bestcallerapp.smswebhook.data.local.dao.DeliveryDao
import com.bestcallerapp.smswebhook.data.local.dao.SmsMessageDao
import com.bestcallerapp.smswebhook.domain.engine.MessageStatusAggregator
import com.bestcallerapp.smswebhook.domain.model.DeliveryStatus
import javax.inject.Inject
import javax.inject.Singleton

/** Recomputes a message's derived status from its deliveries. Call inside the same transaction as the change. */
@Singleton
class MessageStatusUpdater @Inject constructor(
    private val deliveryDao: DeliveryDao,
    private val messageDao: SmsMessageDao,
) {
    suspend fun recompute(messageId: Long) {
        val statuses = deliveryDao.statusesForMessage(messageId).mapNotNull { name ->
            DeliveryStatus.entries.firstOrNull { it.name == name }
        }
        messageDao.updateStatus(messageId, MessageStatusAggregator.aggregate(statuses).name)
    }
}
