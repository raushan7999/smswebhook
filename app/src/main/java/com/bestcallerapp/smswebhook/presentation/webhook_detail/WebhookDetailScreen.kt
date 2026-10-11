package com.bestcallerapp.smswebhook.presentation.webhook_detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.bestcallerapp.smswebhook.domain.model.WebhookScope
import com.bestcallerapp.smswebhook.presentation.components.ConfirmDialog
import com.bestcallerapp.smswebhook.presentation.components.DeliveryItem
import com.bestcallerapp.smswebhook.presentation.components.EmptyState
import com.bestcallerapp.smswebhook.presentation.components.KeyValueRow
import com.bestcallerapp.smswebhook.presentation.components.LoadingState
import com.bestcallerapp.smswebhook.presentation.components.Panel
import com.bestcallerapp.smswebhook.presentation.components.ScreenScaffold
import com.bestcallerapp.smswebhook.presentation.components.SectionHeader
import com.bestcallerapp.smswebhook.presentation.components.Spacing
import com.bestcallerapp.smswebhook.presentation.components.SwitchRow
import com.bestcallerapp.smswebhook.presentation.components.TestResultDialog
import com.bestcallerapp.smswebhook.presentation.components.absoluteTime
import com.bestcallerapp.smswebhook.presentation.components.describeFilter
import com.bestcallerapp.smswebhook.presentation.components.formatCount
import com.bestcallerapp.smswebhook.presentation.components.label
import com.bestcallerapp.smswebhook.presentation.webhook.OverflowMenu

@Composable
fun WebhookDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenMessage: (Long) -> Unit,
    viewModel: WebhookDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val failedOnly by viewModel.failedOnly.collectAsStateWithLifecycle()
    val test by viewModel.test.collectAsStateWithLifecycle()
    val deliveries = viewModel.deliveries.collectAsLazyPagingItems()
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.events.collect { snackbar.showSnackbar(it) } }

    val loaded = state as? WebhookDetailState.Loaded
    ScreenScaffold(
        title = loaded?.webhook?.name ?: "Webhook",
        topLevel = false,
        onBack = onBack,
        snackbarHostState = snackbar,
        actions = {
            if (loaded != null) {
                IconButton(onClick = { onEdit(viewModel.webhookId) }) { Icon(Icons.Outlined.Edit, contentDescription = "Edit webhook") }
                OverflowMenu(onDuplicate = viewModel::duplicate, onDelete = { confirmDelete = true })
            }
        },
    ) { padding ->
        when (val current = state) {
            WebhookDetailState.Loading -> LoadingState(Modifier.padding(padding))
            WebhookDetailState.Missing -> EmptyState(
                Icons.Outlined.SearchOff, "Webhook not found", "It may have been deleted.", Modifier.padding(padding),
            )
            is WebhookDetailState.Loaded -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                val webhook = current.webhook
                item {
                    Panel {
                        SwitchRow(
                            title = if (webhook.enabled) "Enabled" else "Disabled",
                            subtitle = if (webhook.enabled) "Receives new matching messages" else "New messages are not sent here",
                            checked = webhook.enabled,
                            onCheckedChange = viewModel::setEnabled,
                        )
                        KeyValueRow("URL", webhook.url, valueStyleMonospace = true)
                        KeyValueRow("Method", webhook.method.name)
                        KeyValueRow(
                            "Receives",
                            if (webhook.scope == WebhookScope.ALL_MESSAGES) "All messages" else describeFilter(webhook.filter),
                        )
                        KeyValueRow("Payload", webhook.payload.format.label())
                        KeyValueRow("Headers", if (webhook.headers.isEmpty()) "None" else webhook.headers.joinToString { it.name })
                        if (webhook.payload.extractors.isNotEmpty()) {
                            KeyValueRow("Extracted fields", webhook.payload.extractors.joinToString { it.name })
                        }
                        KeyValueRow("Timeout", "${webhook.timeoutSeconds} s")
                        KeyValueRow("Retries", "${webhook.maxRetries}, starting at ${webhook.retryDelaySeconds} s")
                        KeyValueRow("Updated", absoluteTime(webhook.updatedAtMillis))
                        if (webhook.headers.any { it.needsReentry }) {
                            Text(
                                "A secret header could not be decrypted (e.g. after restoring the app). Edit the webhook and re-enter it.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.sm)) {
                            OutlinedButton(onClick = viewModel::test, enabled = test.runningForId == null) {
                                if (test.runningForId != null) {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("Test webhook")
                                }
                            }
                            if (current.failed > 0) {
                                OutlinedButton(onClick = viewModel::retryAllFailed) { Text("Retry failed") }
                            }
                        }
                    }
                }
                item {
                    Panel {
                        KeyValueRow("Delivered", formatCount(current.sent))
                        KeyValueRow("Failed", formatCount(current.failed))
                        KeyValueRow("Pending", formatCount(current.pending))
                    }
                }
                item {
                    SectionHeader("Delivery history")
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        FilterChip(selected = !failedOnly, onClick = { viewModel.setFailedOnly(false) }, label = { Text("All") })
                        FilterChip(selected = failedOnly, onClick = { viewModel.setFailedOnly(true) }, label = { Text("Failed") })
                    }
                }
                if (deliveries.itemCount == 0) {
                    item {
                        Text(
                            if (failedOnly) "No failed deliveries." else "No deliveries yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(count = deliveries.itemCount, key = deliveries.itemKey { it.id }) { index ->
                    deliveries[index]?.let { delivery ->
                        DeliveryItem(
                            delivery = delivery,
                            title = "Message #${delivery.messageId} · ${absoluteTime(delivery.createdAtMillis)}",
                            onRetry = { viewModel.retry(delivery.id) },
                            onOpen = { onOpenMessage(delivery.messageId) },
                        )
                    }
                }
            }
        }
    }

    test.result?.let { TestResultDialog(it, onDismiss = viewModel::dismissTest) }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete webhook?",
            message = "Its configuration and delivery history will be removed. Pending deliveries are cancelled.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { viewModel.delete(onBack) },
            onDismiss = { confirmDelete = false },
        )
    }
}
