package com.bestcallerapp.smswebhook.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.bestcallerapp.smswebhook.data.local.entity.RegexExtractorEntity
import com.bestcallerapp.smswebhook.data.local.entity.WebhookEntity
import com.bestcallerapp.smswebhook.data.local.entity.WebhookFilterEntity
import com.bestcallerapp.smswebhook.data.local.entity.WebhookHeaderEntity
import com.bestcallerapp.smswebhook.data.local.entity.WebhookOverviewRow
import com.bestcallerapp.smswebhook.data.local.entity.WebhookPayloadConfigEntity
import kotlinx.coroutines.flow.Flow

data class WebhookWithConfig(
    @Embedded val webhook: WebhookEntity,
    @Relation(parentColumn = "id", entityColumn = "webhook_id")
    val headers: List<WebhookHeaderEntity>,
    @Relation(parentColumn = "id", entityColumn = "webhook_id")
    val filter: WebhookFilterEntity?,
    @Relation(parentColumn = "id", entityColumn = "webhook_id")
    val payload: WebhookPayloadConfigEntity?,
    @Relation(parentColumn = "id", entityColumn = "webhook_id")
    val extractors: List<RegexExtractorEntity>,
)

@Dao
interface WebhookDao {

    @Query(
        """
        SELECT w.id, w.name, w.url, w.enabled, w.scope,
            (SELECT COUNT(*) FROM webhook_deliveries d WHERE d.webhook_id = w.id AND d.status = :sent) AS sent_count,
            (SELECT COUNT(*) FROM webhook_deliveries d WHERE d.webhook_id = w.id AND d.status = :failed) AS failed_count,
            (SELECT COUNT(*) FROM webhook_deliveries d WHERE d.webhook_id = w.id AND d.status IN (:active)) AS pending_count
        FROM webhooks w ORDER BY w.name COLLATE NOCASE, w.id
        """,
    )
    fun observeOverviews(sent: String, failed: String, active: List<String>): Flow<List<WebhookOverviewRow>>

    @Transaction
    @Query("SELECT * FROM webhooks WHERE id = :id")
    suspend fun getWithConfig(id: Long): WebhookWithConfig?

    @Transaction
    @Query("SELECT * FROM webhooks WHERE id = :id")
    fun observeWithConfig(id: Long): Flow<WebhookWithConfig?>

    @Transaction
    @Query("SELECT * FROM webhooks WHERE enabled = 1 ORDER BY id")
    suspend fun getEnabledWithConfig(): List<WebhookWithConfig>

    @Transaction
    @Query("SELECT * FROM webhooks ORDER BY id")
    suspend fun getAllWithConfig(): List<WebhookWithConfig>

    @Query("SELECT COUNT(*) FROM webhooks WHERE enabled = 1")
    fun observeEnabledCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM webhooks")
    fun observeTotalCount(): Flow<Int>

    @Insert
    suspend fun insert(webhook: WebhookEntity): Long

    @Update
    suspend fun update(webhook: WebhookEntity)

    @Query("UPDATE webhooks SET enabled = :enabled, updated_at = :now WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean, now: Long)

    @Query("DELETE FROM webhooks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM webhook_headers WHERE webhook_id = :webhookId")
    suspend fun deleteHeaders(webhookId: Long)

    @Insert
    suspend fun insertHeaders(headers: List<WebhookHeaderEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFilter(filter: WebhookFilterEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPayload(payload: WebhookPayloadConfigEntity)

    @Query("DELETE FROM regex_extractors WHERE webhook_id = :webhookId")
    suspend fun deleteExtractors(webhookId: Long)

    @Insert
    suspend fun insertExtractors(extractors: List<RegexExtractorEntity>)
}
