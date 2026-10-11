package com.bestcallerapp.smswebhook.domain.model

sealed interface RetentionPolicy {
    data object Forever : RetentionPolicy
    data class ByAge(val days: Int) : RetentionPolicy
    data class ByCount(val maxMessages: Int) : RetentionPolicy

    companion object {
        val AGE_PRESETS = listOf(7, 30, 365)
        val COUNT_PRESETS = listOf(100, 1_000, 10_000, 100_000)
    }
}

enum class DeleteSmsPolicy {
    DISABLED,
    AFTER_ALL_SUCCEEDED,
    AFTER_FIRST_SUCCESS,
}

data class AppSettings(
    val listenerEnabled: Boolean = true,
    val retention: RetentionPolicy = RetentionPolicy.ByAge(30),
    val deleteSmsPolicy: DeleteSmsPolicy = DeleteSmsPolicy.DISABLED,
    val defaultMaxRetries: Int = 5,
    val defaultRetryDelaySeconds: Int = 30,
    val defaultTimeoutSeconds: Int = 15,
    val wifiOnly: Boolean = false,
    val keepDeliveryLogs: Boolean = true,
    val logRetentionDays: Int = 30,
)
