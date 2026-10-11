package com.bestcallerapp.smswebhook.domain.model.filter

import java.util.UUID

enum class LogicalOperator { AND, OR }

enum class ValueKind { TEXT, NUMBER, TIME_OF_DAY }

enum class FilterField(val valueKind: ValueKind) {
    SENDER(ValueKind.TEXT),
    MESSAGE(ValueKind.TEXT),

    /** One-based SIM slot ("SIM 1", "SIM 2"). */
    SIM_SLOT(ValueKind.NUMBER),
    SUBSCRIPTION_ID(ValueKind.NUMBER),
    MESSAGE_LENGTH(ValueKind.NUMBER),

    /** Local time of day the SMS was received, compared as HH:mm. */
    RECEIVED_TIME(ValueKind.TIME_OF_DAY),
    ;

    val operators: List<FilterOperator>
        get() = FilterOperator.entries.filter { valueKind in it.kinds }
}

enum class FilterOperator(val kinds: Set<ValueKind>, val usesSecondValue: Boolean = false) {
    EQUALS(setOf(ValueKind.TEXT, ValueKind.NUMBER)),
    NOT_EQUALS(setOf(ValueKind.TEXT, ValueKind.NUMBER)),
    CONTAINS(setOf(ValueKind.TEXT)),
    NOT_CONTAINS(setOf(ValueKind.TEXT)),
    STARTS_WITH(setOf(ValueKind.TEXT)),
    ENDS_WITH(setOf(ValueKind.TEXT)),
    MATCHES_REGEX(setOf(ValueKind.TEXT)),
    CONTAINS_ANY_KEYWORD(setOf(ValueKind.TEXT)),
    CONTAINS_ALL_KEYWORDS(setOf(ValueKind.TEXT)),
    GREATER_THAN(setOf(ValueKind.NUMBER, ValueKind.TIME_OF_DAY)),
    LESS_THAN(setOf(ValueKind.NUMBER, ValueKind.TIME_OF_DAY)),
    BETWEEN(setOf(ValueKind.NUMBER, ValueKind.TIME_OF_DAY), usesSecondValue = true),
    ;

    val usesKeywords: Boolean get() = this == CONTAINS_ANY_KEYWORD || this == CONTAINS_ALL_KEYWORDS
}

/**
 * A node in a webhook's filter tree. Groups combine children with AND/OR; any node can be
 * negated (NOT). Disabled nodes are ignored during evaluation. An empty group matches
 * everything, i.e. it imposes no constraint.
 */
sealed interface FilterNode {
    val id: String
    val negate: Boolean
    val enabled: Boolean
}

data class FilterGroup(
    override val id: String = newNodeId(),
    val operator: LogicalOperator = LogicalOperator.AND,
    val children: List<FilterNode> = emptyList(),
    override val negate: Boolean = false,
    override val enabled: Boolean = true,
) : FilterNode

data class FilterCondition(
    override val id: String = newNodeId(),
    val field: FilterField = FilterField.SENDER,
    val operator: FilterOperator = FilterOperator.CONTAINS,
    val value: String = "",
    /** Upper bound for [FilterOperator.BETWEEN]. */
    val secondValue: String = "",
    val keywords: List<String> = emptyList(),
    val caseSensitive: Boolean = false,
    override val negate: Boolean = false,
    override val enabled: Boolean = true,
) : FilterNode

fun newNodeId(): String = UUID.randomUUID().toString()

/** Depth-first sequence of every condition in the tree (including disabled ones). */
fun FilterGroup.allConditions(): Sequence<FilterCondition> = sequence {
    children.forEach { child ->
        when (child) {
            is FilterCondition -> yield(child)
            is FilterGroup -> yieldAll(child.allConditions())
        }
    }
}

fun FilterGroup.depth(): Int =
    1 + (children.filterIsInstance<FilterGroup>().maxOfOrNull { it.depth() } ?: 0)
