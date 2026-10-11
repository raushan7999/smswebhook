package com.bestcallerapp.smswebhook.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.bestcallerapp.smswebhook.data.serialization.enumOrNull
import com.bestcallerapp.smswebhook.domain.model.AppSettings
import com.bestcallerapp.smswebhook.domain.model.DeleteSmsPolicy
import com.bestcallerapp.smswebhook.domain.model.RetentionPolicy
import com.bestcallerapp.smswebhook.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<AppSettings> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map(::toSettings)

    override suspend fun current(): AppSettings = settings.first()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs ->
            val updated = transform(toSettings(prefs))
            prefs[Keys.LISTENER_ENABLED] = updated.listenerEnabled
            when (val retention = updated.retention) {
                RetentionPolicy.Forever -> {
                    prefs[Keys.RETENTION_MODE] = MODE_FOREVER
                }
                is RetentionPolicy.ByAge -> {
                    prefs[Keys.RETENTION_MODE] = MODE_AGE
                    prefs[Keys.RETENTION_VALUE] = retention.days
                }
                is RetentionPolicy.ByCount -> {
                    prefs[Keys.RETENTION_MODE] = MODE_COUNT
                    prefs[Keys.RETENTION_VALUE] = retention.maxMessages
                }
            }
            prefs[Keys.DELETE_POLICY] = updated.deleteSmsPolicy.name
            prefs[Keys.DEFAULT_RETRIES] = updated.defaultMaxRetries
            prefs[Keys.DEFAULT_RETRY_DELAY] = updated.defaultRetryDelaySeconds
            prefs[Keys.DEFAULT_TIMEOUT] = updated.defaultTimeoutSeconds
            prefs[Keys.WIFI_ONLY] = updated.wifiOnly
            prefs[Keys.KEEP_LOGS] = updated.keepDeliveryLogs
            prefs[Keys.LOG_RETENTION_DAYS] = updated.logRetentionDays
        }
    }

    private fun toSettings(prefs: Preferences): AppSettings {
        val defaults = AppSettings()
        val retentionValue = prefs[Keys.RETENTION_VALUE]
        val retention = when (prefs[Keys.RETENTION_MODE]) {
            MODE_FOREVER -> RetentionPolicy.Forever
            MODE_AGE -> retentionValue?.let { RetentionPolicy.ByAge(it) } ?: defaults.retention
            MODE_COUNT -> retentionValue?.let { RetentionPolicy.ByCount(it) } ?: defaults.retention
            else -> defaults.retention
        }
        return AppSettings(
            listenerEnabled = prefs[Keys.LISTENER_ENABLED] ?: defaults.listenerEnabled,
            retention = retention,
            deleteSmsPolicy = prefs[Keys.DELETE_POLICY]?.let { enumOrNull<DeleteSmsPolicy>(it) } ?: defaults.deleteSmsPolicy,
            defaultMaxRetries = prefs[Keys.DEFAULT_RETRIES] ?: defaults.defaultMaxRetries,
            defaultRetryDelaySeconds = prefs[Keys.DEFAULT_RETRY_DELAY] ?: defaults.defaultRetryDelaySeconds,
            defaultTimeoutSeconds = prefs[Keys.DEFAULT_TIMEOUT] ?: defaults.defaultTimeoutSeconds,
            wifiOnly = prefs[Keys.WIFI_ONLY] ?: defaults.wifiOnly,
            keepDeliveryLogs = prefs[Keys.KEEP_LOGS] ?: defaults.keepDeliveryLogs,
            logRetentionDays = prefs[Keys.LOG_RETENTION_DAYS] ?: defaults.logRetentionDays,
        )
    }

    private object Keys {
        val LISTENER_ENABLED = booleanPreferencesKey("listener_enabled")
        val RETENTION_MODE = stringPreferencesKey("retention_mode")
        val RETENTION_VALUE = intPreferencesKey("retention_value")
        val DELETE_POLICY = stringPreferencesKey("delete_sms_policy")
        val DEFAULT_RETRIES = intPreferencesKey("default_max_retries")
        val DEFAULT_RETRY_DELAY = intPreferencesKey("default_retry_delay_seconds")
        val DEFAULT_TIMEOUT = intPreferencesKey("default_timeout_seconds")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val KEEP_LOGS = booleanPreferencesKey("keep_delivery_logs")
        val LOG_RETENTION_DAYS = intPreferencesKey("log_retention_days")
    }

    private companion object {
        const val MODE_FOREVER = "forever"
        const val MODE_AGE = "age"
        const val MODE_COUNT = "count"
    }
}
