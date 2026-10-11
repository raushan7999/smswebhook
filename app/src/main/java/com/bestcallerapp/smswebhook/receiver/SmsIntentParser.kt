package com.bestcallerapp.smswebhook.receiver

import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SubscriptionManager
import com.bestcallerapp.smswebhook.domain.model.IncomingSms

/**
 * Converts an SMS_RECEIVED broadcast into [IncomingSms] values. A single broadcast carries all
 * PDUs of a multipart message; parts are grouped by originating address and concatenated in order.
 */
object SmsIntentParser {

    fun parse(intent: Intent, receivedAtMillis: Long): List<IncomingSms> {
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)?.filterNotNull().orEmpty()
        if (parts.isEmpty()) return emptyList()

        val subscriptionId = subscriptionIdOf(intent)
        val simSlot = simSlotOf(intent, subscriptionId)

        return parts
            .groupBy { it.displayOriginatingAddress ?: it.originatingAddress.orEmpty() }
            .map { (sender, messageParts) ->
                IncomingSms(
                    sender = sender.trim(),
                    body = messageParts.joinToString(separator = "") { it.displayMessageBody ?: it.messageBody.orEmpty() },
                    sentAtMillis = messageParts.first().timestampMillis,
                    receivedAtMillis = receivedAtMillis,
                    subscriptionId = subscriptionId,
                    simSlot = simSlot,
                    partCount = messageParts.size,
                )
            }
    }

    private fun subscriptionIdOf(intent: Intent): Int? {
        val id = intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, INVALID)
            .takeIf { it != INVALID }
            ?: intent.getIntExtra(LEGACY_SUBSCRIPTION_EXTRA, INVALID)
        return id.takeIf { it >= 0 }
    }

    private fun simSlotOf(intent: Intent, subscriptionId: Int?): Int? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            intent.getIntExtra(SubscriptionManager.EXTRA_SLOT_INDEX, INVALID).takeIf { it >= 0 }?.let { return it }
        }
        LEGACY_SLOT_EXTRAS.forEach { key ->
            intent.getIntExtra(key, INVALID).takeIf { it >= 0 }?.let { return it }
        }
        if (subscriptionId != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return SubscriptionManager.getSlotIndex(subscriptionId).takeIf { it >= 0 }
        }
        return null
    }

    private const val INVALID = -1
    private const val LEGACY_SUBSCRIPTION_EXTRA = "subscription"
    private val LEGACY_SLOT_EXTRAS = listOf("slot", "simSlot", "slot_id")
}
