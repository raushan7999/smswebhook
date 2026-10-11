package com.bestcallerapp.smswebhook.fakes

import com.bestcallerapp.smswebhook.domain.model.IncomingSms
import com.bestcallerapp.smswebhook.domain.model.MessageStatus
import com.bestcallerapp.smswebhook.domain.model.SmsMessage
import com.bestcallerapp.smswebhook.domain.model.Webhook
import com.bestcallerapp.smswebhook.domain.model.WebhookScope
import com.bestcallerapp.smswebhook.domain.model.filter.FilterCondition
import com.bestcallerapp.smswebhook.domain.model.filter.FilterField
import com.bestcallerapp.smswebhook.domain.model.filter.FilterGroup
import com.bestcallerapp.smswebhook.domain.model.filter.FilterOperator
import com.bestcallerapp.smswebhook.domain.model.filter.LogicalOperator

/** 2026-10-05T20:30:00+05:30 */
const val SAMPLE_TIME = 1_791_212_400_000L

fun sms(
    sender: String = "VK-HDFCBK",
    body: String = "Your account XXXX1234 has been credited with Rs 5,000 on 05-Oct-26.",
    sentAt: Long = SAMPLE_TIME,
    receivedAt: Long = SAMPLE_TIME + 1_000,
    subscriptionId: Int? = 1,
    simSlot: Int? = 0,
) = IncomingSms(sender, body, sentAt, receivedAt, subscriptionId, simSlot, partCount = 1)

fun message(
    sender: String = "VK-HDFCBK",
    body: String = "Your account XXXX1234 has been credited with Rs 5,000 on 05-Oct-26.",
    simSlot: Int? = 0,
    receivedAt: Long = SAMPLE_TIME,
) = SmsMessage(
    id = 7, fingerprint = "abc123", sender = sender, body = body, sentAtMillis = SAMPLE_TIME,
    receivedAtMillis = receivedAt, subscriptionId = 3, simSlot = simSlot, partCount = 1,
    status = MessageStatus.RECEIVED, createdAtMillis = SAMPLE_TIME,
)

fun cond(
    field: FilterField,
    operator: FilterOperator,
    value: String = "",
    keywords: List<String> = emptyList(),
    caseSensitive: Boolean = false,
    negate: Boolean = false,
    secondValue: String = "",
) = FilterCondition(
    field = field, operator = operator, value = value, secondValue = secondValue, keywords = keywords,
    caseSensitive = caseSensitive, negate = negate,
)

fun and(vararg nodes: com.bestcallerapp.smswebhook.domain.model.filter.FilterNode) = FilterGroup(operator = LogicalOperator.AND, children = nodes.toList())
fun or(vararg nodes: com.bestcallerapp.smswebhook.domain.model.filter.FilterNode) = FilterGroup(operator = LogicalOperator.OR, children = nodes.toList())

fun webhook(
    name: String = "Payment Server",
    url: String = "https://example.com/sms",
    filter: FilterGroup? = null,
    maxRetries: Int = 3,
    enabled: Boolean = true,
) = Webhook(
    name = name,
    url = url,
    enabled = enabled,
    timeoutSeconds = 10,
    maxRetries = maxRetries,
    retryDelaySeconds = 10,
    scope = if (filter == null) WebhookScope.ALL_MESSAGES else WebhookScope.MATCHING_RULES,
    filter = filter ?: FilterGroup(),
)
