package com.bestcallerapp.smswebhook.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bestcallerapp.smswebhook.domain.model.AppSettings
import com.bestcallerapp.smswebhook.domain.model.DeleteSmsPolicy
import com.bestcallerapp.smswebhook.domain.model.RetentionPolicy
import com.bestcallerapp.smswebhook.domain.port.ConfigFormatException
import com.bestcallerapp.smswebhook.domain.port.PowerStatusProvider
import com.bestcallerapp.smswebhook.domain.port.SmsDeleter
import com.bestcallerapp.smswebhook.domain.port.WorkScheduler
import com.bestcallerapp.smswebhook.domain.repository.DeliveryLogRepository
import com.bestcallerapp.smswebhook.domain.repository.SettingsRepository
import com.bestcallerapp.smswebhook.domain.repository.SmsRepository
import com.bestcallerapp.smswebhook.domain.usecase.ExportConfigUseCase
import com.bestcallerapp.smswebhook.domain.usecase.ImportConfigUseCase
import com.bestcallerapp.smswebhook.domain.usecase.SetForwardingEnabledUseCase
import com.bestcallerapp.smswebhook.domain.usecase.SetWifiOnlyUseCase
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

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val smsRepository: SmsRepository,
    private val logRepository: DeliveryLogRepository,
    private val scheduler: WorkScheduler,
    private val setForwardingEnabled: SetForwardingEnabledUseCase,
    private val setWifiOnlyUseCase: SetWifiOnlyUseCase,
    private val exportConfig: ExportConfigUseCase,
    private val importConfig: ImportConfigUseCase,
    private val powerStatus: PowerStatusProvider,
    smsDeleter: SmsDeleter,
) : ViewModel() {

    val settings: StateFlow<AppSettings?> = settingsRepository.settings.map<AppSettings, AppSettings?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val smsDeletionSupported: Boolean = smsDeleter.isSupported()

    private val _batteryUnrestricted = MutableStateFlow(powerStatus.isIgnoringBatteryOptimizations())
    val batteryUnrestricted: StateFlow<Boolean> = _batteryUnrestricted.asStateFlow()

    private val _events = Channel<String>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun refresh() {
        _batteryUnrestricted.value = powerStatus.isIgnoringBatteryOptimizations()
    }

    fun setListener(enabled: Boolean) = viewModelScope.launch { setForwardingEnabled(enabled) }

    fun setRetention(policy: RetentionPolicy) = viewModelScope.launch {
        settingsRepository.update { it.copy(retention = policy) }
        scheduler.scheduleCleanupNow()
    }

    fun setDeletePolicy(policy: DeleteSmsPolicy) = update { it.copy(deleteSmsPolicy = policy) }
    fun setWifiOnly(value: Boolean) = viewModelScope.launch { setWifiOnlyUseCase(value) }
    fun setDefaultRetries(value: Int) = update { it.copy(defaultMaxRetries = value) }
    fun setDefaultRetryDelay(value: Int) = update { it.copy(defaultRetryDelaySeconds = value) }
    fun setDefaultTimeout(value: Int) = update { it.copy(defaultTimeoutSeconds = value) }

    fun setKeepLogs(value: Boolean) = viewModelScope.launch {
        settingsRepository.update { it.copy(keepDeliveryLogs = value) }
        if (!value) scheduler.scheduleCleanupNow()
    }

    fun setLogRetention(days: Int) = viewModelScope.launch {
        settingsRepository.update { it.copy(logRetentionDays = days) }
        scheduler.scheduleCleanupNow()
    }

    fun clearHistory() = viewModelScope.launch {
        smsRepository.clearInactive()
        _events.send("History cleared (messages still being delivered were kept)")
    }

    fun clearLogs() = viewModelScope.launch {
        logRepository.clear()
        _events.send("Delivery logs cleared")
    }

    suspend fun export(includeSecrets: Boolean): String = exportConfig(includeSecrets)

    fun onExported(success: Boolean) = viewModelScope.launch {
        _events.send(if (success) "Configuration exported" else "Export failed")
    }

    fun import(text: String?) = viewModelScope.launch {
        if (text == null) {
            _events.send("Could not read the file")
            return@launch
        }
        val message = try {
            val report = importConfig(text)
            buildString {
                append("Imported ${report.imported} webhook(s), disabled for review.")
                if (report.needSecrets > 0) append(" ${report.needSecrets} need secret headers re-entered.")
                if (report.skipped.isNotEmpty()) append(" Skipped ${report.skipped.size}: ${report.skipped.first()}")
            }
        } catch (e: ConfigFormatException) {
            e.message ?: "Invalid file"
        }
        _events.send(message)
    }

    private fun update(transform: (AppSettings) -> AppSettings) = viewModelScope.launch { settingsRepository.update(transform) }
}
