package com.bestcallerapp.smswebhook.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.bestcallerapp.smswebhook.core.logging.AppLogger
import com.bestcallerapp.smswebhook.core.utils.ApplicationScope
import com.bestcallerapp.smswebhook.domain.usecase.IngestSmsUseCase
import com.bestcallerapp.smswebhook.worker.IngestFallbackScheduler
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Entry point for incoming SMS. Does the minimum possible: parse, persist (deduplicated) and
 * enqueue background processing. No network access happens here.
 */
class SmsReceiver : BroadcastReceiver() {

    /** Dependencies are pulled from the Hilt graph via an entry point (receivers are short-lived). */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun ingestSms(): IngestSmsUseCase
        fun fallback(): IngestFallbackScheduler
        fun logger(): AppLogger

        @ApplicationScope
        fun appScope(): CoroutineScope
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val deps = EntryPointAccessors.fromApplication(context.applicationContext, Dependencies::class.java)
        val logger = deps.logger()
        val ingestSms = deps.ingestSms()
        val fallback = deps.fallback()

        val messages = try {
            SmsIntentParser.parse(intent, System.currentTimeMillis())
        } catch (e: Exception) {
            logger.e(TAG, "Could not parse SMS broadcast", e)
            return
        }
        if (messages.isEmpty()) return

        val pendingResult = goAsync()
        deps.appScope().launch {
            try {
                messages.forEach { sms ->
                    val stored = withTimeoutOrNull(INGEST_TIMEOUT_MS) {
                        runCatching { ingestSms(sms) }
                            .onFailure { logger.e(TAG, "Direct ingest failed; deferring to WorkManager", it) }
                            .isSuccess
                    } ?: false
                    if (!stored) fallback.enqueue(sms)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "SmsReceiver"

        /** goAsync() allows ~10 s; leave headroom for the fallback enqueue. */
        const val INGEST_TIMEOUT_MS = 6_000L
    }
}

