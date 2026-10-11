package com.bestcallerapp.smswebhook.data.local.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.bestcallerapp.smswebhook.data.local.entity.DeliveryAttemptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeliveryAttemptDao {

    @Insert
    suspend fun insert(attempt: DeliveryAttemptEntity): Long

    @Query("SELECT * FROM delivery_attempts WHERE outcome IN (:outcomes) ORDER BY started_at DESC, id DESC")
    fun pagingSource(outcomes: List<String>): PagingSource<Int, DeliveryAttemptEntity>

    @Query("SELECT * FROM delivery_attempts WHERE message_id = :messageId ORDER BY started_at DESC, id DESC")
    fun observeForMessage(messageId: Long): Flow<List<DeliveryAttemptEntity>>

    @Query(
        """
        DELETE FROM delivery_attempts WHERE id IN (
            SELECT id FROM delivery_attempts WHERE started_at < :cutoff LIMIT :batchSize
        )
        """,
    )
    suspend fun deleteOlderThan(cutoff: Long, batchSize: Int): Int

    @Query("DELETE FROM delivery_attempts")
    suspend fun clear()
}
