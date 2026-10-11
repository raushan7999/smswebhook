package com.bestcallerapp.smswebhook.pipeline

import com.bestcallerapp.smswebhook.domain.model.DeliveryErrorType
import com.bestcallerapp.smswebhook.domain.model.DeliveryStatus
import com.bestcallerapp.smswebhook.domain.model.MessageStatus
import com.bestcallerapp.smswebhook.domain.model.RetentionPolicy
import com.bestcallerapp.smswebhook.domain.model.WebhookHeader
import com.bestcallerapp.smswebhook.domain.model.filter.FilterField
import com.bestcallerapp.smswebhook.domain.model.filter.FilterOperator
import com.bestcallerapp.smswebhook.domain.port.HttpResult
import com.bestcallerapp.smswebhook.domain.usecase.DeliveryRunResult
import com.bestcallerapp.smswebhook.domain.usecase.IngestResult
import com.bestcallerapp.smswebhook.fakes.ScriptedSender
import com.bestcallerapp.smswebhook.fakes.and
import com.bestcallerapp.smswebhook.fakes.cond
import com.bestcallerapp.smswebhook.fakes.sms
import com.bestcallerapp.smswebhook.fakes.webhook
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class PipelineIntegrationTest {

    private lateinit var server: MockWebServer

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After fun tearDown() {
        server.shutdown()
    }

    private fun url(path: String = "/sms") = server.url(path).toString()

    private val hdfcCredited = and(
        cond(FilterField.SENDER, FilterOperator.CONTAINS, "HDFC"),
        cond(FilterField.MESSAGE, FilterOperator.CONTAINS, "credited"),
    )

    @Test fun `acceptance - stored, matched, delivered, marked SENT and never duplicated`() = runTest {
        val h = PipelineHarness()
        val id = h.webhooks.save(
            webhook(url = url(), filter = hdfcCredited).copy(headers = listOf(WebhookHeader("Authorization", "Bearer t0ken", true))),
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))

        val stored = h.receive(sms()) as IngestResult.Stored
        assertEquals(MessageStatus.PENDING, h.sms.messages[stored.messageId]!!.status)
        assertEquals(mapOf(1L to DeliveryRunResult.DONE), h.runDeliveriesOnce())

        val request = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("POST", request.method)
        assertEquals("Bearer t0ken", request.getHeader("Authorization"))
        assertTrue(request.getHeader("Content-Type")!!.startsWith("application/json"))
        assertTrue(request.getHeader("X-Idempotency-Key")!!.isNotBlank())
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("VK-HDFCBK", body["sender"]!!.jsonPrimitive.content)
        assertEquals("2026-10-05T20:30:00+05:30", body["timestamp"]!!.jsonPrimitive.content)

        val delivery = h.deliveries.deliveries.values.single()
        assertEquals(id, delivery.webhookId)
        assertEquals(DeliveryStatus.SENT, delivery.status)
        assertEquals(200, delivery.httpStatus)
        assertEquals(MessageStatus.SENT, h.sms.messages[stored.messageId]!!.status)

        // Android re-delivers the same broadcast: ignored by fingerprint.
        assertEquals(IngestResult.Duplicate, h.receive(sms(receivedAt = 123_456)))
        // A stray re-run of the delivery work must not send again.
        assertEquals(DeliveryRunResult.DONE, h.deliver(delivery.id, 0))
        assertEquals(1, server.requestCount)
        assertEquals(1, h.deliveries.deliveries.size)

        // Secrets never reach the attempt log.
        assertFalse(h.logs.attempts.toString().contains("t0ken"))
    }

    @Test fun `no matching webhook marks the message FILTERED`() = runTest {
        val h = PipelineHarness()
        h.webhooks.save(webhook(url = url(), filter = hdfcCredited))
        val stored = h.receive(sms(sender = "AMAZON", body = "Your order shipped")) as IngestResult.Stored
        assertEquals(MessageStatus.FILTERED, h.sms.messages[stored.messageId]!!.status)
        assertEquals(DeliveryStatus.FILTERED, h.deliveries.deliveries.values.single().status)
        assertTrue(h.scheduler.deliveries.isEmpty())
        assertEquals(0, server.requestCount)
    }

    @Test fun `no webhooks at all still stores the message`() = runTest {
        val h = PipelineHarness()
        val stored = h.receive(sms()) as IngestResult.Stored
        assertEquals(MessageStatus.FILTERED, h.sms.messages[stored.messageId]!!.status)
    }

    @Test fun `webhook unavailable - retried and then delivered`() = runTest {
        val h = PipelineHarness()
        h.webhooks.save(webhook(url = url(), filter = hdfcCredited))
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(200))

        val stored = h.receive(sms()) as IngestResult.Stored
        assertEquals(DeliveryRunResult.RETRY, h.runDeliveriesOnce().values.single())
        assertEquals(MessageStatus.RETRYING, h.sms.messages[stored.messageId]!!.status)
        assertEquals(DeliveryStatus.RETRYING, h.deliveries.deliveries.values.single().status)
        assertEquals(DeliveryRunResult.RETRY, h.runDeliveriesOnce().values.single())
        assertEquals(DeliveryRunResult.DONE, h.runDeliveriesOnce().values.single())

        val delivery = h.deliveries.deliveries.values.single()
        assertEquals(DeliveryStatus.SENT, delivery.status)
        assertEquals(3, delivery.attemptCount)
        assertEquals(MessageStatus.SENT, h.sms.messages[stored.messageId]!!.status)
        assertEquals(3, h.logs.attempts.size)
    }

    @Test fun `offline - pending, then delivered when the network returns`() = runTest {
        val sender = ScriptedSender(
            HttpResult.Failure(DeliveryErrorType.DNS, "Could not resolve host", 3),
            HttpResult.Failure(DeliveryErrorType.CONNECTION, "No route", 3),
            HttpResult.Response(200, "ok", 40),
        )
        val h = PipelineHarness(sender = sender)
        h.webhooks.save(webhook(url = "https://hooks.example.com/sms"))
        val stored = h.receive(sms()) as IngestResult.Stored
        assertTrue(h.sms.messages[stored.messageId]!!.status.isActive)
        h.runDeliveriesOnce()
        h.runDeliveriesOnce()
        assertEquals(DeliveryRunResult.DONE, h.runDeliveriesOnce().values.single())
        assertEquals(MessageStatus.SENT, h.sms.messages[stored.messageId]!!.status)
    }

    @Test fun `multiple webhooks - A sent, B filtered, C sent`() = runTest {
        val h = PipelineHarness()
        h.webhooks.save(webhook(name = "A", url = url("/a"), filter = hdfcCredited))
        h.webhooks.save(webhook(name = "B", url = url("/b"), filter = and(cond(FilterField.MESSAGE, FilterOperator.CONTAINS, "OTP"))))
        h.webhooks.save(webhook(name = "C", url = url("/c")))
        repeat(2) { server.enqueue(MockResponse().setResponseCode(200)) }

        val stored = h.receive(sms()) as IngestResult.Stored
        h.runDeliveriesOnce()

        val byName = h.deliveries.deliveries.values.associateBy { it.webhookName }
        assertEquals(DeliveryStatus.SENT, byName.getValue("A").status)
        assertEquals(DeliveryStatus.FILTERED, byName.getValue("B").status)
        assertEquals(DeliveryStatus.SENT, byName.getValue("C").status)
        assertEquals(MessageStatus.SENT, h.sms.messages[stored.messageId]!!.status)
        assertEquals(setOf("/a", "/c"), List(2) { server.takeRequest().path }.toSet())
    }

    @Test fun `permanent failure is not retried but can be retried manually`() = runTest {
        val h = PipelineHarness()
        h.webhooks.save(webhook(url = url()))
        server.enqueue(MockResponse().setResponseCode(400).setBody("bad request"))
        server.enqueue(MockResponse().setResponseCode(200))

        val stored = h.receive(sms()) as IngestResult.Stored
        assertEquals(DeliveryRunResult.FAILED, h.runDeliveriesOnce().values.single())
        val failed = h.deliveries.deliveries.values.single()
        assertEquals(DeliveryStatus.FAILED, failed.status)
        assertEquals(400, failed.httpStatus)
        assertEquals(MessageStatus.FAILED, h.sms.messages[stored.messageId]!!.status)
        assertTrue(h.scheduler.deliveries.isEmpty())

        assertTrue(h.retry.retry(failed.id))
        assertEquals(DeliveryRunResult.DONE, h.runDeliveriesOnce().values.single())
        assertEquals(MessageStatus.SENT, h.sms.messages[stored.messageId]!!.status)
        assertFalse("sent deliveries cannot be retried", h.retry.retry(failed.id))
    }

    @Test fun `max retries reached marks FAILED`() = runTest {
        val h = PipelineHarness(sender = ScriptedSender(*Array(5) { HttpResult.Response(500, null, 1) }))
        h.webhooks.save(webhook(maxRetries = 2))
        h.receive(sms())
        assertEquals(DeliveryRunResult.RETRY, h.runDeliveriesOnce().values.single())
        assertEquals(DeliveryRunResult.RETRY, h.runDeliveriesOnce().values.single())
        assertEquals(DeliveryRunResult.FAILED, h.runDeliveriesOnce().values.single())
        val delivery = h.deliveries.deliveries.values.single()
        assertEquals(DeliveryStatus.FAILED, delivery.status)
        assertEquals(3, delivery.attemptCount)
        assertEquals(DeliveryErrorType.HTTP_SERVER_ERROR, delivery.errorType)
    }

    @Test fun `disabled webhook before delivery fails without sending`() = runTest {
        val sender = ScriptedSender()
        val h = PipelineHarness(sender = sender)
        val id = h.webhooks.save(webhook())
        h.receive(sms())
        h.webhooks.setEnabled(id, false, 0)
        assertEquals(DeliveryRunResult.FAILED, h.runDeliveriesOnce().values.single())
        assertTrue(sender.requests.isEmpty())
    }

    @Test fun `retention keeps active messages and trims by count and age`() = runTest {
        val h = PipelineHarness(sender = ScriptedSender())
        repeat(5) { i -> h.receive(sms(body = "message $i", sentAt = i.toLong(), receivedAt = h.time.now - (5 - i) * 86_400_000L)) }
        // One active message (pending delivery) that must survive.
        h.webhooks.save(webhook())
        val active = (h.receive(sms(body = "active", receivedAt = 0)) as IngestResult.Stored).messageId

        h.settingsRepository.update { it.copy(retention = RetentionPolicy.ByCount(3)) }
        h.retention()
        assertTrue(active in h.sms.messages)
        assertEquals(3, h.sms.messages.size)

        h.settingsRepository.update { it.copy(retention = RetentionPolicy.ByAge(2)) }
        h.retention()
        assertTrue(active in h.sms.messages)
        assertTrue(h.sms.messages.values.all { it.id == active || it.receivedAtMillis >= h.time.now - 2 * 86_400_000L })
    }

    @Test fun `reprocessing an already processed message re-queues its active deliveries`() = runTest {
        val h = PipelineHarness(sender = ScriptedSender())
        h.webhooks.save(webhook())
        val id = (h.receive(sms()) as IngestResult.Stored).messageId
        h.scheduler.deliveries.clear() // simulate: deliveries committed but enqueue was lost
        h.process(id)
        assertEquals(1, h.scheduler.deliveries.size)
    }

    @Test fun `a failing attempt log never causes a successful send to be repeated`() = runTest {
        val sender = ScriptedSender(HttpResult.Response(200, "ok", 1))
        val h = PipelineHarness(sender = sender, logs = object : com.bestcallerapp.smswebhook.fakes.InMemoryLogRepository() {
            override suspend fun record(attempt: com.bestcallerapp.smswebhook.domain.model.DeliveryAttempt) = throw IllegalStateException("db")
        })
        h.webhooks.save(webhook())
        h.receive(sms())
        assertEquals(DeliveryRunResult.DONE, h.runDeliveriesOnce().values.single())
        assertEquals(DeliveryStatus.SENT, h.deliveries.deliveries.values.single().status)
        assertEquals(1, sender.requests.size)
    }
}
