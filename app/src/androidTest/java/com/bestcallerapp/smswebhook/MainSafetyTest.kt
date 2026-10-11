package com.bestcallerapp.smswebhook

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bestcallerapp.smswebhook.data.remote.OkHttpWebhookSender
import com.bestcallerapp.smswebhook.di.NetworkModule
import com.bestcallerapp.smswebhook.domain.model.HttpMethod
import com.bestcallerapp.smswebhook.domain.port.HttpRequestSpec
import com.bestcallerapp.smswebhook.domain.port.HttpResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression: "Test webhook" is launched from viewModelScope (Main). Sending must not touch the
 * network on the main thread (StrictMode crashes the app with NetworkOnMainThreadException).
 */
@RunWith(AndroidJUnit4::class)
class MainSafetyTest {
    @Test fun sendIsMainSafe() {
        val server = MockWebServer().apply { start() }
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
        val sender = OkHttpWebhookSender(NetworkModule.provideOkHttpClient(), Dispatchers.IO)
        val result = runBlocking {
            withContext(Dispatchers.Main) {
                sender.send(HttpRequestSpec(HttpMethod.POST, server.url("/").toString(), emptyList(), "{}", "application/json", 5_000))
            }
        }
        assertEquals(200, (result as HttpResult.Response).code)
        assertEquals("ok", result.body)
        server.shutdown()
    }
}
