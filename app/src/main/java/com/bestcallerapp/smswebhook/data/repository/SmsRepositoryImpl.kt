package com.bestcallerapp.smswebhook.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.bestcallerapp.smswebhook.core.constants.AppConstants
import com.bestcallerapp.smswebhook.core.extensions.escapeForLike
import com.bestcallerapp.smswebhook.data.local.dao.SmsMessageDao
import com.bestcallerapp.smswebhook.data.local.entity.SmsMessageEntity
import com.bestcallerapp.smswebhook.domain.model.IncomingSms
import com.bestcallerapp.smswebhook.domain.model.MessageQuery
import com.bestcallerapp.smswebhook.domain.model.MessageStatus
import com.bestcallerapp.smswebhook.domain.model.MessageSummary
import com.bestcallerapp.smswebhook.domain.model.SmsMessage
import com.bestcallerapp.smswebhook.domain.repository.SmsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmsRepositoryImpl @Inject constructor(
    private val dao: SmsMessageDao,
) : SmsRepository {

    override suspend fun insertIfAbsent(sms: IncomingSms, fingerprint: String, createdAtMillis: Long): Long? {
        val id = dao.insert(
            SmsMessageEntity(
                fingerprint = fingerprint,
                sender = sms.sender,
                body = sms.body,
                sentAt = sms.sentAtMillis,
                receivedAt = sms.receivedAtMillis,
                subscriptionId = sms.subscriptionId,
                simSlot = sms.simSlot,
                partCount = sms.partCount,
                status = MessageStatus.RECEIVED,
                createdAt = createdAtMillis,
            ),
        )
        return id.takeIf { it > 0 }
    }

    override suspend fun getMessage(id: Long): SmsMessage? = dao.getById(id)?.toDomain()

    override suspend fun unprocessedIdForFingerprint(fingerprint: String): Long? =
        dao.idForFingerprintWithStatus(fingerprint, MessageStatus.RECEIVED.name)

    override fun observeMessage(id: Long): Flow<SmsMessage?> = dao.observeById(id).map { it?.toDomain() }

    override fun pagedMessages(query: MessageQuery): Flow<PagingData<MessageSummary>> {
        val search = query.search.trim().takeIf { it.isNotEmpty() }?.let { "%${it.escapeForLike()}%" }
        return Pager(PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false)) {
            if (query.statuses.isEmpty()) {
                dao.pagingSourceAllStatuses(search, AppConstants.PREVIEW_LENGTH)
            } else {
                dao.pagingSource(query.statuses.map { it.name }, search, AppConstants.PREVIEW_LENGTH)
            }
        }.flow.map { data -> data.map { it.toDomain() } }
    }

    override fun observeRecent(limit: Int): Flow<List<MessageSummary>> =
        dao.observeRecent(limit, AppConstants.PREVIEW_LENGTH).map { rows -> rows.map { it.toDomain() } }

    override fun observeCount(): Flow<Int> = dao.observeCount()

    override suspend fun unprocessedMessageIds(): List<Long> = dao.idsWithStatus(MessageStatus.RECEIVED.name)

    override suspend fun deleteInactiveOlderThan(cutoffMillis: Long): Int =
        deleteInBatches { dao.deleteInactiveOlderThan(cutoffMillis, ACTIVE, AppConstants.CLEANUP_BATCH_SIZE) }

    override suspend fun deleteInactiveBeyondNewest(keep: Int): Int {
        val boundary = dao.firstIdBeyondNewest(keep) ?: return 0
        return deleteInBatches { dao.deleteInactiveUpTo(boundary, ACTIVE, AppConstants.CLEANUP_BATCH_SIZE) }
    }

    override suspend fun deleteMessage(id: Long) = dao.delete(id)

    override suspend fun clearInactive() = dao.deleteAllInactive(ACTIVE)

    /** Small transactions keep the database responsive for the receiver while cleaning up. */
    private suspend fun deleteInBatches(deleteBatch: suspend () -> Int): Int {
        var total = 0
        while (true) {
            val deleted = deleteBatch()
            total += deleted
            if (deleted < AppConstants.CLEANUP_BATCH_SIZE) return total
        }
    }

    private companion object {
        const val PAGE_SIZE = 50
        val ACTIVE = MessageStatus.entries.filter { it.isActive }.map { it.name }
    }
}
