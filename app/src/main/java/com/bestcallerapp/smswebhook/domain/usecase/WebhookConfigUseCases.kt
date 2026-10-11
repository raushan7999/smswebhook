package com.bestcallerapp.smswebhook.domain.usecase

import com.bestcallerapp.smswebhook.core.constants.WebhookLimits
import com.bestcallerapp.smswebhook.core.utils.TimeProvider
import com.bestcallerapp.smswebhook.domain.engine.WebhookValidator
import com.bestcallerapp.smswebhook.domain.model.ValidationIssue
import com.bestcallerapp.smswebhook.domain.model.Webhook
import com.bestcallerapp.smswebhook.domain.repository.WebhookRepository
import javax.inject.Inject

sealed interface SaveWebhookResult {
    data class Saved(val id: Long) : SaveWebhookResult
    data class Invalid(val issues: List<ValidationIssue>) : SaveWebhookResult
}

class SaveWebhookUseCase @Inject constructor(
    private val repository: WebhookRepository,
    private val validator: WebhookValidator,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(webhook: Webhook): SaveWebhookResult {
        val normalised = webhook.copy(
            name = webhook.name.trim(),
            url = webhook.url.trim(),
            headers = webhook.headers.map { it.copy(name = it.name.trim()) },
            payload = webhook.payload.copy(
                fields = webhook.payload.fields.map { it.copy(outputName = it.outputName.trim()) },
                extractors = webhook.payload.extractors.map { it.copy(name = it.name.trim()) },
            ),
        )
        val issues = validator.validate(normalised)
        if (issues.isNotEmpty()) return SaveWebhookResult.Invalid(issues)
        val now = time.nowMillis()
        val stamped = normalised.copy(
            createdAtMillis = if (normalised.id == 0L) now else normalised.createdAtMillis,
            updatedAtMillis = now,
        )
        return SaveWebhookResult.Saved(repository.save(stamped))
    }
}

class DuplicateWebhookUseCase @Inject constructor(
    private val repository: WebhookRepository,
    private val time: TimeProvider,
) {
    /** Copies a webhook's configuration. The copy starts disabled so it never double-delivers by surprise. */
    suspend operator fun invoke(id: Long): Long? {
        val source = repository.getWebhook(id) ?: return null
        if (source.headers.any { it.needsReentry }) return null
        val now = time.nowMillis()
        return repository.save(
            source.copy(
                id = 0,
                name = "Copy of ${source.name}".take(WebhookLimits.NAME_MAX_LENGTH),
                enabled = false,
                createdAtMillis = now,
                updatedAtMillis = now,
            ),
        )
    }
}
