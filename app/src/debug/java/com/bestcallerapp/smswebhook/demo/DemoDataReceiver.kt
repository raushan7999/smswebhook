package com.bestcallerapp.smswebhook.demo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.bestcallerapp.smswebhook.core.utils.ApplicationScope
import com.bestcallerapp.smswebhook.core.utils.TimeProvider
import com.bestcallerapp.smswebhook.data.local.database.AppDatabase
import com.bestcallerapp.smswebhook.data.local.entity.DeliveryAttemptEntity
import com.bestcallerapp.smswebhook.data.local.entity.DeliveryEntity
import com.bestcallerapp.smswebhook.data.local.entity.SmsMessageEntity
import com.bestcallerapp.smswebhook.data.repository.MessageStatusUpdater
import com.bestcallerapp.smswebhook.domain.engine.FilterEngine
import com.bestcallerapp.smswebhook.domain.engine.FilterSubject
import com.bestcallerapp.smswebhook.domain.engine.MessageFingerprinter
import com.bestcallerapp.smswebhook.domain.engine.PayloadBuilder
import com.bestcallerapp.smswebhook.domain.model.AppSettings
import com.bestcallerapp.smswebhook.domain.model.AttemptOutcome
import com.bestcallerapp.smswebhook.domain.model.DeliveryErrorType
import com.bestcallerapp.smswebhook.domain.model.DeliveryStatus
import com.bestcallerapp.smswebhook.domain.model.IncomingSms
import com.bestcallerapp.smswebhook.domain.model.MessageStatus
import com.bestcallerapp.smswebhook.domain.model.RetentionPolicy
import com.bestcallerapp.smswebhook.domain.model.SmsMessage
import com.bestcallerapp.smswebhook.domain.model.Webhook
import com.bestcallerapp.smswebhook.domain.model.WebhookHeader
import com.bestcallerapp.smswebhook.domain.model.WebhookScope
import com.bestcallerapp.smswebhook.domain.model.filter.FilterCondition
import com.bestcallerapp.smswebhook.domain.model.filter.FilterField
import com.bestcallerapp.smswebhook.domain.model.filter.FilterGroup
import com.bestcallerapp.smswebhook.domain.model.filter.FilterOperator
import com.bestcallerapp.smswebhook.domain.model.filter.LogicalOperator
import com.bestcallerapp.smswebhook.domain.model.payload.PayloadConfig
import com.bestcallerapp.smswebhook.domain.model.payload.PayloadField
import com.bestcallerapp.smswebhook.domain.model.payload.PayloadFieldKey
import com.bestcallerapp.smswebhook.domain.model.payload.PayloadFormat
import com.bestcallerapp.smswebhook.domain.model.payload.RegexExtractor
import com.bestcallerapp.smswebhook.domain.repository.SettingsRepository
import com.bestcallerapp.smswebhook.domain.repository.WebhookRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * DEBUG BUILDS ONLY. Replaces all app data with a fictional demo set for store screenshots and the website
 * tutorials (example.com endpoints, sample bank/OTP texts — never real messages):
 *
 *   adb shell am broadcast -a com.bestcallerapp.smswebhook.DEMO_SEED -p com.bestcallerapp.smswebhook.debug
 *
 * Deliveries are written in final states (sent / failed / filtered) so nothing is actually sent.
 */
class DemoDataReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun database(): AppDatabase
        fun webhooks(): WebhookRepository
        fun settings(): SettingsRepository
        fun filterEngine(): FilterEngine
        fun payloadBuilder(): PayloadBuilder
        fun fingerprinter(): MessageFingerprinter
        fun statusUpdater(): MessageStatusUpdater
        fun time(): TimeProvider

        @ApplicationScope
        fun scope(): CoroutineScope
    }

    override fun onReceive(context: Context, intent: Intent) {
        val deps = EntryPointAccessors.fromApplication(context.applicationContext, Deps::class.java)
        val pending = goAsync()
        deps.scope().launch {
            try {
                seed(deps)
                Log.i("SmsWebhook/Demo", "Demo data seeded")
            } catch (e: Exception) {
                Log.e("SmsWebhook/Demo", "Seeding failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun seed(deps: Deps) {
        val db = deps.database()
        db.clearAllTables()
        deps.settings().update { AppSettings(retention = RetentionPolicy.ByAge(30)) }

        val now = deps.time().nowMillis()
        val zone = deps.time().zone()
        val hooks = WEBHOOKS.map { webhook ->
            val id = deps.webhooks().save(webhook.copy(createdAtMillis = now - DAY * 9, updatedAtMillis = now - DAY * 2))
            webhook.copy(id = id)
        }
        val random = Random(42)
        val failures = mapOf(("CRM" to 3) to 503, ("CRM" to 9) to 401)

        MESSAGES.forEachIndexed { index, (sender, body, minutesAgo, slot) ->
            val received = now - minutesAgo * MINUTE
            val sms = IncomingSms(sender, body, received - 2_000, received, subscriptionId = 1 + slot, simSlot = slot, partCount = if (body.length > 160) 2 else 1)
            val fingerprint = deps.fingerprinter().fingerprint(sms)
            val messageId = db.smsMessageDao().insert(
                SmsMessageEntity(
                    fingerprint = fingerprint, sender = sender, body = body, sentAt = sms.sentAtMillis, receivedAt = received,
                    subscriptionId = sms.subscriptionId, simSlot = slot, partCount = sms.partCount,
                    status = MessageStatus.RECEIVED, createdAt = received,
                ),
            )
            val message = SmsMessage(
                messageId, fingerprint, sender, body, sms.sentAtMillis, received, sms.subscriptionId, slot,
                sms.partCount, MessageStatus.RECEIVED, received,
            )
            hooks.filter { it.enabled }.forEach { hook ->
                val matches = deps.filterEngine().shouldReceive(hook, FilterSubject.from(message), zone)
                val payload = if (matches) deps.payloadBuilder().build(hook.payload, message, zone) else null
                val failCode = failures[hook.name to index]
                val attempts = when {
                    !matches -> 0
                    failCode == 503 -> 3
                    else -> 1
                }
                val lastAt = received + 1_500
                val status = when {
                    !matches -> DeliveryStatus.FILTERED
                    failCode != null -> DeliveryStatus.FAILED
                    else -> DeliveryStatus.SENT
                }
                val deliveryId = db.deliveryDao().insertAll(
                    listOf(
                        DeliveryEntity(
                            messageId = messageId, webhookId = hook.id, status = status, attemptCount = attempts,
                            httpStatus = if (matches) failCode ?: 200 else null,
                            response = when {
                                !matches -> null
                                failCode == 401 -> """{"error":"invalid api key"}"""
                                failCode != null -> "Service Unavailable"
                                else -> """{"received":true,"id":"evt_${(100000 + index * 7919) % 999999}"}"""
                            },
                            errorType = when (failCode) {
                                401 -> DeliveryErrorType.HTTP_CLIENT_ERROR
                                503 -> DeliveryErrorType.HTTP_SERVER_ERROR
                                else -> null
                            },
                            error = when (failCode) {
                                401 -> "HTTP 401"
                                503 -> "HTTP 503 (retries exhausted)"
                                else -> null
                            },
                            payloadBody = payload?.body, contentType = payload?.contentType,
                            lastAttemptAt = if (matches) lastAt else null,
                            createdAt = received + 400, updatedAt = lastAt,
                        ),
                    ),
                ).single()
                repeat(attempts) { attempt ->
                    val code = failCode ?: 200
                    val last = attempt == attempts - 1
                    db.deliveryAttemptDao().insert(
                        DeliveryAttemptEntity(
                            deliveryId = deliveryId, messageId = messageId, webhookId = hook.id, webhookName = hook.name,
                            attemptNumber = attempt + 1, requestMethod = "POST", requestUrl = hook.url, httpStatus = code,
                            durationMs = 140L + random.nextLong(360),
                            outcome = when {
                                code in 200..299 -> AttemptOutcome.SUCCESS
                                code == 503 && !last -> AttemptOutcome.RETRYABLE_FAILURE
                                else -> AttemptOutcome.PERMANENT_FAILURE
                            },
                            errorType = if (code == 200) null else if (code == 401) DeliveryErrorType.HTTP_CLIENT_ERROR else DeliveryErrorType.HTTP_SERVER_ERROR,
                            error = if (code == 200) null else "HTTP $code",
                            response = if (code == 200) """{"received":true}""" else null,
                            startedAt = received + 900 + attempt * 30_000L,
                        ),
                    )
                }
            }
            deps.statusUpdater().recompute(messageId)
        }
    }

    private companion object {
        const val MINUTE = 60_000L
        const val DAY = 24 * 60 * MINUTE

        fun contains(field: FilterField, value: String) =
            FilterCondition(field = field, operator = FilterOperator.CONTAINS, value = value)

        val WEBHOOKS = listOf(
            Webhook(
                name = "Payment Server",
                url = "https://payments.example.com/webhooks/sms",
                timeoutSeconds = 15, maxRetries = 5, retryDelaySeconds = 30,
                headers = listOf(
                    WebhookHeader("Authorization", "Bearer demo_9f2c41b7e0", isSecret = true),
                    WebhookHeader("X-Source", "android-sms", isSecret = false),
                ),
                scope = WebhookScope.MATCHING_RULES,
                filter = FilterGroup(
                    operator = LogicalOperator.AND,
                    children = listOf(
                        FilterGroup(
                            operator = LogicalOperator.OR,
                            children = listOf(contains(FilterField.SENDER, "HDFC"), contains(FilterField.SENDER, "ICICI"), contains(FilterField.SENDER, "SBI")),
                        ),
                        FilterCondition(
                            field = FilterField.MESSAGE, operator = FilterOperator.CONTAINS_ANY_KEYWORD,
                            keywords = listOf("credited", "debited"),
                        ),
                    ),
                ),
                payload = PayloadConfig(
                    extractors = listOf(
                        RegexExtractor("amount", """(?:Rs\.?|INR)\s?([0-9,]+(?:\.\d{2})?)""", 1),
                        RegexExtractor("account", """A/c\s?(XX\d{4})""", 1),
                    ),
                ),
            ),
            Webhook(
                name = "CRM",
                url = "https://crm.example.com/api/v2/sms",
                timeoutSeconds = 20, maxRetries = 3, retryDelaySeconds = 60,
                headers = listOf(WebhookHeader("X-API-Key", "demo_crm_key_51c8", isSecret = true)),
                scope = WebhookScope.ALL_MESSAGES,
                payload = PayloadConfig(
                    fields = PayloadFieldKey.entries.map { key ->
                        when (key) {
                            PayloadFieldKey.ID -> PayloadField(key, "id", true)
                            PayloadFieldKey.SENDER -> PayloadField(key, "from", true)
                            PayloadFieldKey.MESSAGE -> PayloadField(key, "content", true)
                            PayloadFieldKey.RECEIVED_AT -> PayloadField(key, "receivedAt", true)
                            PayloadFieldKey.SIM_SLOT -> PayloadField(key, "sim", true)
                            else -> PayloadField(key, key.defaultName, false)
                        }
                    },
                ),
            ),
            Webhook(
                name = "OTP Relay",
                url = "https://auth.example.org/hooks/otp",
                timeoutSeconds = 10, maxRetries = 2, retryDelaySeconds = 10,
                scope = WebhookScope.MATCHING_RULES,
                filter = FilterGroup(
                    children = listOf(
                        FilterCondition(field = FilterField.MESSAGE, operator = FilterOperator.MATCHES_REGEX, value = """(?i)(otp|verification|code)"""),
                        FilterCondition(field = FilterField.MESSAGE, operator = FilterOperator.MATCHES_REGEX, value = """\b\d{6}\b"""),
                    ),
                ),
                payload = PayloadConfig(
                    format = PayloadFormat.JSON_TEMPLATE,
                    template = "{\n  \"code\": \"{{regex.otp}}\",\n  \"from\": \"{{sender}}\",\n  \"at\": \"{{received_at}}\"\n}",
                    extractors = listOf(RegexExtractor("otp", """\b(\d{6})\b""", 1)),
                ),
            ),
            Webhook(
                name = "Internal Server",
                url = "https://internal.example.net/sms/inbox",
                enabled = false,
                timeoutSeconds = 15, maxRetries = 5, retryDelaySeconds = 30,
                scope = WebhookScope.ALL_MESSAGES,
                payload = PayloadConfig(format = PayloadFormat.RAW_TEXT, template = PayloadConfig.DEFAULT_RAW_TEMPLATE),
            ),
        )

        /** sender, body, minutes ago, zero-based SIM slot. Fictional sample texts. */
        val MESSAGES = listOf(
            Quad("VM-HDFCBK", "Rs 5,000.00 credited to A/c XX1234 on 05-10-26 by UPI ref 627815394021. Avl Bal Rs 48,210.55 - HDFC Bank", 2, 0),
            Quad("AX-ICICIT", "482913 is your OTP for login. Valid for 10 minutes. Do not share it with anyone. - ICICI Bank", 6, 0),
            Quad("JD-AIRTEL", "Your 2GB/day data pack is active till 04-Nov-26. Recharge again with the Airtel Thanks app.", 19, 1),
            Quad("BZ-SBIUPI", "Dear Customer, INR 1,250.00 debited from A/c XX9087 to swiggy@icici. UPI Ref 628112907755 - SBI", 47, 0),
            Quad("VK-AMAZON", "Your package with 2 items will be delivered today by 8 PM. Track your order in the Amazon app.", 63, 1),
            Quad("VM-ICICIB", "INR 3,400.00 credited to A/c XX5521 on 05-Oct-26. Info: NEFT-ACME TRADERS. Avl Bal INR 21,064.20 - ICICI Bank", 95, 0),
            Quad("VK-PAYTMB", "739104 is your verification code for Paytm. Never share this code with anyone.", 142, 0),
            Quad("AD-SWIGGY", "Your order from Meghana Foods is out for delivery. Delivery partner Ravi will reach you in 15 mins.", 188, 1),
            Quad("VM-HDFCBK", "Rs 12,500.00 debited from A/c XX1234 on 05-10-26 towards Credit Card bill. Avl Bal Rs 43,210.55 - HDFC Bank", 263, 0),
            Quad("JM-BESCOM", "Electricity bill of Rs 1,846 for account 7741293 is due on 12-Oct-26. Pay online to avoid late fee.", 395, 1),
            Quad("VD-GOOGLE", "G-582014 is your Google verification code.", 540, 0),
            Quad("BP-IRCTCI", "PNR 4521873390, Train 12627, 08-Oct, SL S4 32. Chart not prepared. Happy journey!", 820, 1),
        )

        data class Quad(val sender: String, val body: String, val minutesAgo: Long, val slot: Int)
    }
}
