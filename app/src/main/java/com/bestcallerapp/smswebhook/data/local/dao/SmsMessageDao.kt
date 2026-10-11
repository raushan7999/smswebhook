package com.bestcallerapp.smswebhook.data.local.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bestcallerapp.smswebhook.data.local.entity.MessageSummaryRow
import com.bestcallerapp.smswebhook.data.local.entity.SmsMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsMessageDao {

    /** Returns the new row id, or -1 if a message with the same fingerprint already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: SmsMessageEntity): Long

    @Query("SELECT * FROM sms_messages WHERE id = :id")
    suspend fun getById(id: Long): SmsMessageEntity?

    @Query("SELECT id FROM sms_messages WHERE fingerprint = :fingerprint AND status = :status")
    suspend fun idForFingerprintWithStatus(fingerprint: String, status: String): Long?

    @Query("SELECT * FROM sms_messages WHERE id = :id")
    fun observeById(id: Long): Flow<SmsMessageEntity?>

    @Query(
        """
        SELECT id, sender, substr(body, 1, :previewLength) AS preview, received_at, status
        FROM sms_messages
        WHERE status IN (:statuses)
          AND (:search IS NULL OR sender LIKE :search ESCAPE '\' OR body LIKE :search ESCAPE '\')
        ORDER BY received_at DESC, id DESC
        """,
    )
    fun pagingSource(statuses: List<String>, search: String?, previewLength: Int): PagingSource<Int, MessageSummaryRow>

    /** Unfiltered variant: walks the received_at index instead of sorting the whole table. */
    @Query(
        """
        SELECT id, sender, substr(body, 1, :previewLength) AS preview, received_at, status
        FROM sms_messages
        WHERE (:search IS NULL OR sender LIKE :search ESCAPE '\' OR body LIKE :search ESCAPE '\')
        ORDER BY received_at DESC, id DESC
        """,
    )
    fun pagingSourceAllStatuses(search: String?, previewLength: Int): PagingSource<Int, MessageSummaryRow>

    @Query(
        """
        SELECT id, sender, substr(body, 1, :previewLength) AS preview, received_at, status
        FROM sms_messages ORDER BY received_at DESC, id DESC LIMIT :limit
        """,
    )
    fun observeRecent(limit: Int, previewLength: Int): Flow<List<MessageSummaryRow>>

    @Query("SELECT COUNT(*) FROM sms_messages")
    fun observeCount(): Flow<Int>

    @Query("SELECT id FROM sms_messages WHERE status = :status ORDER BY id")
    suspend fun idsWithStatus(status: String): List<Long>

    @Query("UPDATE sms_messages SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query(
        """
        DELETE FROM sms_messages WHERE id IN (
            SELECT id FROM sms_messages
            WHERE received_at < :cutoff AND status NOT IN (:activeStatuses)
            LIMIT :batchSize
        )
        """,
    )
    suspend fun deleteInactiveOlderThan(cutoff: Long, activeStatuses: List<String>, batchSize: Int): Int

    /** Id of the newest message that falls outside the newest [keep] messages, if any. */
    @Query("SELECT id FROM sms_messages ORDER BY id DESC LIMIT 1 OFFSET :keep")
    suspend fun firstIdBeyondNewest(keep: Int): Long?

    @Query(
        """
        DELETE FROM sms_messages WHERE id IN (
            SELECT id FROM sms_messages
            WHERE id <= :maxId AND status NOT IN (:activeStatuses)
            LIMIT :batchSize
        )
        """,
    )
    suspend fun deleteInactiveUpTo(maxId: Long, activeStatuses: List<String>, batchSize: Int): Int

    @Query("DELETE FROM sms_messages WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM sms_messages WHERE status NOT IN (:activeStatuses)")
    suspend fun deleteAllInactive(activeStatuses: List<String>)
}
