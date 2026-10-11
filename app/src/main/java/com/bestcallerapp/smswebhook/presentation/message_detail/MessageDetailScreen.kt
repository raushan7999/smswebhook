package com.bestcallerapp.smswebhook.presentation.message_detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bestcallerapp.smswebhook.presentation.components.AttemptItem
import com.bestcallerapp.smswebhook.presentation.components.ConfirmDialog
import com.bestcallerapp.smswebhook.presentation.components.DeliveryItem
import com.bestcallerapp.smswebhook.presentation.components.EmptyState
import com.bestcallerapp.smswebhook.presentation.components.KeyValueRow
import com.bestcallerapp.smswebhook.presentation.components.LoadingState
import com.bestcallerapp.smswebhook.presentation.components.Panel
import com.bestcallerapp.smswebhook.presentation.components.ScreenScaffold
import com.bestcallerapp.smswebhook.presentation.components.SectionHeader
import com.bestcallerapp.smswebhook.presentation.components.Spacing
import com.bestcallerapp.smswebhook.presentation.components.StatusBadge
import com.bestcallerapp.smswebhook.presentation.components.absoluteTime
import com.bestcallerapp.smswebhook.presentation.components.label

@Composable
fun MessageDetailScreen(onBack: () -> Unit, viewModel: MessageDetailViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.events.collect { snackbar.showSnackbar(it) } }

    ScreenScaffold(
        title = state.message?.sender ?: "Message",
        topLevel = false,
        onBack = onBack,
        snackbarHostState = snackbar,
        actions = {
            if (state.message != null) {
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, contentDescription = "Delete message") }
            }
        },
    ) { padding ->
        val message = state.message
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            message == null -> EmptyState(
                Icons.Outlined.SearchOff, "Message not found", "It may have been removed by retention cleanup.",
                Modifier.padding(padding),
            )
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                item {
                    Panel {
                        StatusBadge(message.status.label())
                        SelectionContainer {
                            Text(message.body, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = Spacing.md))
                        }
                        KeyValueRow("Sender", message.sender)
                        KeyValueRow("Sent (SMSC)", absoluteTime(message.sentAtMillis))
                        KeyValueRow("Received", absoluteTime(message.receivedAtMillis))
                        KeyValueRow("SIM", message.simSlot?.let { "SIM ${it + 1}" } ?: "Unknown")
                        message.subscriptionId?.let { KeyValueRow("Subscription", it.toString()) }
                        KeyValueRow("Parts", message.partCount.toString())
                        KeyValueRow("Message ID", message.id.toString())
                        KeyValueRow("Fingerprint", message.fingerprint.take(16) + "…", valueStyleMonospace = true)
                    }
                }
                item { SectionHeader("Webhooks (${state.deliveries.size})") }
                if (state.deliveries.isEmpty()) {
                    item {
                        Text(
                            "No webhooks were enabled when this message arrived.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(state.deliveries, key = { it.id }) { delivery ->
                    DeliveryItem(delivery, title = delivery.webhookName, onRetry = { viewModel.retry(delivery.id) })
                }
                if (state.attempts.isNotEmpty()) {
                    item { SectionHeader("Delivery attempts") }
                    items(state.attempts, key = { "attempt-${it.id}" }) { AttemptItem(it) }
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete message?",
            message = "The message and its delivery history will be removed from this app. Pending deliveries are cancelled. The SMS in your messaging app is not affected.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { viewModel.delete(onBack) },
            onDismiss = { confirmDelete = false },
        )
    }
}

