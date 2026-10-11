package com.bestcallerapp.smswebhook.presentation.message_detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.bestcallerapp.smswebhook.domain.model.Delivery
import com.bestcallerapp.smswebhook.domain.model.DeliveryAttempt
import com.bestcallerapp.smswebhook.domain.model.SmsMessage
import com.bestcallerapp.smswebhook.domain.repository.DeliveryLogRepository
import com.bestcallerapp.smswebhook.domain.repository.DeliveryRepository
import com.bestcallerapp.smswebhook.domain.repository.SmsRepository
import com.bestcallerapp.smswebhook.domain.usecase.RetryDeliveryUseCase
import com.bestcallerapp.smswebhook.presentation.navigation.MessageDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MessageDetailUiState(
    val loading: Boolean = true,
    val message: SmsMessage? = null,
    val deliveries: List<Delivery> = emptyList(),
    val attempts: List<DeliveryAttempt> = emptyList(),
)

@HiltViewModel
class MessageDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val smsRepository: SmsRepository,
    deliveryRepository: DeliveryRepository,
    logRepository: DeliveryLogRepository,
    private val retryDelivery: RetryDeliveryUseCase,
) : ViewModel() {

    private val messageId = savedStateHandle.toRoute<MessageDetailRoute>().messageId

    private val _events = Channel<String>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val uiState: StateFlow<MessageDetailUiState> = combine(
        smsRepository.observeMessage(messageId),
        deliveryRepository.observeForMessage(messageId),
        logRepository.observeForMessage(messageId),
    ) { message, deliveries, attempts -> MessageDetailUiState(false, message, deliveries, attempts) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MessageDetailUiState())

    fun retry(deliveryId: Long) {
        viewModelScope.launch {
            _events.send(if (retryDelivery.retry(deliveryId)) "Retry queued" else "Delivery can no longer be retried")
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            smsRepository.deleteMessage(messageId)
            onDeleted()
        }
    }
}
