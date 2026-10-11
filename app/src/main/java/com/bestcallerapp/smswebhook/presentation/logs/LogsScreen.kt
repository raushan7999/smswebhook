package com.bestcallerapp.smswebhook.presentation.logs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.bestcallerapp.smswebhook.domain.model.LogFilter
import com.bestcallerapp.smswebhook.presentation.components.AttemptItem
import com.bestcallerapp.smswebhook.presentation.components.ConfirmDialog
import com.bestcallerapp.smswebhook.presentation.components.EmptyState
import com.bestcallerapp.smswebhook.presentation.components.LoadingState
import com.bestcallerapp.smswebhook.presentation.components.ScreenScaffold
import com.bestcallerapp.smswebhook.presentation.components.Spacing

@Composable
fun LogsScreen(onOpenMessage: (Long) -> Unit, viewModel: LogsViewModel = hiltViewModel()) {
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val loggingEnabled by viewModel.loggingEnabled.collectAsStateWithLifecycle()
    val items = viewModel.attempts.collectAsLazyPagingItems()
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    ScreenScaffold(
        title = "Delivery logs",
        topLevel = true,
        actions = {
            IconButton(onClick = { confirmClear = true }, enabled = items.itemCount > 0) {
                Icon(Icons.Outlined.DeleteSweep, contentDescription = "Clear logs")
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                LogFilter.entries.forEach { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { viewModel.setFilter(option) },
                        label = {
                            Text(
                                when (option) {
                                    LogFilter.ALL -> "All"
                                    LogFilter.SUCCESS -> "Successful"
                                    LogFilter.FAILURE -> "Failed"
                                },
                            )
                        },
                    )
                }
            }
            if (!loggingEnabled) {
                Text(
                    "Delivery logging is turned off in Settings. New attempts are not recorded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )
            }
            when {
                items.loadState.refresh is LoadState.Loading && items.itemCount == 0 -> LoadingState()
                items.itemCount == 0 -> EmptyState(
                    Icons.AutoMirrored.Outlined.ListAlt,
                    "No delivery attempts",
                    "Each webhook request is recorded here with its status, duration and error. Header values are never logged.",
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    items(count = items.itemCount, key = items.itemKey { it.id }) { index ->
                        items[index]?.let { attempt ->
                            AttemptItem(attempt, onOpenMessage = { onOpenMessage(attempt.messageId) })
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "Clear delivery logs?",
            message = "All recorded attempts are removed. Message history and delivery status are kept.",
            confirmLabel = "Clear",
            destructive = true,
            onConfirm = viewModel::clear,
            onDismiss = { confirmClear = false },
        )
    }
}
