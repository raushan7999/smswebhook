package com.bestcallerapp.smswebhook.domain.usecase

import com.bestcallerapp.smswebhook.core.logging.AppLogger
import com.bestcallerapp.smswebhook.core.utils.TimeProvider
import com.bestcallerapp.smswebhook.domain.model.RetentionPolicy
import com.bestcallerapp.smswebhook.domain.port.WorkScheduler
import com.bestcallerapp.smswebhook.domain.repository.DeliveryLogRepository
import com.bestcallerapp.smswebhook.domain.repository.DeliveryRepository
import com.bestcallerapp.smswebhook.domain.repository.SettingsRepository
import com.bestcallerapp.smswebhook.domain.repository.SmsRepository
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class CleanupReport(val messagesDeleted: Int, val logsDeleted: Int)

/**
 * Enforces the retention settings. Messages with active deliveries are never removed; their
 * delivery records and logs are removed with them through foreign-key cascades.
 */
class ApplyRetentionUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val smsRepository: SmsRepository,
    private val logRepository: DeliveryLogRepository,
    private val time: TimeProvider,
    private val logger: AppLogger,
) {
    suspend operator fun invoke(): CleanupReport {
        val settings = settingsRepository.current()
        val now = time.nowMillis()
        val messagesDeleted = when (val retention = settings.retention) {
            RetentionPolicy.Forever -> 0
            is RetentionPolicy.ByAge -> smsRepository.deleteInactiveOlderThan(now - TimeUnit.DAYS.toMillis(retention.days.toLong()))
            is RetentionPolicy.ByCount -> smsRepository.deleteInactiveBeyondNewest(retention.maxMessages)
        }
        val logsDeleted = if (settings.keepDeliveryLogs) {
            logRepository.deleteOlderThan(now - TimeUnit.DAYS.toMillis(settings.logRetentionDays.toLong()))
        } else {
            logRepository.deleteOlderThan(Long.MAX_VALUE)
        }
        logger.i(TAG, "Retention cleanup removed $messagesDeleted message(s), $logsDeleted log entr(ies)")
        return CleanupReport(messagesDeleted, logsDeleted)
    }

    private companion object {
        const val TAG = "Retention"
    }
}

/**
 * Re-enqueues work that may have been lost (e.g. the process died between persisting a message
 * and enqueuing its work). Enqueueing is idempotent, so running this often is harmless.
 */
class ReconcileQueueUseCase @Inject constructor(
    private val smsRepository: SmsRepository,
    private val deliveryRepository: DeliveryRepository,
    private val scheduler: WorkScheduler,
    private val logger: AppLogger,
) {
    suspend operator fun invoke() {
        val unprocessed = smsRepository.unprocessedMessageIds()
        unprocessed.forEach(scheduler::scheduleMessageProcessing)
        val active = deliveryRepository.activeDeliveryIds()
        active.forEach { scheduler.scheduleDelivery(it, DEFAULT_DELAY) }
        if (unprocessed.isNotEmpty() || active.isNotEmpty()) {
            logger.i(TAG, "Reconciled ${unprocessed.size} message(s) and ${active.size} deliver(ies)")
        }
    }

    private companion object {
        const val TAG = "Reconcile"
        const val DEFAULT_DELAY = 30
    }
}
