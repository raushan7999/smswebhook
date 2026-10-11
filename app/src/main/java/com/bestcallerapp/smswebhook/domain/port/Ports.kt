package com.bestcallerapp.smswebhook.domain.port

import com.bestcallerapp.smswebhook.domain.model.DeliveryErrorType
import com.bestcallerapp.smswebhook.domain.model.HttpMethod
import com.bestcallerapp.smswebhook.domain.model.SmsMessage
import com.bestcallerapp.smswebhook.domain.model.Webhook

/** Everything needed to issue one webhook HTTP request. Header values are plaintext here. */
data class HttpRequestSpec(
    val method: HttpMethod,
    val url: String,
    val headers: List<Pair<String, String>>,
    val body: String?,
    val contentType: String?,
    val timeoutMillis: Long,
)

sealed interface HttpResult {
    val durationMillis: Long

    data class Response(val code: Int, val body: String?, override val durationMillis: Long) : HttpResult

    data class Failure(
        val type: DeliveryErrorType,
        val message: String,
        override val durationMillis: Long,
    ) : HttpResult
}

/** Performs webhook HTTP calls. Must never throw for network problems — return [HttpResult.Failure]. */
interface WebhookSender {
    suspend fun send(request: HttpRequestSpec): HttpResult
}

/** Schedules durable background work. Implemented with WorkManager. */
interface WorkScheduler {
    fun scheduleMessageProcessing(messageId: Long)

    /**
     * @param replaceExisting true for user-initiated retries of a finished delivery; false keeps
     *   an already queued/running work item (idempotent re-enqueue).
     */
    suspend fun scheduleDelivery(deliveryId: Long, retryDelaySeconds: Int, replaceExisting: Boolean = false)

    /** Re-applies network constraints (e.g. after "Only Wi-Fi" changed) to a queued delivery. */
    suspend fun refreshDeliveryConstraints(deliveryId: Long, retryDelaySeconds: Int)

    fun scheduleCleanupNow()
    fun ensurePeriodicMaintenance()
    fun scheduleReconcile()
}

sealed interface SmsDeletionResult {
    data object Deleted : SmsDeletionResult
    data object NotFound : SmsDeletionResult

    /** The platform does not allow this app to delete SMS (it is not the default SMS app). */
    data object NotPermitted : SmsDeletionResult
    data class Error(val reason: String) : SmsDeletionResult
}

/** Removes an SMS from the system SMS provider. Only possible for the default SMS app. */
interface SmsDeleter {
    fun isSupported(): Boolean
    suspend fun delete(message: SmsMessage): SmsDeletionResult
}

interface DeviceInfoProvider {
    /** Human-readable, non-identifying device description, e.g. "Google Pixel 8 (Android 15)". */
    fun description(): String
}

/** Controls whether the app reacts to incoming SMS at all. */
interface SmsListenerController {
    fun hasReceivePermission(): Boolean
    fun setListening(enabled: Boolean)
    fun isReceiverEnabled(): Boolean
}

/** Serialises webhook configurations for export/import. */
interface ConfigCodec {
    fun encode(webhooks: List<Webhook>, includeSecrets: Boolean, exportedAtMillis: Long): String

    /** @throws ConfigFormatException */
    fun decode(text: String): List<Webhook>
}

class ConfigFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

interface PowerStatusProvider {
    /** True when the app is exempt from battery optimisation (background work less likely deferred). */
    fun isIgnoringBatteryOptimizations(): Boolean
}
