package com.bestcallerapp.smswebhook.data.serialization

import com.bestcallerapp.smswebhook.domain.model.filter.FilterCondition
import com.bestcallerapp.smswebhook.domain.model.filter.FilterField
import com.bestcallerapp.smswebhook.domain.model.filter.FilterGroup
import com.bestcallerapp.smswebhook.domain.model.filter.FilterNode
import com.bestcallerapp.smswebhook.domain.model.filter.FilterOperator
import com.bestcallerapp.smswebhook.domain.model.filter.LogicalOperator
import com.bestcallerapp.smswebhook.domain.model.filter.newNodeId
import com.bestcallerapp.smswebhook.domain.model.payload.PayloadField
import com.bestcallerapp.smswebhook.domain.model.payload.PayloadFieldKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * JSON representations used for persistence of nested configuration and for export files.
 * Enum values are stored as strings and decoded leniently so that data written by a newer app
 * version (with values this version does not know) degrades gracefully instead of crashing.
 */
internal val ConfigJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    classDiscriminator = "type"
}

@Serializable
sealed class FilterNodeJson {
    abstract val id: String
    abstract val negate: Boolean
    abstract val enabled: Boolean
}

@Serializable
@SerialName("group")
data class FilterGroupJson(
    override val id: String = newNodeId(),
    val operator: String = LogicalOperator.AND.name,
    val children: List<FilterNodeJson> = emptyList(),
    override val negate: Boolean = false,
    override val enabled: Boolean = true,
) : FilterNodeJson()

@Serializable
@SerialName("condition")
data class FilterConditionJson(
    override val id: String = newNodeId(),
    val field: String,
    val operator: String,
    val value: String = "",
    val secondValue: String = "",
    val keywords: List<String> = emptyList(),
    val caseSensitive: Boolean = false,
    override val negate: Boolean = false,
    override val enabled: Boolean = true,
) : FilterNodeJson()

@Serializable
data class PayloadFieldJson(val key: String, val name: String, val enabled: Boolean)

object FilterDefinitionCodec {
    const val SCHEMA_VERSION = 1

    fun encode(group: FilterGroup): String = ConfigJson.encodeToString(FilterNodeJson.serializer(), toJson(group))

    fun decode(json: String): FilterGroup = try {
        (fromJson(ConfigJson.decodeFromString(FilterNodeJson.serializer(), json)) as? FilterGroup) ?: FilterGroup()
    } catch (_: Exception) {
        FilterGroup()
    }

    fun toJson(node: FilterNode): FilterNodeJson = when (node) {
        is FilterGroup -> FilterGroupJson(node.id, node.operator.name, node.children.map(::toJson), node.negate, node.enabled)
        is FilterCondition -> FilterConditionJson(
            node.id, node.field.name, node.operator.name, node.value, node.secondValue,
            node.keywords, node.caseSensitive, node.negate, node.enabled,
        )
    }

    fun fromJson(node: FilterNodeJson): FilterNode = when (node) {
        is FilterGroupJson -> FilterGroup(
            id = node.id,
            operator = enumOrNull<LogicalOperator>(node.operator) ?: LogicalOperator.AND,
            children = node.children.map(::fromJson),
            negate = node.negate,
            enabled = node.enabled,
        )
        is FilterConditionJson -> {
            val field = enumOrNull<FilterField>(node.field)
            val operator = enumOrNull<FilterOperator>(node.operator)
            FilterCondition(
                id = node.id,
                field = field ?: FilterField.MESSAGE,
                operator = operator ?: FilterOperator.CONTAINS,
                value = node.value,
                secondValue = node.secondValue,
                keywords = node.keywords,
                caseSensitive = node.caseSensitive,
                negate = node.negate,
                // Unknown field/operator: keep the node but disable it rather than guess semantics.
                enabled = node.enabled && field != null && operator != null,
            )
        }
    }
}

object PayloadFieldsCodec {
    private val serializer = ListSerializer(PayloadFieldJson.serializer())

    fun encode(fields: List<PayloadField>): String =
        ConfigJson.encodeToString(serializer, fields.map(::toJson))

    fun decode(json: String): List<PayloadField> = try {
        normalise(ConfigJson.decodeFromString(serializer, json).mapNotNull(::fromJson))
    } catch (_: Exception) {
        normalise(emptyList())
    }

    fun toJson(field: PayloadField) = PayloadFieldJson(field.key.name, field.outputName, field.enabled)

    fun fromJson(json: PayloadFieldJson): PayloadField? =
        enumOrNull<PayloadFieldKey>(json.key)?.let { PayloadField(it, json.name, json.enabled) }

    /** Ensures every known field appears exactly once, in a stable order. */
    fun normalise(fields: List<PayloadField>): List<PayloadField> {
        val byKey = fields.associateBy { it.key }
        return PayloadFieldKey.entries.map { key -> byKey[key] ?: PayloadField(key, enabled = false) }
    }
}

internal inline fun <reified T : Enum<T>> enumOrNull(name: String): T? =
    enumValues<T>().firstOrNull { it.name == name }
