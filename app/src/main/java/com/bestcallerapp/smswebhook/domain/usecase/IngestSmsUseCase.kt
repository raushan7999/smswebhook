package com.bestcallerapp.smswebhook.domain.usecase

import com.bestcallerapp.smswebhook.core.logging.AppLogger
import com.bestcallerapp.smswebhook.core.utils.TimeProvider
import com.bestcallerapp.smswebhook.domain.engine.MessageFingerprinter
import com.bestcallerapp.smswebhook.domain.model.IncomingSms
import com.bestcallerapp.smswebhook.domain.port.WorkScheduler
import com.bestcallerapp.smswebhook.domain.repository.SmsRepository
import javax.inject.Inject

sealed interface IngestResult {
    data class Stored(val messageId: Long) : IngestResult
    data object Duplicate : IngestResult
}

/**
 * First step of the pipeline: persist the SMS (deduplicated by fingerprint) and hand off to
 * background processing. Intentionally fast so it can run inside a BroadcastReceiver.
 */
class IngestSmsUseCase @Inject constructor(
    private val smsRepository: SmsRepository,
    private val fingerprinter: MessageFingerprinter,
    private val scheduler: WorkScheduler,
    private val time: TimeProvider,
    private val logger: AppLogger,
) {
    suspend operator fun invoke(sms: IncomingSms): IngestResult {
        val fingerprint = fingerprinter.fingerprint(sms)
        val id = smsRepository.insertIfAbsent(sms, fingerprint, time.nowMillis())
        if (id == null) {
            logger.i(TAG, "Duplicate SMS broadcast ignored")
            // If the original copy was stored but its processing was never queued (process died),
            // queue it now. Unique work makes this a no-op when it is already queued.
            smsRepository.unprocessedIdForFingerprint(fingerprint)?.let(scheduler::scheduleMessageProcessing)
            return IngestResult.Duplicate
        }
        logger.i(TAG, "Stored message $id (${sms.partCount} part(s))")
        scheduler.scheduleMessageProcessing(id)
        return IngestResult.Stored(id)
    }

    private companion object {
        const val TAG = "Ingest"
    }
}
