package com.bestcallerapp.smswebhook.presentation.messages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.bestcallerapp.smswebhook.domain.model.MessageSummary
import com.bestcallerapp.smswebhook.presentation.components.EmptyState
import com.bestcallerapp.smswebhook.presentation.components.LoadingState
import com.bestcallerapp.smswebhook.presentation.components.ScreenScaffold
import com.bestcallerapp.smswebhook.presentation.components.Spacing
import com.bestcallerapp.smswebhook.presentation.components.StatusBadge
import com.bestcallerapp.smswebhook.presentation.components.ThinDivider
import com.bestcallerapp.smswebhook.presentation.components.label
import com.bestcallerapp.smswebhook.presentation.components.relativeTime

@Composable
fun MessagesScreen(onOpenMessage: (Long) -> Unit, viewModel: MessagesViewModel = hiltViewModel()) {
    val search by viewModel.search.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val items = viewModel.messages.collectAsLazyPagingItems()

    ScreenScaffold(title = "Messages", topLevel = true) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = search,
                onValueChange = viewModel::onSearchChange,
                placeholder = { Text("Search sender or message") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (search.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchChange("") }) {
                            Icon(Icons.Outlined.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                MessageFilter.entries.forEach { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { viewModel.onFilterChange(option) },
                        label = { Text(option.label) },
                    )
                }
            }
            when {
                items.loadState.refresh is LoadState.Loading && items.itemCount == 0 -> LoadingState()
                items.loadState.refresh is LoadState.Error -> EmptyState(
                    Icons.Outlined.Inbox, "Could not load messages", "Please try again.",
                )
                items.itemCount == 0 -> EmptyState(
                    icon = Icons.Outlined.Inbox,
                    title = if (search.isBlank() && filter == MessageFilter.ALL) "No messages yet" else "No matching messages",
                    message = if (search.isBlank() && filter == MessageFilter.ALL) {
                        "Received SMS will appear here once forwarding is active."
                    } else {
                        "Try a different search or filter."
                    },
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(count = items.itemCount, key = items.itemKey { it.id }) { index ->
                        items[index]?.let { message ->
                            MessageRow(message, onClick = { onOpenMessage(message.id) })
                            ThinDivider(Modifier.padding(horizontal = Spacing.lg))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageRow(message: MessageSummary, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                message.sender,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                relativeTime(message.receivedAtMillis),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            message.preview,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        StatusBadge(message.status.label())
    }
}

