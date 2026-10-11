package com.bestcallerapp.smswebhook.presentation.webhook_editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.bestcallerapp.smswebhook.core.constants.WebhookLimits
import com.bestcallerapp.smswebhook.domain.engine.RegexTestResult
import com.bestcallerapp.smswebhook.domain.engine.WebhookPreview
import com.bestcallerapp.smswebhook.domain.engine.WebhookPreviewer
import com.bestcallerapp.smswebhook.domain.engine.WebhookValidator
import com.bestcallerapp.smswebhook.domain.model.ConfigSection
import com.bestcallerapp.smswebhook.domain.model.ValidationIssue
import com.bestcallerapp.smswebhook.domain.model.Webhook
import com.bestcallerapp.smswebhook.domain.model.WebhookHeader
import com.bestcallerapp.smswebhook.domain.model.WebhookScope
import com.bestcallerapp.smswebhook.domain.model.filter.FilterCondition
import com.bestcallerapp.smswebhook.domain.model.filter.FilterGroup
import com.bestcallerapp.smswebhook.domain.model.filter.FilterNode
import com.bestcallerapp.smswebhook.domain.model.filter.FilterTreeOps
import com.bestcallerapp.smswebhook.domain.model.payload.PayloadConfig
import com.bestcallerapp.smswebhook.domain.model.payload.PayloadField
import com.bestcallerapp.smswebhook.domain.model.payload.PayloadFormat
import com.bestcallerapp.smswebhook.domain.model.payload.RegexExtractor
import com.bestcallerapp.smswebhook.domain.port.ConfigCodec
import com.bestcallerapp.smswebhook.domain.repository.SettingsRepository
import com.bestcallerapp.smswebhook.domain.repository.WebhookRepository
import com.bestcallerapp.smswebhook.domain.usecase.SaveWebhookResult
import com.bestcallerapp.smswebhook.domain.usecase.SaveWebhookUseCase
import com.bestcallerapp.smswebhook.domain.usecase.TestWebhookUseCase
import com.bestcallerapp.smswebhook.domain.usecase.WebhookTestResult
import com.bestcallerapp.smswebhook.presentation.navigation.WebhookEditorRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Base64
import javax.inject.Inject

enum class EditorStep(val title: String, val section: ConfigSection?) {
    BASICS("Webhook", ConfigSection.BASICS),
    HEADERS("Headers & authentication", ConfigSection.HEADERS),
    FILTERS("Message filters", ConfigSection.FILTERS),
    PAYLOAD("Payload", ConfigSection.PAYLOAD),
    EXTRACTION("Regex extraction", ConfigSection.EXTRACTION),
    DELIVERY("Retry & timeout", ConfigSection.DELIVERY),
    REVIEW("Review", null),
}

data class EditorUiState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val step: EditorStep = EditorStep.BASICS,
    val webhook: Webhook = Webhook(name = "", url = "", timeoutSeconds = 15, maxRetries = 5, retryDelaySeconds = 30),
    val timeoutText: String = "",
    val retriesText: String = "",
    val delayText: String = "",
    val issues: List<ValidationIssue> = emptyList(),
    val saving: Boolean = false,
    val testing: Boolean = false,
    val testResult: WebhookTestResult? = null,
    val sampleSender: String = DEFAULT_SAMPLE_SENDER,
    val sampleBody: String = DEFAULT_SAMPLE_BODY,
    val loadError: String? = null,
    /** Stable UI keys parallel to [Webhook.headers]. */
    val headerKeys: List<Long> = emptyList(),
) {
    fun issuesFor(section: ConfigSection) = issues.filter { it.section == section }
    fun issueFor(target: String) = issues.firstOrNull { it.target == target }?.message

    companion object {
        const val DEFAULT_SAMPLE_SENDER = "VM-HDFCBK"
        const val DEFAULT_SAMPLE_BODY =
            "Rs 5,000.00 credited to A/c XX1234 on 05-10-26 by UPI ref 627815394021. Avl Bal Rs 48,210.55 - HDFC Bank"
    }
}

sealed interface EditorEvent {
    data class Saved(val id: Long) : EditorEvent
    data class Message(val text: String) : EditorEvent
}

@HiltViewModel
class WebhookEditorViewModel @Inject constructor(
    private val repository: WebhookRepository,
    private val settingsRepository: SettingsRepository,
    private val saveWebhook: SaveWebhookUseCase,
    private val testWebhook: TestWebhookUseCase,
    private val validator: WebhookValidator,
    private val previewer: WebhookPreviewer,
    private val configCodec: ConfigCodec,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val webhookId = savedStateHandle.toRoute<WebhookEditorRoute>().webhookId

    private val _state = MutableStateFlow(EditorUiState())
    private var nextHeaderKey = 0L

    private fun newHeaderKeys(count: Int) = List(count) { nextHeaderKey++ }
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private val _events = Channel<EditorEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** Live evaluation of the draft against the sample SMS (filters, extraction, payload). */
    val preview: StateFlow<WebhookPreview?> = _state
        .map { Triple(it.webhook, it.sampleSender, it.sampleBody) }
        .distinctUntilChanged()
        .map { (webhook, sender, body) -> previewer.preview(webhook, sender, body) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            if (!restoreDraft()) load()
            // Persist the draft so it survives process death. Secret values are never written to
            // saved state; they are restored from the database (existing webhooks) or re-entered.
            _state.collect(::saveDraft)
        }
    }

    private fun saveDraft(state: EditorUiState) {
        if (state.loading || state.loadError != null) return
        savedStateHandle[KEY_DRAFT] = configCodec.encode(listOf(state.webhook), includeSecrets = false, exportedAtMillis = 0)
        savedStateHandle[KEY_STEP] = state.step.ordinal
        savedStateHandle[KEY_CREATED_AT] = state.webhook.createdAtMillis
        savedStateHandle[KEY_SAMPLE_SENDER] = state.sampleSender
        savedStateHandle[KEY_SAMPLE_BODY] = state.sampleBody
    }

    private suspend fun restoreDraft(): Boolean {
        val json = savedStateHandle.get<String>(KEY_DRAFT) ?: return false
        val decoded = runCatching { configCodec.decode(json).single() }.getOrNull() ?: return false
        val stored = if (webhookId != 0L) repository.getWebhook(webhookId) else null
        val headers = decoded.headers.map { header ->
            val secret = stored?.headers?.firstOrNull { it.isSecret && it.name == header.name && !it.needsReentry }
            if (header.needsReentry && secret != null) header.copy(value = secret.value, needsReentry = false) else header
        }
        val webhook = decoded.copy(
            id = webhookId,
            headers = headers,
            createdAtMillis = savedStateHandle.get<Long>(KEY_CREATED_AT) ?: 0,
        )
        _state.value = EditorUiState(
            loading = false,
            isNew = webhookId == 0L,
            webhook = webhook,
            step = EditorStep.entries.getOrElse(savedStateHandle.get<Int>(KEY_STEP) ?: 0) { EditorStep.BASICS },
            sampleSender = savedStateHandle.get<String>(KEY_SAMPLE_SENDER) ?: EditorUiState.DEFAULT_SAMPLE_SENDER,
            sampleBody = savedStateHandle.get<String>(KEY_SAMPLE_BODY) ?: EditorUiState.DEFAULT_SAMPLE_BODY,
        ).withNumberTexts()
        return true
    }

    private suspend fun load() {
        if (webhookId == 0L) {
            val settings = settingsRepository.current()
            val draft = Webhook(
                name = "",
                url = "",
                timeoutSeconds = settings.defaultTimeoutSeconds,
                maxRetries = settings.defaultMaxRetries,
                retryDelaySeconds = settings.defaultRetryDelaySeconds,
            )
            _state.value = EditorUiState(loading = false, isNew = true, webhook = draft).withNumberTexts()
        } else {
            val existing = repository.getWebhook(webhookId)
            _state.value = if (existing == null) {
                EditorUiState(loading = false, loadError = "Webhook not found")
            } else {
                EditorUiState(loading = false, isNew = false, webhook = existing).withNumberTexts()
            }
        }
    }

    private fun EditorUiState.withNumberTexts() = copy(
        headerKeys = newHeaderKeys(webhook.headers.size),
        timeoutText = webhook.timeoutSeconds.toString(),
        retriesText = webhook.maxRetries.toString(),
        delayText = webhook.retryDelaySeconds.toString(),
    )

    // region Navigation between steps

    fun goTo(step: EditorStep) = _state.update { it.copy(step = step) }

    fun next() {
        val current = _state.value
        val issues = validateStep(current.step, current.webhook)
        _state.update { it.copy(issues = issues) }
        if (issues.isNotEmpty()) return
        EditorStep.entries.getOrNull(current.step.ordinal + 1)?.let { goTo(it) }
    }

    fun back(): Boolean {
        val previous = EditorStep.entries.getOrNull(_state.value.step.ordinal - 1) ?: return false
        goTo(previous)
        return true
    }

    private fun validateStep(step: EditorStep, webhook: Webhook): List<ValidationIssue> = when (step) {
        EditorStep.BASICS -> validator.validateBasics(webhook.name, webhook.url)
        EditorStep.HEADERS -> validator.validateHeaders(webhook.headers)
        EditorStep.FILTERS -> validator.validateFilters(webhook.scope, webhook.filter)
        EditorStep.PAYLOAD -> validator.validatePayload(webhook.payload)
        EditorStep.EXTRACTION -> validator.validateExtractors(webhook.payload.extractors)
        EditorStep.DELIVERY -> validator.validateDelivery(webhook.timeoutSeconds, webhook.maxRetries, webhook.retryDelaySeconds)
        EditorStep.REVIEW -> validator.validate(webhook)
    }

    // endregion

    private fun edit(transform: (Webhook) -> Webhook) = _state.update { state ->
        val updated = transform(state.webhook)
        // Re-validate the visible step only once the user has seen errors, to avoid nagging while typing.
        val issues = if (state.issues.isEmpty()) emptyList() else validateStep(state.step, updated)
        state.copy(webhook = updated, issues = issues)
    }

    // region Basics
    fun setName(value: String) = edit { it.copy(name = value.take(WebhookLimits.NAME_MAX_LENGTH)) }
    fun setUrl(value: String) = edit { it.copy(url = value.trim().take(WebhookLimits.URL_MAX_LENGTH)) }
    fun setEnabled(value: Boolean) = edit { it.copy(enabled = value) }
    // endregion

    // region Headers
    fun addHeader(name: String = "", value: String = "", secret: Boolean = false) {
        _state.update { it.copy(headerKeys = it.headerKeys + newHeaderKeys(1)) }
        edit { it.copy(headers = it.headers + WebhookHeader(name, value, secret)) }
    }

    fun updateHeader(index: Int, header: WebhookHeader) = edit { webhook ->
        webhook.copy(headers = webhook.headers.mapIndexed { i, h -> if (i == index) header.copy(needsReentry = false) else h })
    }

    fun removeHeader(index: Int) {
        _state.update { it.copy(headerKeys = it.headerKeys.filterIndexed { i, _ -> i != index }) }
        edit { it.copy(headers = it.headers.filterIndexed { i, _ -> i != index }) }
    }

    fun addBasicAuth(username: String, password: String) {
        val token = Base64.getEncoder().encodeToString("${username.trim()}:$password".toByteArray(Charsets.UTF_8))
        upsertAuthorization("Basic $token")
    }

    fun addBearerToken(token: String) = upsertAuthorization("Bearer ${token.trim()}")

    private fun upsertAuthorization(value: String) {
        val headers = _state.value.webhook.headers
        val keep = headers.indices.filterNot { headers[it].name.equals(AUTHORIZATION, ignoreCase = true) }
        _state.update { state -> state.copy(headerKeys = keep.map { state.headerKeys.getOrElse(it) { nextHeaderKey++ } } + newHeaderKeys(1)) }
        edit { webhook -> webhook.copy(headers = keep.map { headers[it] } + WebhookHeader(AUTHORIZATION, value, isSecret = true)) }
    }
    // endregion

    // region Filters
    fun setScope(scope: WebhookScope) = edit { webhook ->
        val filter = if (scope == WebhookScope.MATCHING_RULES && webhook.filter.children.isEmpty()) {
            webhook.filter.copy(children = listOf(FilterCondition()))
        } else {
            webhook.filter
        }
        webhook.copy(scope = scope, filter = filter)
    }

    fun updateFilterNode(id: String, transform: (FilterNode) -> FilterNode) =
        edit { it.copy(filter = FilterTreeOps.update(it.filter, id, transform)) }

    fun addCondition(groupId: String) = edit { it.copy(filter = FilterTreeOps.addChild(it.filter, groupId, FilterCondition())) }

    fun addGroup(groupId: String) = edit {
        val depth = FilterTreeOps.depthOf(it.filter, groupId) ?: return@edit it
        if (depth >= WebhookLimits.MAX_FILTER_DEPTH) return@edit it
        it.copy(filter = FilterTreeOps.addChild(it.filter, groupId, FilterGroup(children = listOf(FilterCondition()))))
    }

    fun removeFilterNode(id: String) = edit { it.copy(filter = FilterTreeOps.remove(it.filter, id)) }
    // endregion

    // region Payload
    private fun editPayload(transform: (PayloadConfig) -> PayloadConfig) = edit { it.copy(payload = transform(it.payload)) }

    /** Switching format starts from that format's default template (a raw-text template is not valid JSON). */
    fun setFormat(format: PayloadFormat) = editPayload { payload ->
        if (payload.format == format) payload else payload.copy(format = format, template = PayloadConfig.defaultTemplateFor(format))
    }

    fun updateField(field: PayloadField) = editPayload { payload ->
        payload.copy(fields = payload.fields.map { if (it.key == field.key) field else it })
    }

    fun setTemplate(template: String) = editPayload { it.copy(template = template) }
    // endregion

    // region Extraction
    fun addExtractor() = editPayload { it.copy(extractors = it.extractors + RegexExtractor(name = "", pattern = "")) }

    fun updateExtractor(index: Int, extractor: RegexExtractor) = editPayload { payload ->
        payload.copy(extractors = payload.extractors.mapIndexed { i, e -> if (i == index) extractor else e })
    }

    fun removeExtractor(index: Int) = editPayload { payload ->
        payload.copy(extractors = payload.extractors.filterIndexed { i, _ -> i != index })
    }
    // endregion

    // region Delivery
    fun setTimeoutText(text: String) {
        _state.update { it.copy(timeoutText = text) }
        edit { it.copy(timeoutSeconds = text.toIntOrNull() ?: -1) }
    }

    fun setRetriesText(text: String) {
        _state.update { it.copy(retriesText = text) }
        edit { it.copy(maxRetries = text.toIntOrNull() ?: -1) }
    }

    fun setDelayText(text: String) {
        _state.update { it.copy(delayText = text) }
        edit { it.copy(retryDelaySeconds = text.toIntOrNull() ?: -1) }
    }
    // endregion

    // region Samples & tests
    fun setSampleSender(value: String) = _state.update { it.copy(sampleSender = value) }
    fun setSampleBody(value: String) = _state.update { it.copy(sampleBody = value) }

    fun testRegex(pattern: String, caseSensitive: Boolean, sample: String): RegexTestResult =
        previewer.testRegex(pattern, caseSensitive, sample)

    fun sendTest() {
        val current = _state.value
        if (current.testing) return
        val issues = validator.validateBasics(current.webhook.name.ifBlank { "Test" }, current.webhook.url) +
            validator.validateHeaders(current.webhook.headers)
        if (issues.isNotEmpty()) {
            _state.update { it.copy(issues = issues) }
            viewModelScope.launch { _events.send(EditorEvent.Message(issues.first().message)) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(testing = true) }
            val result = testWebhook(current.webhook, current.sampleSender, current.sampleBody)
            _state.update { it.copy(testing = false, testResult = result) }
        }
    }

    fun dismissTestResult() = _state.update { it.copy(testResult = null) }
    // endregion

    fun save() {
        val current = _state.value
        if (current.saving) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            when (val result = saveWebhook(current.webhook)) {
                is SaveWebhookResult.Saved -> {
                    _state.update { it.copy(saving = false) }
                    _events.send(EditorEvent.Saved(result.id))
                }
                is SaveWebhookResult.Invalid -> {
                    val firstStep = EditorStep.entries.firstOrNull { step -> result.issues.any { it.section == step.section } }
                        ?: current.step
                    _state.update { it.copy(saving = false, issues = result.issues, step = firstStep) }
                    _events.send(EditorEvent.Message("Please fix the highlighted problems"))
                }
            }
        }
    }

    private companion object {
        const val AUTHORIZATION = "Authorization"
        const val KEY_DRAFT = "editor_draft"
        const val KEY_STEP = "editor_step"
        const val KEY_CREATED_AT = "editor_created_at"
        const val KEY_SAMPLE_SENDER = "editor_sample_sender"
        const val KEY_SAMPLE_BODY = "editor_sample_body"
    }
}
