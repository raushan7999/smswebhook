package com.bestcallerapp.smswebhook.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.bestcallerapp.smswebhook.domain.model.AttemptOutcome
import com.bestcallerapp.smswebhook.domain.model.DeliveryErrorType
import com.bestcallerapp.smswebhook.domain.model.DeliveryStatus
import com.bestcallerapp.smswebhook.domain.model.HttpMethod
import com.bestcallerapp.smswebhook.domain.model.MessageStatus
import com.bestcallerapp.smswebhook.domain.model.WebhookScope
import com.bestcallerapp.smswebhook.domain.model.payload.PayloadFormat

@Entity(
    tableName = "sms_messages",
    indices = [
        Index(value = ["fingerprint"], unique = true),
        Index(value = ["received_at"]),
        Index(value = ["status", "received_at"]),
    ],
)
data class SmsMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fingerprint: String,
    val sender: String,
    val body: String,
    @ColumnInfo(name = "sent_at") val sentAt: Long,
    @ColumnInfo(name = "received_at") val receivedAt: Long,
    @ColumnInfo(name = "subscription_id") val subscriptionId: Int?,
    @ColumnInfo(name = "sim_slot") val simSlot: Int?,
    @ColumnInfo(name = "part_count") val partCount: Int,
    val status: MessageStatus,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(tableName = "webhooks")
data class WebhookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val url: String,
    val method: HttpMethod,
    val enabled: Boolean,
    @ColumnInfo(name = "timeout_seconds") val timeoutSeconds: Int,
    @ColumnInfo(name = "max_retries") val maxRetries: Int,
    @ColumnInfo(name = "retry_delay_seconds") val retryDelaySeconds: Int,
    val scope: WebhookScope,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "webhook_headers",
    foreignKeys = [
        ForeignKey(
            entity = WebhookEntity::class,
            parentColumns = ["id"],
            childColumns = ["webhook_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["webhook_id"])],
)
data class WebhookHeaderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "webhook_id") val webhookId: Long,
    val position: Int,
    val name: String,
    /** Plaintext for non-secret headers; Keystore ciphertext for secret ones. */
    val value: String,
    @ColumnInfo(name = "is_secret") val isSecret: Boolean,
)

/** The webhook's filter tree, stored as versioned JSON (see FilterDefinitionCodec). */
@Entity(
    tableName = "webhook_filters",
    foreignKeys = [
        ForeignKey(
            entity = WebhookEntity::class,
            parentColumns = ["id"],
            childColumns = ["webhook_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class WebhookFilterEntity(
    @PrimaryKey @ColumnInfo(name = "webhook_id") val webhookId: Long,
    @ColumnInfo(name = "schema_version") val schemaVersion: Int,
    val definition: String,
)

@Entity(
    tableName = "webhook_payload_config",
    foreignKeys = [
        ForeignKey(
            entity = WebhookEntity::class,
            parentColumns = ["id"],
            childColumns = ["webhook_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class WebhookPayloadConfigEntity(
    @PrimaryKey @ColumnInfo(name = "webhook_id") val webhookId: Long,
    val format: PayloadFormat,
    /** JSON array of payload field settings. */
    val fields: String,
    val template: String,
)

@Entity(
    tableName = "regex_extractors",
    foreignKeys = [
        ForeignKey(
            entity = WebhookEntity::class,
            parentColumns = ["id"],
            childColumns = ["webhook_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["webhook_id"])],
)
data class RegexExtractorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "webhook_id") val webhookId: Long,
    val position: Int,
    @ColumnInfo(name = "field_name") val fieldName: String,
    val pattern: String,
    @ColumnInfo(name = "capture_group") val captureGroup: Int,
    @ColumnInfo(name = "case_sensitive") val caseSensitive: Boolean,
)

@Entity(
    tableName = "webhook_deliveries",
    foreignKeys = [
        ForeignKey(
            entity = SmsMessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["message_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = WebhookEntity::class,
            parentColumns = ["id"],
            childColumns = ["webhook_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["message_id", "webhook_id"], unique = true),
        Index(value = ["webhook_id", "status"]),
        Index(value = ["status"]),
    ],
)
data class DeliveryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "message_id") val messageId: Long,
    @ColumnInfo(name = "webhook_id") val webhookId: Long,
    val status: DeliveryStatus,
    @ColumnInfo(name = "attempt_count") val attemptCount: Int = 0,
    @ColumnInfo(name = "http_status") val httpStatus: Int? = null,
    val response: String? = null,
    @ColumnInfo(name = "error_type") val errorType: DeliveryErrorType? = null,
    val error: String? = null,
    @ColumnInfo(name = "payload_body") val payloadBody: String?,
    @ColumnInfo(name = "content_type") val contentType: String?,
    @ColumnInfo(name = "last_attempt_at") val lastAttemptAt: Long? = null,
    @ColumnInfo(name = "next_attempt_at") val nextAttemptAt: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "delivery_attempts",
    foreignKeys = [
        ForeignKey(
            entity = DeliveryEntity::class,
            parentColumns = ["id"],
            childColumns = ["delivery_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["delivery_id"]),
        Index(value = ["message_id"]),
        Index(value = ["started_at"]),
        Index(value = ["outcome", "started_at"]),
    ],
)
data class DeliveryAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "delivery_id") val deliveryId: Long,
    @ColumnInfo(name = "message_id") val messageId: Long,
    @ColumnInfo(name = "webhook_id") val webhookId: Long,
    @ColumnInfo(name = "webhook_name") val webhookName: String,
    @ColumnInfo(name = "attempt_number") val attemptNumber: Int,
    @ColumnInfo(name = "request_method") val requestMethod: String,
    @ColumnInfo(name = "request_url") val requestUrl: String,
    @ColumnInfo(name = "http_status") val httpStatus: Int?,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    val outcome: AttemptOutcome,
    @ColumnInfo(name = "error_type") val errorType: DeliveryErrorType?,
    val error: String?,
    val response: String?,
    @ColumnInfo(name = "started_at") val startedAt: Long,
)

/** Projection joining a delivery with its webhook's name. */
data class DeliveryWithWebhookName(
    val id: Long,
    @ColumnInfo(name = "message_id") val messageId: Long,
    @ColumnInfo(name = "webhook_id") val webhookId: Long,
    @ColumnInfo(name = "webhook_name") val webhookName: String,
    val status: DeliveryStatus,
    @ColumnInfo(name = "attempt_count") val attemptCount: Int,
    @ColumnInfo(name = "http_status") val httpStatus: Int?,
    val response: String?,
    @ColumnInfo(name = "error_type") val errorType: DeliveryErrorType?,
    val error: String?,
    @ColumnInfo(name = "payload_body") val payloadBody: String?,
    @ColumnInfo(name = "content_type") val contentType: String?,
    @ColumnInfo(name = "last_attempt_at") val lastAttemptAt: Long?,
    @ColumnInfo(name = "next_attempt_at") val nextAttemptAt: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

data class MessageSummaryRow(
    val id: Long,
    val sender: String,
    val preview: String,
    @ColumnInfo(name = "received_at") val receivedAt: Long,
    val status: MessageStatus,
)

data class WebhookOverviewRow(
    val id: Long,
    val name: String,
    val url: String,
    val enabled: Boolean,
    val scope: WebhookScope,
    @ColumnInfo(name = "sent_count") val sentCount: Int,
    @ColumnInfo(name = "failed_count") val failedCount: Int,
    @ColumnInfo(name = "pending_count") val pendingCount: Int,
)

data class DeliveryCountsRow(
    val pending: Int,
    val sent: Int,
    val failed: Int,
)
