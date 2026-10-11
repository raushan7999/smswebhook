package com.bestcallerapp.smswebhook.presentation.components

import com.bestcallerapp.smswebhook.domain.model.filter.FilterCondition
import com.bestcallerapp.smswebhook.domain.model.filter.FilterField
import com.bestcallerapp.smswebhook.domain.model.filter.FilterGroup
import com.bestcallerapp.smswebhook.domain.model.filter.FilterNode
import com.bestcallerapp.smswebhook.domain.model.filter.FilterOperator
import com.bestcallerapp.smswebhook.domain.model.filter.LogicalOperator
import com.bestcallerapp.smswebhook.domain.model.filter.ValueKind
import com.bestcallerapp.smswebhook.domain.model.payload.PayloadFieldKey
import com.bestcallerapp.smswebhook.domain.model.payload.PayloadFormat

fun FilterField.label(): String = when (this) {
    FilterField.SENDER -> "Sender"
    FilterField.MESSAGE -> "Message"
    FilterField.SIM_SLOT -> "SIM slot"
    FilterField.SUBSCRIPTION_ID -> "Subscription ID"
    FilterField.MESSAGE_LENGTH -> "Message length"
    FilterField.RECEIVED_TIME -> "Time received"
}

fun FilterOperator.label(kind: ValueKind): String = when (this) {
    FilterOperator.EQUALS -> if (kind == ValueKind.TEXT) "Equals" else "="
    FilterOperator.NOT_EQUALS -> if (kind == ValueKind.TEXT) "Does not equal" else "≠"
    FilterOperator.CONTAINS -> "Contains"
    FilterOperator.NOT_CONTAINS -> "Does not contain"
    FilterOperator.STARTS_WITH -> "Starts with"
    FilterOperator.ENDS_WITH -> "Ends with"
    FilterOperator.MATCHES_REGEX -> "Matches regex"
    FilterOperator.CONTAINS_ANY_KEYWORD -> "Contains any keyword"
    FilterOperator.CONTAINS_ALL_KEYWORDS -> "Contains all keywords"
    FilterOperator.GREATER_THAN -> if (kind == ValueKind.TIME_OF_DAY) "After" else ">"
    FilterOperator.LESS_THAN -> if (kind == ValueKind.TIME_OF_DAY) "Before" else "<"
    FilterOperator.BETWEEN -> "Between"
}

fun LogicalOperator.label(): String = when (this) {
    LogicalOperator.AND -> "All conditions (AND)"
    LogicalOperator.OR -> "Any condition (OR)"
}

fun PayloadFormat.label(): String = when (this) {
    PayloadFormat.JSON -> "JSON"
    PayloadFormat.JSON_TEMPLATE -> "Custom JSON template"
    PayloadFormat.RAW_TEXT -> "Raw text"
    PayloadFormat.FORM_URLENCODED -> "Form data"
}

fun PayloadFieldKey.label(): String = when (this) {
    PayloadFieldKey.ID -> "Message ID (stable fingerprint)"
    PayloadFieldKey.SENDER -> "Sender"
    PayloadFieldKey.MESSAGE -> "Message"
    PayloadFieldKey.TIMESTAMP -> "Timestamp (sent)"
    PayloadFieldKey.RECEIVED_AT -> "Received at"
    PayloadFieldKey.SIM_SLOT -> "SIM slot"
    PayloadFieldKey.SUBSCRIPTION_ID -> "Subscription ID"
    PayloadFieldKey.DEVICE -> "Device model"
}

/** Human-readable one-line description of a filter tree, e.g. `Sender contains "HDFC" AND …`. */
fun describeFilter(node: FilterNode, root: Boolean = true): String {
    val text = when (node) {
        is FilterCondition -> describeCondition(node)
        is FilterGroup -> {
            val parts = node.children.filter { it.enabled }.map { describeFilter(it, root = false) }
            when {
                parts.isEmpty() -> "everything"
                parts.size == 1 -> parts.single()
                else -> parts.joinToString(" ${node.operator.name} ").let { if (root) it else "($it)" }
            }
        }
    }
    return if (node.negate) "NOT ($text)" else text
}

private fun describeCondition(condition: FilterCondition): String {
    val kind = condition.field.valueKind
    val op = condition.operator.label(kind).lowercase()
    val value = when {
        condition.operator.usesKeywords -> condition.keywords.filter { it.isNotBlank() }.joinToString(", ") { "\"$it\"" }
        condition.operator.usesSecondValue -> "${condition.value} and ${condition.secondValue}"
        kind == ValueKind.TEXT -> "\"${condition.value}\""
        else -> condition.value
    }
    val case = if (kind == ValueKind.TEXT && condition.caseSensitive) " (case-sensitive)" else ""
    return "${condition.field.label()} $op $value$case"
}
