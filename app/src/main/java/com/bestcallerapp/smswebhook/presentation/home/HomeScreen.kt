package com.bestcallerapp.smswebhook.presentation.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bestcallerapp.smswebhook.domain.model.MessageSummary
import com.bestcallerapp.smswebhook.presentation.components.LoadingState
import com.bestcallerapp.smswebhook.presentation.components.Panel
import com.bestcallerapp.smswebhook.presentation.components.ScreenScaffold
import com.bestcallerapp.smswebhook.presentation.components.SectionHeader
import com.bestcallerapp.smswebhook.presentation.components.Spacing
import com.bestcallerapp.smswebhook.presentation.components.StatusBadge
import com.bestcallerapp.smswebhook.presentation.components.StatusDot
import com.bestcallerapp.smswebhook.presentation.components.StatusTone
import com.bestcallerapp.smswebhook.presentation.components.SwitchRow
import com.bestcallerapp.smswebhook.presentation.components.ThinDivider
import com.bestcallerapp.smswebhook.presentation.components.contentColor
import com.bestcallerapp.smswebhook.presentation.components.formatCount
import com.bestcallerapp.smswebhook.presentation.components.label
import com.bestcallerapp.smswebhook.presentation.components.relativeTime

@Composable
fun HomeScreen(
    onOpenMessage: (Long) -> Unit,
    onOpenMessages: () -> Unit,
    onOpenWebhooks: () -> Unit,
    onAddWebhook: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleResumeEffect(Unit) {
        viewModel.refreshSystemState()
        onPauseOrDispose { }
    }

    ScreenScaffold(title = "SMS Webhook", topLevel = true) { padding ->
        if (state.loading) {
            LoadingState(Modifier.padding(padding))
            return@ScreenScaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (!state.system.hasSmsPermission) {
                item { SmsPermissionCard(onResult = viewModel::refreshSystemState) }
            }
            item { ServiceStatusPanel(state, onToggle = viewModel::setForwarding) }
            item { StatsGrid(state, onOpenWebhooks) }
            if (state.stats.totalWebhooks == 0) {
                item {
                    Panel {
                        Text("No webhooks yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Add a webhook to start forwarding SMS. Incoming messages are kept locally until then.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = onAddWebhook, modifier = Modifier.padding(top = Spacing.xs)) { Text("Add webhook") }
                    }
                }
            }
            if (!state.system.batteryUnrestricted) {
                item {
                    Panel {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            Icon(Icons.Outlined.BatteryAlert, contentDescription = null, tint = StatusTone.WARNING.contentColor())
                            Column(Modifier.weight(1f)) {
                                Text("Battery optimisation is on", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Some devices delay background delivery to save battery. If forwarding is slow, " +
                                        "set SMS Webhook to \"Unrestricted\" / \"Don't optimise\".",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        TextButton(onClick = { context.openBatteryOptimizationSettings() }) { Text("Battery settings") }
                    }
                }
            }
            item {
                SectionHeader("Recent messages") {
                    TextButton(onClick = onOpenMessages) { Text("View all") }
                }
            }
            if (state.recent.isEmpty()) {
                item {
                    Panel {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            Icon(Icons.Outlined.Inbox, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                            Text(
                                "No messages received yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                item {
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        state.recent.forEachIndexed { index, message ->
                            if (index > 0) ThinDivider()
                            RecentMessageRow(message, onClick = { onOpenMessage(message.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceStatusPanel(state: HomeUiState, onToggle: (Boolean) -> Unit) {
    Panel {
        val listenerTone = if (state.listenerActive) StatusTone.SUCCESS else StatusTone.ERROR
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StatusDot(listenerTone.contentColor())
            Text(
                if (state.listenerActive) "Service active" else "Service inactive",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        StatusLine("SMS listener", if (state.listenerActive) "Active" else if (!state.system.hasSmsPermission) "No permission" else "Stopped", listenerTone)
        StatusLine(
            "Internet",
            if (state.online) "Connected" else "Offline — deliveries will resume automatically",
            if (state.online) StatusTone.SUCCESS else StatusTone.WARNING,
        )
        ThinDivider(Modifier.padding(vertical = Spacing.sm))
        SwitchRow(
            title = "Forward incoming SMS",
            subtitle = if (state.forwardingEnabled) "New messages are stored and forwarded" else "Incoming SMS are ignored",
            checked = state.forwardingEnabled,
            onCheckedChange = onToggle,
        )
    }
}

@Composable
private fun StatusLine(label: String, value: String, tone: StatusTone) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp).semantics { contentDescription = "$label: $value" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = tone.contentColor())
    }
}

@Composable
private fun StatsGrid(state: HomeUiState, onOpenWebhooks: () -> Unit) {
    val stats = state.stats
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StatTile("Messages", formatCount(stats.totalMessages), Modifier.weight(1f))
            StatTile("Delivered", formatCount(stats.sentDeliveries), Modifier.weight(1f), StatusTone.SUCCESS)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StatTile("Pending", formatCount(stats.pendingDeliveries), Modifier.weight(1f), if (stats.pendingDeliveries > 0) StatusTone.WARNING else null)
            StatTile("Failed", formatCount(stats.failedDeliveries), Modifier.weight(1f), if (stats.failedDeliveries > 0) StatusTone.ERROR else null)
            StatTile(
                "Webhooks",
                "${stats.enabledWebhooks}/${stats.totalWebhooks}",
                Modifier.weight(1f),
                onClick = onOpenWebhooks,
                description = "${stats.enabledWebhooks} of ${stats.totalWebhooks} webhooks enabled",
            )
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    tone: StatusTone? = null,
    onClick: (() -> Unit)? = null,
    description: String = "$label: $value",
) {
    OutlinedCard(modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description }) {
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(Spacing.md),
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                color = tone?.contentColor() ?: MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun RecentMessageRow(message: MessageSummary, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(message.sender, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(relativeTime(message.receivedAtMillis), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            message.preview,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        StatusBadge(message.status.label(), Modifier.padding(top = Spacing.xs))
    }
}
