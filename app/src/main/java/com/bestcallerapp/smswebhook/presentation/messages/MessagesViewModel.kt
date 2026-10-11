package com.bestcallerapp.smswebhook.presentation.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.bestcallerapp.smswebhook.domain.model.MessageQuery
import com.bestcallerapp.smswebhook.domain.model.MessageStatus
import com.bestcallerapp.smswebhook.domain.model.MessageSummary
import com.bestcallerapp.smswebhook.domain.repository.SmsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

enum class MessageFilter(val label: String, val statuses: Set<MessageStatus>) {
    ALL("All", emptySet()),
    DELIVERED("Delivered", setOf(MessageStatus.SENT)),
    FAILED("Failed", setOf(MessageStatus.FAILED)),
    PENDING("Pending", setOf(MessageStatus.RECEIVED, MessageStatus.PENDING, MessageStatus.PROCESSING, MessageStatus.RETRYING)),
    FILTERED("Filtered", setOf(MessageStatus.FILTERED)),
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class MessagesViewModel @Inject constructor(
    smsRepository: SmsRepository,
) : ViewModel() {

    private val _search = MutableStateFlow("")
    val search: StateFlow<String> = _search.asStateFlow()

    private val _filter = MutableStateFlow(MessageFilter.ALL)
    val filter: StateFlow<MessageFilter> = _filter.asStateFlow()

    val messages: Flow<PagingData<MessageSummary>> =
        combine(_search.debounce(SEARCH_DEBOUNCE_MS), _filter) { search, filter -> MessageQuery(search, filter.statuses) }
            .distinctUntilChanged()
            .flatMapLatest { smsRepository.pagedMessages(it) }
            .cachedIn(viewModelScope)

    fun onSearchChange(value: String) {
        _search.value = value
    }

    fun onFilterChange(value: MessageFilter) {
        _filter.value = value
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}
