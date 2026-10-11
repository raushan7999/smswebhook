package com.bestcallerapp.smswebhook.data

import com.bestcallerapp.smswebhook.data.remote.OkHttpWebhookSender
import com.bestcallerapp.smswebhook.domain.model.DeliveryErrorType
import com.bestcallerapp.smswebhook.domain.model.HttpMethod
import com.bestcallerapp.smswebhook.domain.port.HttpRequestSpec
import com.bestcallerapp.smswebhook.domain.port.HttpResult
import com.bestcallerapp.smswebhook.core.network.UrlRedactor
import kotlinx.coroutines.test.runTest
import com.bestcallerapp.smswebhook.di.NetworkModule
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OkHttpWebhookSenderTest {
    private lateinit var server: MockWebServer
    private val sender = OkHttpWebhookSender(NetworkModule.provideOkHttpClient(), kotlinx.coroutines.Dispatchers.IO)

    @Before fun setUp() { server = MockWebServer().apply { start() } }

    @After fun tearDown() { server.shutdown() }

    private fun request(timeoutMs: Long = 2_000, headers: List<Pair<String, String>> = emptyList()) = HttpRequestSpec(
        HttpMethod.POST, server.url("/hook").toString(), headers, "{\"a\":1}", "application/json", timeoutMs,
    )

    @Test fun `returns status and body`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody("created"))
        val result = sender.send(request()) as HttpResult.Response
        assertEquals(201, result.code)
        assertEquals("created", result.body)
        assertEquals("{\"a\":1}", server.takeRequest().body.readUtf8())
    }

    @Test fun `timeout is classified`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val result = sender.send(request(timeoutMs = 300)) as HttpResult.Failure
        assertEquals(DeliveryErrorType.TIMEOUT, result.type)
    }

    @Test fun `redirects are not followed`() = runTest {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "https://elsewhere.example/"))
        assertEquals(302, (sender.send(request()) as HttpResult.Response).code)
        assertEquals(1, server.requestCount)
    }

    @Test fun `invalid header is a configuration error, not a crash`() = runTest {
        val result = sender.send(request(headers = listOf("Bad\nName" to "x"))) as HttpResult.Failure
        assertEquals(DeliveryErrorType.INVALID_CONFIGURATION, result.type)
    }

    @Test fun `url redaction hides query values and credentials`() {
        val redacted = UrlRedactor.redact("https://user:pw@example.com/p?token=abc&x=1")
        assertTrue(!redacted.contains("abc") && !redacted.contains("pw"))
        assertTrue(redacted.startsWith("https://example.com/p?token="))
    }
}
