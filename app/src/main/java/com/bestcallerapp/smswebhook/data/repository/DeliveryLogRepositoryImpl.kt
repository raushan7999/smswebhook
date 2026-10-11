package com.bestcallerapp.smswebhook.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.bestcallerapp.smswebhook.core.constants.AppConstants
import com.bestcallerapp.smswebhook.data.local.dao.DeliveryAttemptDao
import com.bestcallerapp.smswebhook.domain.model.AttemptOutcome
import com.bestcallerapp.smswebhook.domain.model.DeliveryAttempt
import com.bestcallerapp.smswebhook.domain.model.LogFilter
import com.bestcallerapp.smswebhook.domain.repository.DeliveryLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeliveryLogRepositoryImpl @Inject constructor(
    private val dao: DeliveryAttemptDao,
) : DeliveryLogRepository {

    override suspend fun record(attempt: DeliveryAttempt) {
        dao.insert(attempt.toEntity())
    }

    override fun paged(filter: LogFilter): Flow<PagingData<DeliveryAttempt>> {
        val outcomes = when (filter) {
            LogFilter.ALL -> AttemptOutcome.entries
            LogFilter.SUCCESS -> listOf(AttemptOutcome.SUCCESS)
            LogFilter.FAILURE -> listOf(AttemptOutcome.RETRYABLE_FAILURE, AttemptOutcome.PERMANENT_FAILURE)
        }.map { it.name }
        return Pager(PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false)) {
            dao.pagingSource(outcomes)
        }.flow.map { data -> data.map { it.toDomain() } }
    }

    override fun observeForMessage(messageId: Long): Flow<List<DeliveryAttempt>> =
        dao.observeForMessage(messageId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun deleteOlderThan(cutoffMillis: Long): Int {
        var total = 0
        while (true) {
            val deleted = dao.deleteOlderThan(cutoffMillis, AppConstants.CLEANUP_BATCH_SIZE)
            total += deleted
            if (deleted < AppConstants.CLEANUP_BATCH_SIZE) return total
        }
    }

    override suspend fun clear() = dao.clear()

    private companion object {
        const val PAGE_SIZE = 50
    }
}
