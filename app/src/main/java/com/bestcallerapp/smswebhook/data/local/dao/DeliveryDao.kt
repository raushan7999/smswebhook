package com.bestcallerapp.smswebhook.data.local.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bestcallerapp.smswebhook.data.local.entity.DeliveryEntity
import com.bestcallerapp.smswebhook.data.local.entity.DeliveryWithWebhookName
import kotlinx.coroutines.flow.Flow

@Dao
interface DeliveryDao {

    /** Returns -1 for rows that already existed (unique message/webhook pair). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(deliveries: List<DeliveryEntity>): List<Long>

    @Query(
        """
        SELECT d.id, d.message_id, d.webhook_id, w.name AS webhook_name, d.status, d.attempt_count,
               d.http_status, d.response, d.error_type, d.error, d.payload_body, d.content_type,
               d.last_attempt_at, d.next_attempt_at, d.created_at, d.updated_at
        FROM webhook_deliveries d JOIN webhooks w ON w.id = d.webhook_id
        WHERE d.id = :id
        """,
    )
    suspend fun getById(id: Long): DeliveryWithWebhookName?

    @Query(
        """
        SELECT d.id, d.message_id, d.webhook_id, w.name AS webhook_name, d.status, d.attempt_count,
               d.http_status, d.response, d.error_type, d.error, d.payload_body, d.content_type,
               d.last_attempt_at, d.next_attempt_at, d.created_at, d.updated_at
        FROM webhook_deliveries d JOIN webhooks w ON w.id = d.webhook_id
        WHERE d.message_id = :messageId ORDER BY w.name COLLATE NOCASE
        """,
    )
    fun observeForMessage(messageId: Long): Flow<List<DeliveryWithWebhookName>>

    @Query(
        """
        SELECT d.id, d.message_id, d.webhook_id, w.name AS webhook_name, d.status, d.attempt_count,
               d.http_status, d.response, d.error_type, d.error, d.payload_body, d.content_type,
               d.last_attempt_at, d.next_attempt_at, d.created_at, d.updated_at
        FROM webhook_deliveries d JOIN webhooks w ON w.id = d.webhook_id
        WHERE d.message_id = :messageId
        """,
    )
    suspend fun forMessage(messageId: Long): List<DeliveryWithWebhookName>

    @Query(
        """
        SELECT d.id, d.message_id, d.webhook_id, w.name AS webhook_name, d.status, d.attempt_count,
               d.http_status, d.response, d.error_type, d.error, d.payload_body, d.content_type,
               d.last_attempt_at, d.next_attempt_at, d.created_at, d.updated_at
        FROM webhook_deliveries d JOIN webhooks w ON w.id = d.webhook_id
        WHERE d.webhook_id = :webhookId AND d.status IN (:statuses)
        ORDER BY d.id DESC
        """,
    )
    fun pagingForWebhook(webhookId: Long, statuses: List<String>): PagingSource<Int, DeliveryWithWebhookName>

    @Query(
        """
        SELECT d.id, d.message_id, d.webhook_id, w.name AS webhook_name, d.status, d.attempt_count,
               d.http_status, d.response, d.error_type, d.error, d.payload_body, d.content_type,
               d.last_attempt_at, d.next_attempt_at, d.created_at, d.updated_at
        FROM webhook_deliveries d JOIN webhooks w ON w.id = d.webhook_id
        WHERE d.webhook_id = :webhookId
        ORDER BY d.id DESC
        """,
    )
    fun pagingAllForWebhook(webhookId: Long): PagingSource<Int, DeliveryWithWebhookName>

    /** Messages whose derived status may change if this webhook's deliveries disappear. */
    @Query(
        """
        SELECT DISTINCT d.message_id FROM webhook_deliveries d JOIN sms_messages m ON m.id = d.message_id
        WHERE d.webhook_id = :webhookId AND m.status IN (:statuses)
        """,
    )
    suspend fun messageIdsForWebhookWithMessageStatus(webhookId: Long, statuses: List<String>): List<Long>

    @Query("SELECT id FROM webhook_deliveries WHERE message_id = :messageId AND status IN (:statuses)")
    suspend fun idsForMessageWithStatuses(messageId: Long, statuses: List<String>): List<Long>

    @Query("SELECT status FROM webhook_deliveries WHERE message_id = :messageId")
    suspend fun statusesForMessage(messageId: Long): List<String>

    @Query("SELECT message_id FROM webhook_deliveries WHERE id = :id")
    suspend fun messageIdOf(id: Long): Long?

    @Query(
        """
        UPDATE webhook_deliveries
        SET status = :processing, attempt_count = attempt_count + 1, last_attempt_at = :now, updated_at = :now
        WHERE id = :id AND status IN (:claimable)
        """,
    )
    suspend fun claim(id: Long, processing: String, claimable: List<String>, now: Long): Int

    @Query(
        """
        UPDATE webhook_deliveries
        SET status = :status, http_status = :httpStatus, response = :response, error_type = :errorType,
            error = :error, next_attempt_at = :nextAttemptAt, updated_at = :now
        WHERE id = :id
        """,
    )
    suspend fun updateResult(
        id: Long,
        status: String,
        httpStatus: Int?,
        response: String?,
        errorType: String?,
        error: String?,
        nextAttemptAt: Long?,
        now: Long,
    )

    @Query(
        """
        UPDATE webhook_deliveries SET status = :pending, next_attempt_at = NULL, updated_at = :now
        WHERE id = :id AND status = :failed
        """,
    )
    suspend fun resetFailed(id: Long, pending: String, failed: String, now: Long): Int

    @Query("SELECT id FROM webhook_deliveries WHERE webhook_id = :webhookId AND status = :status")
    suspend fun idsForWebhookWithStatus(webhookId: Long, status: String): List<Long>

    @Query("SELECT id FROM webhook_deliveries WHERE status IN (:statuses) ORDER BY id")
    suspend fun idsWithStatuses(statuses: List<String>): List<Long>

    @Query("SELECT COUNT(*) FROM webhook_deliveries WHERE status IN (:statuses)")
    fun observeCountWithStatuses(statuses: List<String>): Flow<Int>
}
