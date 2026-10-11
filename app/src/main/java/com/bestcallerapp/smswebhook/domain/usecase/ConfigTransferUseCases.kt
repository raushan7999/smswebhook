package com.bestcallerapp.smswebhook.domain.usecase

import com.bestcallerapp.smswebhook.core.utils.TimeProvider
import com.bestcallerapp.smswebhook.domain.engine.WebhookValidator
import com.bestcallerapp.smswebhook.domain.port.ConfigCodec
import com.bestcallerapp.smswebhook.domain.repository.WebhookRepository
import javax.inject.Inject

class ExportConfigUseCase @Inject constructor(
    private val repository: WebhookRepository,
    private val codec: ConfigCodec,
    private val time: TimeProvider,
) {
    /**
     * @param includeSecrets only true after the user explicitly confirmed a warning that the file
     *   will contain credentials in plain text.
     */
    suspend operator fun invoke(includeSecrets: Boolean): String =
        codec.encode(repository.getAll(), includeSecrets, time.nowMillis())
}

data class ImportReport(val imported: Int, val skipped: List<String>, val needSecrets: Int)

class ImportConfigUseCase @Inject constructor(
    private val repository: WebhookRepository,
    private val codec: ConfigCodec,
    private val validator: WebhookValidator,
    private val time: TimeProvider,
) {
    /**
     * Imports webhooks as new, disabled entries so nothing is forwarded until the user reviews
     * them. @throws com.bestcallerapp.smswebhook.domain.port.ConfigFormatException
     */
    suspend operator fun invoke(text: String): ImportReport {
        val webhooks = codec.decode(text)
        val skipped = mutableListOf<String>()
        var imported = 0
        var needSecrets = 0
        val now = time.nowMillis()
        webhooks.forEach { webhook ->
            val issues = validator.validate(webhook, allowMissingSecrets = true)
            if (issues.isNotEmpty()) {
                skipped += "${webhook.name.ifBlank { "Unnamed" }}: ${issues.first().message}"
                return@forEach
            }
            repository.save(webhook.copy(id = 0, enabled = false, createdAtMillis = now, updatedAtMillis = now))
            imported++
            if (webhook.headers.any { it.needsReentry }) needSecrets++
        }
        return ImportReport(imported, skipped, needSecrets)
    }
}
