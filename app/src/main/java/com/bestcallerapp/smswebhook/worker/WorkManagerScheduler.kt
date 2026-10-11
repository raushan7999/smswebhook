package com.bestcallerapp.smswebhook.worker

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.bestcallerapp.smswebhook.core.constants.WebhookLimits
import com.bestcallerapp.smswebhook.core.constants.WorkNames
import com.bestcallerapp.smswebhook.core.utils.Hashing
import com.bestcallerapp.smswebhook.domain.model.IncomingSms
import com.bestcallerapp.smswebhook.domain.port.WorkScheduler
import com.bestcallerapp.smswebhook.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * All background work is unique per item, so re-enqueueing (reconcile, duplicate broadcasts)
 * never runs the same message or delivery concurrently.
 */
@Singleton
class WorkManagerScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val settingsRepository: SettingsRepository,
) : WorkScheduler {

    override fun scheduleMessageProcessing(messageId: Long) {
        val request = OneTimeWorkRequestBuilder<MessageProcessingWorker>()
            .setInputData(workDataOf(MessageProcessingWorker.KEY_MESSAGE_ID to messageId))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(WorkNames.TAG_PROCESSING)
            .build()
        workManager.enqueueUniqueWork(WorkNames.PROCESS_MESSAGE_PREFIX + messageId, ExistingWorkPolicy.KEEP, request)
    }

    override suspend fun scheduleDelivery(deliveryId: Long, retryDelaySeconds: Int, replaceExisting: Boolean) {
        val request = deliveryRequest(deliveryId, retryDelaySeconds, settingsRepository.current().wifiOnly).build()
        workManager.enqueueUniqueWork(
            WorkNames.DELIVERY_PREFIX + deliveryId,
            if (replaceExisting) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request,
        )
    }

    /** Applies the current network setting to already-queued work (takes effect for the next run if running). */
    override suspend fun refreshDeliveryConstraints(deliveryId: Long, retryDelaySeconds: Int) {
        val existing = workManager.getWorkInfosForUniqueWorkFlow(WorkNames.DELIVERY_PREFIX + deliveryId).first()
            .firstOrNull { !it.state.isFinished } ?: return
        val request = deliveryRequest(deliveryId, retryDelaySeconds, settingsRepository.current().wifiOnly)
            .setId(existing.id)
            .build()
        workManager.updateWork(request)
    }

    private fun deliveryRequest(deliveryId: Long, retryDelaySeconds: Int, wifiOnly: Boolean) =
        OneTimeWorkRequestBuilder<WebhookDeliveryWorker>()
            .setInputData(workDataOf(WebhookDeliveryWorker.KEY_DELIVERY_ID to deliveryId))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                retryDelaySeconds.coerceAtLeast(WebhookLimits.MIN_RETRY_DELAY_SECONDS).toLong(),
                TimeUnit.SECONDS,
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(WorkNames.TAG_DELIVERY)

    override fun scheduleCleanupNow() {
        workManager.enqueueUniqueWork(
            WorkNames.ONE_OFF_CLEANUP,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<CleanupWorker>().build(),
        )
    }

    override fun ensurePeriodicMaintenance() {
        val cleanup = PeriodicWorkRequestBuilder<CleanupWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        workManager.enqueueUniquePeriodicWork(WorkNames.PERIODIC_MAINTENANCE, ExistingPeriodicWorkPolicy.KEEP, cleanup)
    }

    override fun scheduleReconcile() {
        workManager.enqueueUniqueWork(
            WorkNames.RECONCILE,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<ReconcileWorker>().build(),
        )
    }
}

/** Used by the receiver when the database could not be written synchronously. */
@Singleton
class IngestFallbackScheduler @Inject constructor(
    private val workManager: WorkManager,
) {
    fun enqueue(sms: IncomingSms) {
        val key = Hashing.sha256Hex("${sms.sender}|${sms.sentAtMillis}|${sms.body}").take(KEY_LENGTH)
        val request = OneTimeWorkRequestBuilder<IngestSmsWorker>()
            .setInputData(IngestSmsWorker.toData(sms))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WebhookLimits.MIN_RETRY_DELAY_SECONDS.toLong(), TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(WorkNames.INGEST_FALLBACK_PREFIX + key, ExistingWorkPolicy.KEEP, request)
    }

    private companion object {
        const val KEY_LENGTH = 24
    }
}
