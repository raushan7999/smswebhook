package com.bestcallerapp.smswebhook.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.bestcallerapp.smswebhook.core.logging.AppLogger
import com.bestcallerapp.smswebhook.domain.model.IncomingSms
import com.bestcallerapp.smswebhook.domain.usecase.ApplyRetentionUseCase
import com.bestcallerapp.smswebhook.domain.usecase.DeliverWebhookUseCase
import com.bestcallerapp.smswebhook.domain.usecase.DeliveryRunResult
import com.bestcallerapp.smswebhook.domain.usecase.IngestSmsUseCase
import com.bestcallerapp.smswebhook.domain.usecase.ProcessMessageUseCase
import com.bestcallerapp.smswebhook.domain.usecase.ReconcileQueueUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/** Evaluates a stored message against webhook rules and queues deliveries. */
@HiltWorker
class MessageProcessingWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val processMessage: ProcessMessageUseCase,
    private val logger: AppLogger,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val messageId = inputData.getLong(KEY_MESSAGE_ID, -1)
        if (messageId <= 0) return Result.failure()
        return try {
            processMessage(messageId)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e(TAG, "Processing message $messageId failed", e)
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = WorkerNotifications.foregroundInfo(applicationContext)

    companion object {
        const val KEY_MESSAGE_ID = "message_id"
        private const val TAG = "ProcessWorker"
        private const val MAX_ATTEMPTS = 5
    }
}

/**
 * Delivers one message to one webhook. Retries use WorkManager's persisted exponential backoff,
 * so they survive process death and reboots, and wait for network connectivity.
 */
@HiltWorker
class WebhookDeliveryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val deliverWebhook: DeliverWebhookUseCase,
    private val logger: AppLogger,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val deliveryId = inputData.getLong(KEY_DELIVERY_ID, -1)
        if (deliveryId <= 0) return Result.failure()
        return try {
            when (deliverWebhook(deliveryId, runAttemptCount)) {
                DeliveryRunResult.DONE -> Result.success()
                DeliveryRunResult.RETRY -> Result.retry()
                DeliveryRunResult.FAILED -> Result.failure()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Unexpected (e.g. database) error: the delivery stays active and is retried.
            logger.e(TAG, "Delivery $deliveryId crashed", e)
            if (runAttemptCount < MAX_UNEXPECTED_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = WorkerNotifications.foregroundInfo(applicationContext)

    companion object {
        const val KEY_DELIVERY_ID = "delivery_id"
        private const val TAG = "DeliveryWorker"
        private const val MAX_UNEXPECTED_ATTEMPTS = 10
    }
}

/** Applies history and log retention settings. */
@HiltWorker
class CleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val applyRetention: ApplyRetentionUseCase,
    private val logger: AppLogger,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        applyRetention()
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logger.e(TAG, "Cleanup failed", e)
        Result.retry()
    }

    private companion object {
        const val TAG = "CleanupWorker"
    }
}

/** Re-enqueues work for messages/deliveries whose work item may have been lost. */
@HiltWorker
class ReconcileWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val reconcile: ReconcileQueueUseCase,
    private val logger: AppLogger,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        reconcile()
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logger.e(TAG, "Reconcile failed", e)
        Result.retry()
    }

    private companion object {
        const val TAG = "ReconcileWorker"
    }
}

/** Fallback when the receiver could not write to the database directly. */
@HiltWorker
class IngestSmsWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val ingestSms: IngestSmsUseCase,
    private val logger: AppLogger,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sms = fromData() ?: return Result.failure()
        return try {
            ingestSms(sms)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e(TAG, "Deferred ingest failed", e)
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = WorkerNotifications.foregroundInfo(applicationContext)

    private fun fromData(): IncomingSms? {
        val sender = inputData.getString(KEY_SENDER) ?: return null
        val body = inputData.getString(KEY_BODY) ?: return null
        return IncomingSms(
            sender = sender,
            body = body,
            sentAtMillis = inputData.getLong(KEY_SENT_AT, 0),
            receivedAtMillis = inputData.getLong(KEY_RECEIVED_AT, 0),
            subscriptionId = inputData.getInt(KEY_SUBSCRIPTION, -1).takeIf { it >= 0 },
            simSlot = inputData.getInt(KEY_SLOT, -1).takeIf { it >= 0 },
            partCount = inputData.getInt(KEY_PARTS, 1),
        )
    }

    companion object {
        private const val TAG = "IngestWorker"
        private const val MAX_ATTEMPTS = 10
        private const val KEY_SENDER = "sender"
        private const val KEY_BODY = "body"
        private const val KEY_SENT_AT = "sent_at"
        private const val KEY_RECEIVED_AT = "received_at"
        private const val KEY_SUBSCRIPTION = "subscription_id"
        private const val KEY_SLOT = "sim_slot"
        private const val KEY_PARTS = "parts"

        fun toData(sms: IncomingSms) = workDataOf(
            KEY_SENDER to sms.sender,
            KEY_BODY to sms.body,
            KEY_SENT_AT to sms.sentAtMillis,
            KEY_RECEIVED_AT to sms.receivedAtMillis,
            KEY_SUBSCRIPTION to (sms.subscriptionId ?: -1),
            KEY_SLOT to (sms.simSlot ?: -1),
            KEY_PARTS to sms.partCount,
        )
    }
}
