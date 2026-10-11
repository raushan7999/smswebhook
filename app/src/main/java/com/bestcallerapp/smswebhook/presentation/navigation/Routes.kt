package com.bestcallerapp.smswebhook.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Webhook
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute

@Serializable data object MessagesRoute

@Serializable data class MessageDetailRoute(val messageId: Long)

@Serializable data object WebhooksRoute

@Serializable data class WebhookDetailRoute(val webhookId: Long)

/** [webhookId] 0 creates a new webhook. */
@Serializable data class WebhookEditorRoute(val webhookId: Long = 0)

@Serializable data object LogsRoute

@Serializable data object SettingsRoute

enum class TopLevelDestination(val route: Any, val label: String, val icon: ImageVector) {
    HOME(HomeRoute, "Home", Icons.Outlined.Dashboard),
    MESSAGES(MessagesRoute, "Messages", Icons.Outlined.Sms),
    WEBHOOKS(WebhooksRoute, "Webhooks", Icons.Outlined.Webhook),
    LOGS(LogsRoute, "Logs", Icons.AutoMirrored.Outlined.ListAlt),
    SETTINGS(SettingsRoute, "Settings", Icons.Outlined.Settings),
}
