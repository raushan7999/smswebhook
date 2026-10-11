package com.bestcallerapp.smswebhook.data.remote

import com.bestcallerapp.smswebhook.core.constants.AppConstants
import com.bestcallerapp.smswebhook.domain.model.DeliveryErrorType
import com.bestcallerapp.smswebhook.domain.port.HttpRequestSpec
import com.bestcallerapp.smswebhook.domain.port.HttpResult
import com.bestcallerapp.smswebhook.domain.port.WebhookSender
import com.bestcallerapp.smswebhook.core.utils.IoDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import kotlin.coroutines.resume

/**
 * Sends webhook requests with OkHttp. Retrofit is not used because every webhook has its own
 * user-supplied URL, method, headers and body — there is no fixed API surface to describe.
 *
 * TLS validation uses the platform defaults (plus network_security_config); it is never relaxed.
 * Redirects are not followed so a payload is never re-sent to a host the user did not configure.
 */
@Singleton
class OkHttpWebhookSender @Inject constructor(
    private val baseClient: OkHttpClient,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : WebhookSender {

    /** Main-safe: all network I/O, including reading the response body, runs on [ioDispatcher]. */
    override suspend fun send(request: HttpRequestSpec): HttpResult = withContext(ioDispatcher) { sendBlocking(request) }

    private suspend fun sendBlocking(request: HttpRequestSpec): HttpResult {
        val started = System.nanoTime()
        fun elapsed() = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)

        val url = request.url.toHttpUrlOrNull()
            ?: return HttpResult.Failure(DeliveryErrorType.INVALID_URL, "Invalid URL", 0)

        val okRequest = try {
            Request.Builder()
                .url(url)
                .apply { request.headers.forEach { (name, value) -> addHeader(name, value) } }
                .method(
                    request.method.name,
                    if (request.method.hasBody) {
                        request.body.orEmpty().toRequestBody(request.contentType?.toMediaTypeOrNull())
                    } else {
                        null
                    },
                )
                .build()
        } catch (e: IllegalArgumentException) {
            // OkHttp rejects malformed header names/values; never echo the value itself.
            return HttpResult.Failure(DeliveryErrorType.INVALID_CONFIGURATION, "Invalid request header", 0)
        }

        val client = baseClient.newBuilder()
            .callTimeout(request.timeoutMillis, TimeUnit.MILLISECONDS)
            .connectTimeout(request.timeoutMillis, TimeUnit.MILLISECONDS)
            .readTimeout(request.timeoutMillis, TimeUnit.MILLISECONDS)
            .writeTimeout(request.timeoutMillis, TimeUnit.MILLISECONDS)
            .build()

        return try {
            client.newCall(okRequest).await().use { response ->
                HttpResult.Response(response.code, readBodySafely(response), elapsed())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            HttpResult.Failure(classify(e), describe(e), elapsed())
        }
    }

    private fun readBodySafely(response: Response): String? = try {
        response.peekBody(AppConstants.MAX_RESPONSE_READ_BYTES).string()
    } catch (_: IOException) {
        null
    }

    private fun classify(e: IOException): DeliveryErrorType = when (e) {
        is UnknownHostException -> DeliveryErrorType.DNS
        is SocketTimeoutException -> DeliveryErrorType.TIMEOUT
        is InterruptedIOException -> DeliveryErrorType.TIMEOUT
        // Only certificate/identity problems are permanent; other TLS errors (handshake reset,
        // I/O during handshake) are common on mobile networks and are retried as connection errors.
        is SSLPeerUnverifiedException -> DeliveryErrorType.SSL
        is SSLHandshakeException -> if (e.hasCause<CertificateException>()) DeliveryErrorType.SSL else DeliveryErrorType.CONNECTION
        is SSLException -> DeliveryErrorType.CONNECTION
        is ConnectException, is NoRouteToHostException, is SocketException -> DeliveryErrorType.CONNECTION
        else -> DeliveryErrorType.UNKNOWN
    }

    private fun describe(e: IOException): String = when (classify(e)) {
        DeliveryErrorType.DNS -> "Could not resolve host (DNS failure or no internet)"
        DeliveryErrorType.TIMEOUT -> "Connection timeout"
        DeliveryErrorType.SSL -> "TLS/SSL error: the server certificate could not be verified"
        DeliveryErrorType.CONNECTION -> "Could not connect to server"
        else -> "Network error (${e.javaClass.simpleName})"
    }

    private inline fun <reified T : Throwable> Throwable.hasCause(): Boolean =
        generateSequence(cause) { it.cause }.take(MAX_CAUSE_DEPTH).any { it is T }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(
            object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response) { _, value, _ -> value.close() }
                }

                override fun onFailure(call: Call, e: IOException) {
                    if (!continuation.isCancelled) continuation.resumeWith(Result.failure(e))
                }
            },
        )
    }

    private companion object {
        const val MAX_CAUSE_DEPTH = 8
    }
}
