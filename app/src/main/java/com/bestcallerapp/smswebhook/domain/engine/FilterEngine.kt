package com.bestcallerapp.smswebhook.domain.engine

import com.bestcallerapp.smswebhook.domain.model.SmsMessage
import com.bestcallerapp.smswebhook.domain.model.Webhook
import com.bestcallerapp.smswebhook.domain.model.WebhookScope
import com.bestcallerapp.smswebhook.domain.model.filter.FilterCondition
import com.bestcallerapp.smswebhook.domain.model.filter.FilterField
import com.bestcallerapp.smswebhook.domain.model.filter.FilterGroup
import com.bestcallerapp.smswebhook.domain.model.filter.FilterNode
import com.bestcallerapp.smswebhook.domain.model.filter.FilterOperator
import com.bestcallerapp.smswebhook.domain.model.filter.LogicalOperator
import com.bestcallerapp.smswebhook.domain.model.filter.ValueKind
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** The attributes of an SMS that filters can inspect. */
data class FilterSubject(
    val sender: String,
    val body: String,
    /** Zero-based SIM slot. */
    val simSlot: Int?,
    val subscriptionId: Int?,
    val receivedAtMillis: Long,
) {
    companion object {
        fun from(message: SmsMessage) = FilterSubject(
            sender = message.sender,
            body = message.body,
            simSlot = message.simSlot,
            subscriptionId = message.subscriptionId,
            receivedAtMillis = message.receivedAtMillis,
        )
    }
}

/**
 * Evaluates webhook filter trees. Pure and deterministic apart from the regex time budget:
 * a condition that cannot be evaluated (invalid regex, timeout, missing attribute, unparsable
 * number) evaluates to `false` before negation.
 */
@Singleton
class FilterEngine @Inject constructor(
    private val regex: RegexEvaluator,
) {

    fun shouldReceive(webhook: Webhook, subject: FilterSubject, zone: ZoneId): Boolean = when (webhook.scope) {
        WebhookScope.ALL_MESSAGES -> true
        WebhookScope.MATCHING_RULES -> evaluate(webhook.filter, subject, zone)
    }

    fun evaluate(node: FilterNode, subject: FilterSubject, zone: ZoneId): Boolean {
        val raw = when (node) {
            is FilterGroup -> evaluateGroup(node, subject, zone)
            is FilterCondition -> evaluateCondition(node, subject, zone)
        }
        return raw != node.negate
    }

    private fun evaluateGroup(group: FilterGroup, subject: FilterSubject, zone: ZoneId): Boolean {
        val active = group.children.filter { it.enabled }
        if (active.isEmpty()) return true
        return when (group.operator) {
            LogicalOperator.AND -> active.all { evaluate(it, subject, zone) }
            LogicalOperator.OR -> active.any { evaluate(it, subject, zone) }
        }
    }

    private fun evaluateCondition(condition: FilterCondition, subject: FilterSubject, zone: ZoneId): Boolean =
        when (condition.field.valueKind) {
            ValueKind.TEXT -> evaluateText(condition, textOf(condition.field, subject))
            ValueKind.NUMBER -> evaluateNumber(condition, numberOf(condition.field, subject))
            ValueKind.TIME_OF_DAY -> evaluateTime(condition, minuteOfDay(subject.receivedAtMillis, zone))
        }

    private fun textOf(field: FilterField, subject: FilterSubject): String = when (field) {
        FilterField.SENDER -> subject.sender
        else -> subject.body
    }

    private fun numberOf(field: FilterField, subject: FilterSubject): Long? = when (field) {
        FilterField.SIM_SLOT -> subject.simSlot?.let { it + 1L }
        FilterField.SUBSCRIPTION_ID -> subject.subscriptionId?.toLong()
        FilterField.MESSAGE_LENGTH -> subject.body.codePointCount(0, subject.body.length).toLong()
        else -> null
    }

    private fun evaluateText(condition: FilterCondition, actual: String): Boolean {
        val ignoreCase = !condition.caseSensitive
        val expected = condition.value
        return when (condition.operator) {
            FilterOperator.EQUALS -> actual.trim().equals(expected.trim(), ignoreCase)
            FilterOperator.NOT_EQUALS -> !actual.trim().equals(expected.trim(), ignoreCase)
            FilterOperator.CONTAINS -> expected.isNotEmpty() && actual.contains(expected, ignoreCase)
            FilterOperator.NOT_CONTAINS -> expected.isEmpty() || !actual.contains(expected, ignoreCase)
            FilterOperator.STARTS_WITH -> expected.isNotEmpty() && actual.startsWith(expected, ignoreCase)
            FilterOperator.ENDS_WITH -> expected.isNotEmpty() && actual.endsWith(expected, ignoreCase)
            FilterOperator.MATCHES_REGEX -> matchesRegex(expected, condition.caseSensitive, actual)
            FilterOperator.CONTAINS_ANY_KEYWORD ->
                condition.cleanKeywords().let { kw -> kw.isNotEmpty() && kw.any { actual.contains(it, ignoreCase) } }
            FilterOperator.CONTAINS_ALL_KEYWORDS ->
                condition.cleanKeywords().let { kw -> kw.isNotEmpty() && kw.all { actual.contains(it, ignoreCase) } }
            FilterOperator.GREATER_THAN, FilterOperator.LESS_THAN, FilterOperator.BETWEEN -> false
        }
    }

    private fun matchesRegex(pattern: String, caseSensitive: Boolean, actual: String): Boolean {
        val compiled = regex.compile(pattern, caseSensitive) as? RegexCompileResult.Valid ?: return false
        return try {
            regex.containsMatch(compiled.pattern, actual)
        } catch (_: RegexTimeoutException) {
            false
        }
    }

    private fun evaluateNumber(condition: FilterCondition, actual: Long?): Boolean {
        actual ?: return false
        val first = condition.value.trim().toLongOrNull() ?: return false
        return when (condition.operator) {
            FilterOperator.EQUALS -> actual == first
            FilterOperator.NOT_EQUALS -> actual != first
            FilterOperator.GREATER_THAN -> actual > first
            FilterOperator.LESS_THAN -> actual < first
            FilterOperator.BETWEEN -> {
                val second = condition.secondValue.trim().toLongOrNull() ?: return false
                actual in minOf(first, second)..maxOf(first, second)
            }
            else -> false
        }
    }

    private fun evaluateTime(condition: FilterCondition, actual: Int): Boolean {
        val first = TimeOfDay.parseMinutes(condition.value) ?: return false
        return when (condition.operator) {
            FilterOperator.GREATER_THAN -> actual >= first
            FilterOperator.LESS_THAN -> actual < first
            FilterOperator.BETWEEN -> {
                val second = TimeOfDay.parseMinutes(condition.secondValue) ?: return false
                // A window such as 22:00–06:00 wraps past midnight.
                if (first <= second) actual in first until second else actual >= first || actual < second
            }
            else -> false
        }
    }

    private fun minuteOfDay(epochMillis: Long, zone: ZoneId): Int {
        val time = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalTime()
        return time.hour * 60 + time.minute
    }

    private fun FilterCondition.cleanKeywords(): List<String> = keywords.map { it.trim() }.filter { it.isNotEmpty() }
}

object TimeOfDay {
    private val PATTERN = Regex("""^([01]?\d|2[0-3]):([0-5]\d)$""")

    /** Parses "HH:mm" into minutes since midnight. */
    fun parseMinutes(value: String): Int? {
        val match = PATTERN.matchEntire(value.trim()) ?: return null
        return match.groupValues[1].toInt() * 60 + match.groupValues[2].toInt()
    }
}
