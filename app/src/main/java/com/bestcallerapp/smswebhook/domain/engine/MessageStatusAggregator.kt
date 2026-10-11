package com.bestcallerapp.smswebhook.domain.engine

import com.bestcallerapp.smswebhook.domain.model.DeliveryStatus
import com.bestcallerapp.smswebhook.domain.model.MessageStatus

/** Derives a message's overall status from its per-webhook deliveries. */
object MessageStatusAggregator {
    fun aggregate(deliveries: Collection<DeliveryStatus>): MessageStatus {
        val relevant = deliveries.filter { it != DeliveryStatus.FILTERED }
        return when {
            relevant.isEmpty() -> MessageStatus.FILTERED
            DeliveryStatus.PROCESSING in relevant -> MessageStatus.PROCESSING
            DeliveryStatus.RETRYING in relevant -> MessageStatus.RETRYING
            DeliveryStatus.PENDING in relevant -> MessageStatus.PENDING
            DeliveryStatus.FAILED in relevant -> MessageStatus.FAILED
            else -> MessageStatus.SENT
        }
    }
}
