package com.bestcallerapp.smswebhook.domain.usecase

import com.bestcallerapp.smswebhook.core.logging.AppLogger
import com.bestcallerapp.smswebhook.domain.model.DeleteSmsPolicy
import com.bestcallerapp.smswebhook.domain.model.DeliveryStatus
import com.bestcallerapp.smswebhook.domain.port.SmsDeleter
import com.bestcallerapp.smswebhook.domain.port.SmsDeletionResult
import com.bestcallerapp.smswebhook.domain.repository.DeliveryRepository
import com.bestcallerapp.smswebhook.domain.repository.SettingsRepository
import com.bestcallerapp.smswebhook.domain.repository.SmsRepository
import javax.inject.Inject

/**
 * Applies the "delete SMS after forwarding" setting. Deletion only happens after successful
 * delivery and only when the platform permits it (the app must be the default SMS app).
 */
class SmsDeletionCoordinator @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val deliveryRepository: DeliveryRepository,
    private val smsRepository: SmsRepository,
    private val deleter: SmsDeleter,
    private val logger: AppLogger,
) {
    suspend fun onDeliverySucceeded(messageId: Long) {
        val policy = settingsRepository.current().deleteSmsPolicy
        if (policy == DeleteSmsPolicy.DISABLED || !deleter.isSupported()) return
        val statuses = deliveryRepository.forMessage(messageId).map { it.status }
        if (!shouldDelete(policy, statuses)) return
        val message = smsRepository.getMessage(messageId) ?: return
        when (val result = deleter.delete(message)) {
            SmsDeletionResult.Deleted -> logger.i(TAG, "System SMS for message $messageId deleted")
            SmsDeletionResult.NotFound -> logger.i(TAG, "System SMS for message $messageId not found")
            SmsDeletionResult.NotPermitted -> logger.w(TAG, "SMS deletion not permitted on this device")
            is SmsDeletionResult.Error -> logger.w(TAG, "SMS deletion failed: ${result.reason}")
        }
    }

    companion object {
        fun shouldDelete(policy: DeleteSmsPolicy, statuses: List<DeliveryStatus>): Boolean {
            val relevant = statuses.filter { it != DeliveryStatus.FILTERED }
            return when (policy) {
                DeleteSmsPolicy.DISABLED -> false
                DeleteSmsPolicy.AFTER_FIRST_SUCCESS -> relevant.any { it == DeliveryStatus.SENT }
                DeleteSmsPolicy.AFTER_ALL_SUCCEEDED -> relevant.isNotEmpty() && relevant.all { it == DeliveryStatus.SENT }
            }
        }

        private const val TAG = "SmsDeletion"
    }
}
