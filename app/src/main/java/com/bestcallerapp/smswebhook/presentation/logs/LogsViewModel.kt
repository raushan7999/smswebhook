package com.bestcallerapp.smswebhook.presentation.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.bestcallerapp.smswebhook.domain.model.DeliveryAttempt
import com.bestcallerapp.smswebhook.domain.model.LogFilter
import com.bestcallerapp.smswebhook.domain.repository.DeliveryLogRepository
import com.bestcallerapp.smswebhook.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LogsViewModel @Inject constructor(
    private val logRepository: DeliveryLogRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _filter = MutableStateFlow(LogFilter.ALL)
    val filter: StateFlow<LogFilter> = _filter.asStateFlow()

    val loggingEnabled: StateFlow<Boolean> = settingsRepository.settings.map { it.keepDeliveryLogs }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val attempts: Flow<PagingData<DeliveryAttempt>> = _filter
        .flatMapLatest { logRepository.paged(it) }
        .cachedIn(viewModelScope)

    fun setFilter(value: LogFilter) {
        _filter.value = value
    }

    fun clear() {
        viewModelScope.launch { logRepository.clear() }
    }
}
