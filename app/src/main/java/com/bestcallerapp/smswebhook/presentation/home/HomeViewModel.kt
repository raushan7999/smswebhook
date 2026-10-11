package com.bestcallerapp.smswebhook.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bestcallerapp.smswebhook.core.constants.AppConstants
import com.bestcallerapp.smswebhook.core.network.ConnectivityObserver
import com.bestcallerapp.smswebhook.domain.model.DashboardStats
import com.bestcallerapp.smswebhook.domain.model.MessageSummary
import com.bestcallerapp.smswebhook.domain.port.PowerStatusProvider
import com.bestcallerapp.smswebhook.domain.port.SmsListenerController
import com.bestcallerapp.smswebhook.domain.repository.DeliveryRepository
import com.bestcallerapp.smswebhook.domain.repository.SettingsRepository
import com.bestcallerapp.smswebhook.domain.repository.SmsRepository
import com.bestcallerapp.smswebhook.domain.usecase.SetForwardingEnabledUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SystemState(
    val hasSmsPermission: Boolean = true,
    val batteryUnrestricted: Boolean = true,
)

data class HomeUiState(
    val loading: Boolean = true,
    val stats: DashboardStats = DashboardStats(),
    val recent: List<MessageSummary> = emptyList(),
    val forwardingEnabled: Boolean = true,
    val online: Boolean = true,
    val system: SystemState = SystemState(),
) {
    /** The listener is truly active only when enabled by the user *and* permitted by the system. */
    val listenerActive: Boolean get() = forwardingEnabled && system.hasSmsPermission
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    smsRepository: SmsRepository,
    deliveryRepository: DeliveryRepository,
    settingsRepository: SettingsRepository,
    connectivityObserver: ConnectivityObserver,
    private val listenerController: SmsListenerController,
    private val powerStatus: PowerStatusProvider,
    private val setForwardingEnabled: SetForwardingEnabledUseCase,
) : ViewModel() {

    private val systemState = MutableStateFlow(readSystemState())

    val uiState: StateFlow<HomeUiState> = combine(
        deliveryRepository.observeDashboardStats(),
        smsRepository.observeRecent(AppConstants.RECENT_MESSAGES_LIMIT),
        settingsRepository.settings.map { it.listenerEnabled },
        connectivityObserver.isOnline,
        systemState,
    ) { stats, recent, enabled, online, system ->
        HomeUiState(false, stats, recent, enabled, online, system)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState())

    /** Called when the screen resumes: permission and battery state can change in system settings. */
    fun refreshSystemState() {
        systemState.value = readSystemState()
    }

    fun setForwarding(enabled: Boolean) {
        viewModelScope.launch { setForwardingEnabled(enabled) }
    }

    private fun readSystemState() = SystemState(
        hasSmsPermission = listenerController.hasReceivePermission(),
        batteryUnrestricted = powerStatus.isIgnoringBatteryOptimizations(),
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
