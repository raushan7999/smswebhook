package com.bestcallerapp.smswebhook.pipeline

import com.bestcallerapp.smswebhook.data.remote.OkHttpWebhookSender
import com.bestcallerapp.smswebhook.domain.engine.DeliveryResultClassifier
import com.bestcallerapp.smswebhook.domain.engine.FilterEngine
import com.bestcallerapp.smswebhook.domain.engine.MessageFingerprinter
import com.bestcallerapp.smswebhook.domain.engine.PayloadBuilder
import com.bestcallerapp.smswebhook.domain.engine.RegexEvaluator
import com.bestcallerapp.smswebhook.domain.engine.RetryPolicy
import com.bestcallerapp.smswebhook.domain.engine.ValueExtractor
import com.bestcallerapp.smswebhook.domain.engine.WebhookRequestFactory
import com.bestcallerapp.smswebhook.domain.model.AppSettings
import com.bestcallerapp.smswebhook.domain.model.IncomingSms
import com.bestcallerapp.smswebhook.domain.port.WebhookSender
import com.bestcallerapp.smswebhook.domain.usecase.ApplyRetentionUseCase
import com.bestcallerapp.smswebhook.domain.usecase.DeliverWebhookUseCase
import com.bestcallerapp.smswebhook.domain.usecase.DeliveryRunResult
import com.bestcallerapp.smswebhook.domain.usecase.IngestResult
import com.bestcallerapp.smswebhook.domain.usecase.IngestSmsUseCase
import com.bestcallerapp.smswebhook.domain.usecase.ProcessMessageUseCase
import com.bestcallerapp.smswebhook.domain.usecase.RetryDeliveryUseCase
import com.bestcallerapp.smswebhook.domain.usecase.SmsDeletionCoordinator
import com.bestcallerapp.smswebhook.fakes.FakeDeviceInfo
import com.bestcallerapp.smswebhook.fakes.FakeSettingsRepository
import com.bestcallerapp.smswebhook.fakes.FakeSmsDeleter
import com.bestcallerapp.smswebhook.fakes.FakeTime
import com.bestcallerapp.smswebhook.fakes.InMemoryDeliveryRepository
import com.bestcallerapp.smswebhook.fakes.InMemoryLogRepository
import com.bestcallerapp.smswebhook.fakes.InMemorySmsRepository
import com.bestcallerapp.smswebhook.fakes.InMemoryWebhookRepository
import com.bestcallerapp.smswebhook.fakes.NoopLogger
import com.bestcallerapp.smswebhook.fakes.RecordingScheduler
import com.bestcallerapp.smswebhook.di.NetworkModule

/**
 * Wires the real domain pipeline (ingest → process → deliver) to in-memory storage and a
 * deterministic "WorkManager" that the test drives explicitly, mirroring worker semantics:
 * RETRY re-runs the delivery with an incremented run attempt, DONE/FAILED finish the work.
 */
class PipelineHarness(
    sender: WebhookSender = OkHttpWebhookSender(NetworkModule.provideOkHttpClient(), kotlinx.coroutines.Dispatchers.IO),
    settings: AppSettings = AppSettings(),
    val logs: InMemoryLogRepository = InMemoryLogRepository(),
) {
    val time = FakeTime()
    val sms = InMemorySmsRepository()
    val webhooks = InMemoryWebhookRepository()
    val deliveries = InMemoryDeliveryRepository(sms, webhooks)
    val settingsRepository = FakeSettingsRepository(settings)
    val scheduler = RecordingScheduler()
    private val regex = RegexEvaluator()
    private val deleter = FakeSmsDeleter()

    val ingest = IngestSmsUseCase(sms, MessageFingerprinter(), scheduler, time, NoopLogger)
    val process = ProcessMessageUseCase(
        sms, webhooks, deliveries, FilterEngine(regex), PayloadBuilder(ValueExtractor(regex), FakeDeviceInfo), scheduler, time, NoopLogger,
    )
    val deliver = DeliverWebhookUseCase(
        deliveries, webhooks, logs, settingsRepository, sender, WebhookRequestFactory(), DeliveryResultClassifier(),
        RetryPolicy(), SmsDeletionCoordinator(settingsRepository, deliveries, sms, deleter, NoopLogger), time, NoopLogger,
    )
    val retry = RetryDeliveryUseCase(deliveries, webhooks, scheduler, time)
    val retention = ApplyRetentionUseCase(settingsRepository, sms, logs, time, NoopLogger)

    private val runAttempts = mutableMapOf<Long, Int>()

    /** Simulates the receiver + MessageProcessingWorker. */
    suspend fun receive(incoming: IncomingSms): IngestResult {
        val result = ingest(incoming)
        drainProcessing()
        return result
    }

    suspend fun drainProcessing() {
        while (scheduler.processing.isNotEmpty()) process(scheduler.processing.removeAt(0))
    }

    /** Runs one WorkManager execution of each queued delivery; RETRY results stay queued. */
    suspend fun runDeliveriesOnce(): Map<Long, DeliveryRunResult> {
        val queued = scheduler.deliveries.distinct()
        scheduler.deliveries.clear()
        return queued.associateWith { id ->
            val attempt = runAttempts[id] ?: 0
            val result = deliver(id, attempt)
            if (result == DeliveryRunResult.RETRY) {
                runAttempts[id] = attempt + 1
                scheduler.deliveries += id
            } else {
                runAttempts.remove(id)
            }
            result
        }
    }
}
