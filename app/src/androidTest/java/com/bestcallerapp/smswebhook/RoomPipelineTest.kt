package com.bestcallerapp.smswebhook

import android.content.Context
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bestcallerapp.smswebhook.core.logging.AppLogger
import com.bestcallerapp.smswebhook.core.security.KeystoreSecretCipher
import com.bestcallerapp.smswebhook.core.utils.TimeProvider
import com.bestcallerapp.smswebhook.data.local.database.AppDatabase
import com.bestcallerapp.smswebhook.data.remote.OkHttpWebhookSender
import com.bestcallerapp.smswebhook.data.repository.DeliveryLogRepositoryImpl
import com.bestcallerapp.smswebhook.data.repository.DeliveryRepositoryImpl
import com.bestcallerapp.smswebhook.data.repository.MessageStatusUpdater
import com.bestcallerapp.smswebhook.data.repository.SmsRepositoryImpl
import com.bestcallerapp.smswebhook.data.repository.WebhookRepositoryImpl
import com.bestcallerapp.smswebhook.di.NetworkModule
import com.bestcallerapp.smswebhook.domain.engine.DeliveryResultClassifier
import com.bestcallerapp.smswebhook.domain.engine.FilterEngine
import com.bestcallerapp.smswebhook.domain.engine.MessageFingerprinter
import com.bestcallerapp.smswebhook.domain.engine.PayloadBuilder
import com.bestcallerapp.smswebhook.domain.engine.RegexEvaluator
import com.bestcallerapp.smswebhook.domain.engine.RetryPolicy
import com.bestcallerapp.smswebhook.domain.engine.ValueExtractor
import com.bestcallerapp.smswebhook.domain.engine.WebhookRequestFactory
import com.bestcallerapp.smswebhook.domain.model.AppSettings
import com.bestcallerapp.smswebhook.domain.model.DeliveryStatus
import com.bestcallerapp.smswebhook.domain.model.IncomingSms
import com.bestcallerapp.smswebhook.domain.model.MessageStatus
import com.bestcallerapp.smswebhook.domain.model.RetentionPolicy
import com.bestcallerapp.smswebhook.domain.model.Webhook
import com.bestcallerapp.smswebhook.domain.model.WebhookHeader
import com.bestcallerapp.smswebhook.domain.model.WebhookScope
import com.bestcallerapp.smswebhook.domain.model.filter.FilterCondition
import com.bestcallerapp.smswebhook.domain.model.filter.FilterField
import com.bestcallerapp.smswebhook.domain.model.filter.FilterGroup
import com.bestcallerapp.smswebhook.domain.model.filter.FilterOperator
import com.bestcallerapp.smswebhook.domain.port.DeviceInfoProvider
import com.bestcallerapp.smswebhook.domain.port.SmsDeleter
import com.bestcallerapp.smswebhook.domain.port.SmsDeletionResult
import com.bestcallerapp.smswebhook.domain.port.WorkScheduler
import com.bestcallerapp.smswebhook.domain.repository.SettingsRepository
import com.bestcallerapp.smswebhook.domain.usecase.ApplyRetentionUseCase
import com.bestcallerapp.smswebhook.domain.usecase.DeliverWebhookUseCase
import com.bestcallerapp.smswebhook.domain.usecase.DeliveryRunResult
import com.bestcallerapp.smswebhook.domain.usecase.IngestResult
import com.bestcallerapp.smswebhook.domain.usecase.IngestSmsUseCase
import com.bestcallerapp.smswebhook.domain.usecase.ProcessMessageUseCase
import com.bestcallerapp.smswebhook.domain.usecase.SmsDeletionCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneId

/**
 * End-to-end pipeline against a real (in-memory) Room database, the real Keystore cipher and a
 * local HTTP server: SMS received → stored → matched → queued → webhook called → delivery recorded.
 */
@RunWith(AndroidJUnit4::class)
class RoomPipelineTest {

    private lateinit var db: AppDatabase
    private lateinit var server: MockWebServer
    private val queuedProcessing = mutableListOf<Long>()
    private val queuedDeliveries = mutableListOf<Long>()
    private val now = 1_791_212_400_000L

    private val time = object : TimeProvider {
        override fun nowMillis() = now
        override fun zone(): ZoneId = ZoneId.of("Asia/Kolkata")
    }
    private val logger = object : AppLogger {
        override fun d(tag: String, message: String) = Unit
        override fun i(tag: String, message: String) = Unit
        override fun w(tag: String, message: String, throwable: Throwable?) = Unit
        override fun e(tag: String, message: String, throwable: Throwable?) = Unit
    }
    private val scheduler = object : WorkScheduler {
        override fun scheduleMessageProcessing(messageId: Long) { queuedProcessing += messageId }
        override suspend fun scheduleDelivery(deliveryId: Long, retryDelaySeconds: Int, replaceExisting: Boolean) { queuedDeliveries += deliveryId }
        override suspend fun refreshDeliveryConstraints(deliveryId: Long, retryDelaySeconds: Int) = Unit
        override fun scheduleCleanupNow() = Unit
        override fun ensurePeriodicMaintenance() = Unit
        override fun scheduleReconcile() = Unit
    }
    private val settings = object : SettingsRepository {
        val state = MutableStateFlow(AppSettings())
        override val settings = state
        override suspend fun current() = state.value
        override suspend fun update(transform: (AppSettings) -> AppSettings) { state.value = transform(state.value) }
    }

    private lateinit var smsRepo: SmsRepositoryImpl
    private lateinit var webhookRepo: WebhookRepositoryImpl
    private lateinit var deliveryRepo: DeliveryRepositoryImpl
    private lateinit var ingest: IngestSmsUseCase
    private lateinit var process: ProcessMessageUseCase
    private lateinit var deliver: DeliverWebhookUseCase

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        server = MockWebServer().apply { start() }
        smsRepo = SmsRepositoryImpl(db.smsMessageDao())
        val statusUpdater = MessageStatusUpdater(db.deliveryDao(), db.smsMessageDao())
        webhookRepo = WebhookRepositoryImpl(db, db.webhookDao(), db.deliveryDao(), statusUpdater, KeystoreSecretCipher(), logger)
        deliveryRepo = DeliveryRepositoryImpl(db, db.deliveryDao(), db.smsMessageDao(), db.webhookDao(), statusUpdater)
        val logRepo = DeliveryLogRepositoryImpl(db.deliveryAttemptDao())
        val regex = RegexEvaluator()
        val device = object : DeviceInfoProvider { override fun description() = "Test" }
        val deleter = object : SmsDeleter {
            override fun isSupported() = false
            override suspend fun delete(message: com.bestcallerapp.smswebhook.domain.model.SmsMessage) = SmsDeletionResult.NotPermitted
        }
        ingest = IngestSmsUseCase(smsRepo, MessageFingerprinter(), scheduler, time, logger)
        process = ProcessMessageUseCase(
            smsRepo, webhookRepo, deliveryRepo, FilterEngine(regex), PayloadBuilder(ValueExtractor(regex), device), scheduler, time, logger,
        )
        deliver = DeliverWebhookUseCase(
            deliveryRepo, webhookRepo, logRepo, settings, OkHttpWebhookSender(NetworkModule.provideOkHttpClient(), kotlinx.coroutines.Dispatchers.IO),
            WebhookRequestFactory(), DeliveryResultClassifier(), RetryPolicy(),
            SmsDeletionCoordinator(settings, deliveryRepo, smsRepo, deleter, logger), time, logger,
        )
    }

    @After fun tearDown() {
        db.close()
        server.shutdown()
    }

    private fun sms(sender: String = "VK-HDFCBK", body: String = "Rs 500 credited to your account", sentAt: Long = now) =
        IncomingSms(sender, body, sentAt, now, subscriptionId = 1, simSlot = 0, partCount = 1)

    private fun hook(name: String, filter: FilterGroup? = null) = Webhook(
        name = name,
        url = server.url("/$name").toString(),
        timeoutSeconds = 5,
        maxRetries = 2,
        retryDelaySeconds = 10,
        scope = if (filter == null) WebhookScope.ALL_MESSAGES else WebhookScope.MATCHING_RULES,
        filter = filter ?: FilterGroup(),
        headers = listOf(WebhookHeader("Authorization", "Bearer secret-token", isSecret = true)),
    )

    private suspend fun drain() {
        while (queuedProcessing.isNotEmpty()) process(queuedProcessing.removeAt(0))
        while (queuedDeliveries.isNotEmpty()) deliver(queuedDeliveries.removeAt(0), 0)
    }

    @Test fun fullPipeline_matchedFilteredAndSent() = runTest {
        val filter = FilterGroup(
            children = listOf(
                FilterCondition(field = FilterField.SENDER, operator = FilterOperator.CONTAINS, value = "HDFC"),
                FilterCondition(field = FilterField.MESSAGE, operator = FilterOperator.CONTAINS, value = "credited"),
            ),
        )
        webhookRepo.save(hook("a", filter))
        webhookRepo.save(hook("b", FilterGroup(children = listOf(FilterCondition(field = FilterField.MESSAGE, operator = FilterOperator.CONTAINS, value = "OTP")))))
        webhookRepo.save(hook("c"))
        repeat(2) { server.enqueue(MockResponse().setResponseCode(200)) }

        val id = (ingest(sms()) as IngestResult.Stored).messageId
        assertEquals(MessageStatus.RECEIVED, smsRepo.getMessage(id)!!.status)
        drain()

        val byName = deliveryRepo.forMessage(id).associate { it.webhookName to it.status }
        assertEquals(mapOf("a" to DeliveryStatus.SENT, "b" to DeliveryStatus.FILTERED, "c" to DeliveryStatus.SENT), byName)
        assertEquals(MessageStatus.SENT, smsRepo.getMessage(id)!!.status)
        assertEquals("Bearer secret-token", server.takeRequest().getHeader("Authorization"))

        // Secret header is encrypted at rest.
        val stored = db.webhookDao().getAllWithConfig().first().headers.single().value
        assertTrue(stored.startsWith("enc:v1:"))

        // Duplicate broadcast and re-processing are no-ops.
        assertEquals(IngestResult.Duplicate, ingest(sms()))
        process(id)
        assertEquals(3, deliveryRepo.forMessage(id).size)
        assertEquals(2, server.requestCount)

        val stats = deliveryRepo.observeDashboardStats().first()
        assertEquals(1, stats.totalMessages)
        assertEquals(2, stats.sentDeliveries)
        assertEquals(3, stats.enabledWebhooks)
    }

    @Test fun retryKeepsMessageActiveUntilDelivered() = runTest {
        webhookRepo.save(hook("a"))
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(200))
        val id = (ingest(sms()) as IngestResult.Stored).messageId
        process(queuedProcessing.removeAt(0))
        val deliveryId = queuedDeliveries.single()

        assertEquals(DeliveryRunResult.RETRY, deliver(deliveryId, 0))
        assertEquals(MessageStatus.RETRYING, smsRepo.getMessage(id)!!.status)
        assertEquals(DeliveryRunResult.DONE, deliver(deliveryId, 1))
        assertEquals(MessageStatus.SENT, smsRepo.getMessage(id)!!.status)
        assertEquals(2, deliveryRepo.getDelivery(deliveryId)!!.attemptCount)
    }

    @Test fun retentionByCountAndAgeSparesActiveMessages() = runTest {
        repeat(10) { i -> ingest(sms(body = "m$i", sentAt = i.toLong())) }
        queuedProcessing.toList().forEach { process(it) } // no webhooks → FILTERED (inactive)
        queuedProcessing.clear()
        webhookRepo.save(hook("a"))
        val activeId = (ingest(sms(body = "active")) as IngestResult.Stored).messageId
        process(queuedProcessing.removeAt(0)) // PENDING (active)

        settings.update { it.copy(retention = RetentionPolicy.ByCount(3)) }
        ApplyRetentionUseCase(settings, smsRepo, DeliveryLogRepositoryImpl(db.deliveryAttemptDao()), time, logger)()
        assertEquals(3, smsRepo.observeCount().first())
        assertTrue(smsRepo.getMessage(activeId) != null)

        settings.update { it.copy(retention = RetentionPolicy.ByAge(1)) }
        val future = object : TimeProvider by time { override fun nowMillis() = now + 2 * 86_400_000L }
        ApplyRetentionUseCase(settings, smsRepo, DeliveryLogRepositoryImpl(db.deliveryAttemptDao()), future, logger)()
        assertEquals(1, smsRepo.observeCount().first())
        assertTrue("active message and its delivery survive", deliveryRepo.forMessage(activeId).isNotEmpty())
    }

    @Test fun pagedSearchEscapesWildcards() = runTest {
        ingest(sms(body = "100% cashback"))
        ingest(sms(body = "nothing here", sentAt = 2))
        val source = db.smsMessageDao().pagingSource(MessageStatus.entries.map { it.name }, "%100\\%%", 160)
        val page = source.load(PagingSource.LoadParams.Refresh(null, 20, false)) as PagingSource.LoadResult.Page
        assertEquals(1, page.data.size)
    }

    @Test fun deletingWebhookCascades() = runTest {
        val webhookId = webhookRepo.save(hook("a"))
        ingest(sms())
        process(queuedProcessing.removeAt(0))
        val messageId = smsRepo.unprocessedMessageIds().firstOrNull() ?: db.smsMessageDao().idsWithStatus(MessageStatus.PENDING.name).single()
        webhookRepo.delete(webhookId)
        assertNull(webhookRepo.getWebhook(webhookId))
        // Status is recomputed: the message must not stay "pending" forever.
        assertEquals(MessageStatus.FILTERED, smsRepo.getMessage(messageId)!!.status)
        assertEquals(DeliveryRunResult.DONE, deliver(queuedDeliveries.single(), 0))
        assertEquals(0, server.requestCount)
    }
}
