package com.bestcallerapp.smswebhook.presentation.webhook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bestcallerapp.smswebhook.core.utils.TimeProvider
import com.bestcallerapp.smswebhook.domain.model.WebhookOverview
import com.bestcallerapp.smswebhook.domain.repository.WebhookRepository
import com.bestcallerapp.smswebhook.domain.usecase.DuplicateWebhookUseCase
import com.bestcallerapp.smswebhook.domain.usecase.TestWebhookUseCase
import com.bestcallerapp.smswebhook.domain.usecase.WebhookTestResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Shared test-run state for screens that offer "Test webhook". */
data class TestState(val runningForId: Long? = null, val result: WebhookTestResult? = null)

@HiltViewModel
class WebhooksViewModel @Inject constructor(
    private val repository: WebhookRepository,
    private val duplicateWebhook: DuplicateWebhookUseCase,
    private val testWebhook: TestWebhookUseCase,
    private val time: TimeProvider,
) : ViewModel() {

    val webhooks: StateFlow<List<WebhookOverview>?> = repository.observeOverviews()
        .map<List<WebhookOverview>, List<WebhookOverview>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _test = MutableStateFlow(TestState())
    val test: StateFlow<TestState> = _test.asStateFlow()

    private val _events = Channel<String>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun setEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(id, enabled, time.nowMillis()) }
    }

    fun duplicate(id: Long) {
        viewModelScope.launch {
            val copy = duplicateWebhook(id)
            _events.send(if (copy != null) "Copy created (disabled)" else "Could not duplicate: re-enter secret headers first")
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            repository.delete(id)
            _events.send("Webhook deleted")
        }
    }

    fun test(id: Long) {
        if (_test.value.runningForId != null) return
        viewModelScope.launch {
            _test.value = TestState(runningForId = id)
            val webhook = repository.getWebhook(id)
            _test.value = if (webhook == null) TestState() else TestState(result = testWebhook(webhook))
        }
    }

    fun dismissTestResult() {
        _test.value = TestState()
    }
}
