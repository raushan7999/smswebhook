package com.bestcallerapp.smswebhook.presentation.webhook_detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.bestcallerapp.smswebhook.core.utils.TimeProvider
import com.bestcallerapp.smswebhook.domain.model.Delivery
import com.bestcallerapp.smswebhook.domain.model.Webhook
import com.bestcallerapp.smswebhook.domain.repository.DeliveryRepository
import com.bestcallerapp.smswebhook.domain.repository.WebhookRepository
import com.bestcallerapp.smswebhook.domain.usecase.DuplicateWebhookUseCase
import com.bestcallerapp.smswebhook.domain.usecase.RetryDeliveryUseCase
import com.bestcallerapp.smswebhook.domain.usecase.TestWebhookUseCase
import com.bestcallerapp.smswebhook.presentation.navigation.WebhookDetailRoute
import com.bestcallerapp.smswebhook.presentation.webhook.TestState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface WebhookDetailState {
    data object Loading : WebhookDetailState
    data object Missing : WebhookDetailState
    data class Loaded(val webhook: Webhook, val sent: Int, val failed: Int, val pending: Int) : WebhookDetailState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WebhookDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: WebhookRepository,
    deliveryRepository: DeliveryRepository,
    private val retryDelivery: RetryDeliveryUseCase,
    private val duplicateWebhook: DuplicateWebhookUseCase,
    private val testWebhook: TestWebhookUseCase,
    private val time: TimeProvider,
) : ViewModel() {

    val webhookId = savedStateHandle.toRoute<WebhookDetailRoute>().webhookId

    val state: StateFlow<WebhookDetailState> = combine(
        repository.observeWebhook(webhookId),
        repository.observeOverviews().map { list -> list.firstOrNull { it.id == webhookId } },
    ) { webhook, overview ->
        if (webhook == null) {
            WebhookDetailState.Missing
        } else {
            WebhookDetailState.Loaded(webhook, overview?.sentCount ?: 0, overview?.failedCount ?: 0, overview?.pendingCount ?: 0)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WebhookDetailState.Loading)

    private val _failedOnly = MutableStateFlow(false)
    val failedOnly: StateFlow<Boolean> = _failedOnly.asStateFlow()

    val deliveries: Flow<PagingData<Delivery>> = _failedOnly
        .flatMapLatest { deliveryRepository.pagedForWebhook(webhookId, it) }
        .cachedIn(viewModelScope)

    private val _test = MutableStateFlow(TestState())
    val test: StateFlow<TestState> = _test.asStateFlow()

    private val _events = Channel<String>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun setFailedOnly(value: Boolean) {
        _failedOnly.value = value
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(webhookId, enabled, time.nowMillis()) }
    }

    fun retry(deliveryId: Long) {
        viewModelScope.launch {
            _events.send(if (retryDelivery.retry(deliveryId)) "Retry queued" else "Delivery can no longer be retried")
        }
    }

    fun retryAllFailed() {
        viewModelScope.launch {
            val count = retryDelivery.retryAllFailed(webhookId)
            _events.send(if (count == 0) "No failed deliveries" else "$count deliver${if (count == 1) "y" else "ies"} queued for retry")
        }
    }

    fun duplicate() {
        viewModelScope.launch {
            _events.send(if (duplicateWebhook(webhookId) != null) "Copy created (disabled)" else "Could not duplicate")
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.delete(webhookId)
            onDeleted()
        }
    }

    fun test() {
        val loaded = state.value as? WebhookDetailState.Loaded ?: return
        if (_test.value.runningForId != null) return
        viewModelScope.launch {
            _test.value = TestState(runningForId = webhookId)
            _test.value = TestState(result = testWebhook(loaded.webhook))
        }
    }

    fun dismissTest() {
        _test.value = TestState()
    }
}
