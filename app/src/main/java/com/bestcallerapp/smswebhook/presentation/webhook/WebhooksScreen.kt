package com.bestcallerapp.smswebhook.presentation.webhook

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Webhook
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bestcallerapp.smswebhook.domain.model.WebhookOverview
import com.bestcallerapp.smswebhook.domain.model.WebhookScope
import com.bestcallerapp.smswebhook.presentation.components.ConfirmDialog
import com.bestcallerapp.smswebhook.presentation.components.EmptyState
import com.bestcallerapp.smswebhook.presentation.components.LoadingState
import com.bestcallerapp.smswebhook.presentation.components.ScreenScaffold
import com.bestcallerapp.smswebhook.presentation.components.Spacing
import com.bestcallerapp.smswebhook.presentation.components.StatusBadge
import com.bestcallerapp.smswebhook.presentation.components.StatusLabel
import com.bestcallerapp.smswebhook.presentation.components.StatusTone
import com.bestcallerapp.smswebhook.presentation.components.TestResultDialog
import com.bestcallerapp.smswebhook.presentation.components.contentColor
import com.bestcallerapp.smswebhook.presentation.components.formatCount
import com.bestcallerapp.smswebhook.presentation.theme.MonospaceStyle

@Composable
fun WebhooksScreen(
    onOpenWebhook: (Long) -> Unit,
    onAddWebhook: () -> Unit,
    onEditWebhook: (Long) -> Unit,
    viewModel: WebhooksViewModel = hiltViewModel(),
) {
    val webhooks by viewModel.webhooks.collectAsStateWithLifecycle()
    val test by viewModel.test.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<WebhookOverview?>(null) }

    LaunchedEffect(Unit) { viewModel.events.collect { snackbar.showSnackbar(it) } }

    ScreenScaffold(
        title = "Webhooks",
        topLevel = true,
        snackbarHostState = snackbar,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddWebhook,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add webhook") },
            )
        },
    ) { padding ->
        val list = webhooks
        when {
            list == null -> LoadingState(Modifier.padding(padding))
            list.isEmpty() -> EmptyState(
                icon = Icons.Outlined.Webhook,
                title = "No webhooks configured",
                message = "A webhook is an HTTPS endpoint that receives your SMS as JSON, text or form data.",
                modifier = Modifier.padding(padding),
                action = { Button(onClick = onAddWebhook) { Text("Add webhook") } },
            )
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                items(list, key = { it.id }) { webhook ->
                    WebhookCard(
                        webhook = webhook,
                        testing = test.runningForId == webhook.id,
                        onOpen = { onOpenWebhook(webhook.id) },
                        onEdit = { onEditWebhook(webhook.id) },
                        onTest = { viewModel.test(webhook.id) },
                        onToggle = { viewModel.setEnabled(webhook.id, it) },
                        onDuplicate = { viewModel.duplicate(webhook.id) },
                        onDelete = { pendingDelete = webhook },
                    )
                }
            }
        }
    }

    test.result?.let { TestResultDialog(it, onDismiss = viewModel::dismissTestResult) }
    pendingDelete?.let { webhook ->
        ConfirmDialog(
            title = "Delete \"${webhook.name}\"?",
            message = "Its configuration and delivery history will be removed. Pending deliveries to it are cancelled.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { viewModel.delete(webhook.id) },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
fun WebhookCard(
    webhook: WebhookOverview,
    testing: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onTest: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.clickable(onClick = onOpen).padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(webhook.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        webhook.url,
                        style = MonospaceStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Switch(
                    checked = webhook.enabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.semantics {
                        contentDescription = "Enable ${webhook.name}"
                        stateDescription = if (webhook.enabled) "Enabled" else "Disabled"
                    },
                )
            }
            Row(
                Modifier.padding(top = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusBadge(
                    if (webhook.enabled) StatusLabel("Enabled", StatusTone.SUCCESS) else StatusLabel("Disabled", StatusTone.NEUTRAL),
                )
                Text(
                    if (webhook.scope == WebhookScope.ALL_MESSAGES) "All messages" else "Filtered",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(Modifier.padding(top = Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                Counter("delivered", webhook.sentCount, StatusTone.SUCCESS)
                Counter("failed", webhook.failedCount, if (webhook.failedCount > 0) StatusTone.ERROR else StatusTone.NEUTRAL)
                if (webhook.pendingCount > 0) Counter("pending", webhook.pendingCount, StatusTone.WARNING)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.xs)) {
                TextButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = onTest, enabled = !testing) {
                    if (testing) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Test")
                    }
                }
                Box(Modifier.weight(1f))
                OverflowMenu(onDuplicate = onDuplicate, onDelete = onDelete)
            }
        }
    }
}

@Composable
private fun Counter(label: String, value: Int, tone: StatusTone) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(formatCount(value), style = MaterialTheme.typography.titleSmall, color = tone.contentColor())
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun OverflowMenu(onDuplicate: () -> Unit, onDelete: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "More actions") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Duplicate") }, onClick = { open = false; onDuplicate() })
            DropdownMenuItem(
                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                onClick = { open = false; onDelete() },
            )
        }
    }
}
