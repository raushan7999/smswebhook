package com.bestcallerapp.smswebhook.data.repository

import androidx.room.withTransaction
import com.bestcallerapp.smswebhook.core.logging.AppLogger
import com.bestcallerapp.smswebhook.core.security.SecretCipher
import com.bestcallerapp.smswebhook.core.security.SecretUnavailableException
import com.bestcallerapp.smswebhook.data.local.dao.DeliveryDao
import com.bestcallerapp.smswebhook.data.local.dao.WebhookDao
import com.bestcallerapp.smswebhook.data.local.dao.WebhookWithConfig
import com.bestcallerapp.smswebhook.data.local.database.AppDatabase
import com.bestcallerapp.smswebhook.data.local.entity.RegexExtractorEntity
import com.bestcallerapp.smswebhook.data.local.entity.WebhookEntity
import com.bestcallerapp.smswebhook.data.local.entity.WebhookFilterEntity
import com.bestcallerapp.smswebhook.data.local.entity.WebhookHeaderEntity
import com.bestcallerapp.smswebhook.data.local.entity.WebhookPayloadConfigEntity
import com.bestcallerapp.smswebhook.data.serialization.FilterDefinitionCodec
import com.bestcallerapp.smswebhook.data.serialization.PayloadFieldsCodec
import com.bestcallerapp.smswebhook.domain.model.DeliveryStatus
import com.bestcallerapp.smswebhook.domain.model.MessageStatus
import com.bestcallerapp.smswebhook.domain.model.Webhook
import com.bestcallerapp.smswebhook.domain.model.WebhookHeader
import com.bestcallerapp.smswebhook.domain.model.WebhookOverview
import com.bestcallerapp.smswebhook.domain.model.filter.FilterGroup
import com.bestcallerapp.smswebhook.domain.model.payload.PayloadConfig
import com.bestcallerapp.smswebhook.domain.model.payload.RegexExtractor
import com.bestcallerapp.smswebhook.domain.repository.WebhookRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Persists webhook configurations. Secret header values are encrypted with the Keystore. */
@Singleton
class WebhookRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val dao: WebhookDao,
    private val deliveryDao: DeliveryDao,
    private val statusUpdater: MessageStatusUpdater,
    private val cipher: SecretCipher,
    private val logger: AppLogger,
) : WebhookRepository {

    override fun observeOverviews(): Flow<List<WebhookOverview>> =
        dao.observeOverviews(
            sent = DeliveryStatus.SENT.name,
            failed = DeliveryStatus.FAILED.name,
            active = DeliveryStatus.entries.filter { it.isActive }.map { it.name },
        ).map { rows -> rows.map { it.toDomain() } }

    override fun observeWebhook(id: Long): Flow<Webhook?> = dao.observeWithConfig(id).map { it?.toDomain() }

    override suspend fun getWebhook(id: Long): Webhook? = dao.getWithConfig(id)?.toDomain()

    override suspend fun getEnabledWebhooks(): List<Webhook> = dao.getEnabledWithConfig().map { it.toDomain() }

    override suspend fun getAll(): List<Webhook> = dao.getAllWithConfig().map { it.toDomain() }

    override suspend fun save(webhook: Webhook): Long = database.withTransaction {
        val entity = WebhookEntity(
            id = webhook.id,
            name = webhook.name,
            url = webhook.url,
            method = webhook.method,
            enabled = webhook.enabled,
            timeoutSeconds = webhook.timeoutSeconds,
            maxRetries = webhook.maxRetries,
            retryDelaySeconds = webhook.retryDelaySeconds,
            scope = webhook.scope,
            createdAt = webhook.createdAtMillis,
            updatedAt = webhook.updatedAtMillis,
        )
        val id = if (webhook.id == 0L) dao.insert(entity) else webhook.id.also { dao.update(entity) }

        dao.deleteHeaders(id)
        if (webhook.headers.isNotEmpty()) {
            dao.insertHeaders(
                webhook.headers.mapIndexed { index, header ->
                    WebhookHeaderEntity(
                        webhookId = id,
                        position = index,
                        name = header.name,
                        value = storedValue(header),
                        isSecret = header.isSecret,
                    )
                },
            )
        }
        dao.upsertFilter(WebhookFilterEntity(id, FilterDefinitionCodec.SCHEMA_VERSION, FilterDefinitionCodec.encode(webhook.filter)))
        dao.upsertPayload(
            WebhookPayloadConfigEntity(
                webhookId = id,
                format = webhook.payload.format,
                fields = PayloadFieldsCodec.encode(webhook.payload.fields),
                template = webhook.payload.template,
            ),
        )
        dao.deleteExtractors(id)
        if (webhook.payload.extractors.isNotEmpty()) {
            dao.insertExtractors(
                webhook.payload.extractors.mapIndexed { index, extractor ->
                    RegexExtractorEntity(
                        webhookId = id,
                        position = index,
                        fieldName = extractor.name,
                        pattern = extractor.pattern,
                        captureGroup = extractor.captureGroup,
                        caseSensitive = extractor.caseSensitive,
                    )
                },
            )
        }
        id
    }

    override suspend fun setEnabled(id: Long, enabled: Boolean, nowMillis: Long) = dao.setEnabled(id, enabled, nowMillis)

    /**
     * Deliveries are removed by cascade, so the derived status of affected messages is recomputed
     * in the same transaction; otherwise a message pending only for this webhook would stay
     * "active" forever (and be exempt from retention).
     */
    override suspend fun delete(id: Long) = database.withTransaction {
        val affected = deliveryDao.messageIdsForWebhookWithMessageStatus(id, RECOMPUTE_STATUSES)
        dao.delete(id)
        affected.forEach { statusUpdater.recompute(it) }
    }

    private fun storedValue(header: WebhookHeader): String = when {
        !header.isSecret -> header.value
        header.needsReentry || header.value.isEmpty() -> ""
        else -> cipher.encrypt(header.value)
    }

    private fun WebhookWithConfig.toDomain(): Webhook = Webhook(
        id = webhook.id,
        name = webhook.name,
        url = webhook.url,
        method = webhook.method,
        enabled = webhook.enabled,
        timeoutSeconds = webhook.timeoutSeconds,
        maxRetries = webhook.maxRetries,
        retryDelaySeconds = webhook.retryDelaySeconds,
        headers = headers.sortedBy { it.position }.map { it.toDomain() },
        scope = webhook.scope,
        filter = filter?.let { FilterDefinitionCodec.decode(it.definition) } ?: FilterGroup(),
        payload = PayloadConfig(
            format = payload?.format ?: PayloadConfig.default().format,
            fields = PayloadFieldsCodec.decode(payload?.fields ?: "[]").let { decoded ->
                if (payload == null) PayloadConfig.default().fields else decoded
            },
            template = payload?.template.orEmpty(),
            extractors = extractors.sortedBy { it.position }.map {
                RegexExtractor(it.fieldName, it.pattern, it.captureGroup, it.caseSensitive)
            },
        ),
        createdAtMillis = webhook.createdAt,
        updatedAtMillis = webhook.updatedAt,
    )

    private fun WebhookHeaderEntity.toDomain(): WebhookHeader {
        if (!isSecret) return WebhookHeader(name, value, isSecret = false)
        if (value.isEmpty()) return WebhookHeader(name, "", isSecret = true, needsReentry = true)
        return try {
            WebhookHeader(name, cipher.decrypt(value), isSecret = true)
        } catch (e: SecretUnavailableException) {
            logger.w(TAG, "Secret header for webhook $webhookId could not be decrypted", e)
            WebhookHeader(name, "", isSecret = true, needsReentry = true)
        }
    }

    private companion object {
        const val TAG = "WebhookRepo"
        val RECOMPUTE_STATUSES = MessageStatus.entries.filter { it.isActive || it == MessageStatus.FAILED }.map { it.name }
    }
}
