package com.bestcallerapp.smswebhook.presentation.webhook_editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bestcallerapp.smswebhook.domain.model.WebhookScope
import com.bestcallerapp.smswebhook.presentation.components.ConfirmDialog
import com.bestcallerapp.smswebhook.presentation.components.EmptyState
import com.bestcallerapp.smswebhook.presentation.components.KeyValueRow
import com.bestcallerapp.smswebhook.presentation.components.LoadingState
import com.bestcallerapp.smswebhook.presentation.components.Panel
import com.bestcallerapp.smswebhook.presentation.components.ScreenScaffold
import com.bestcallerapp.smswebhook.presentation.components.Spacing
import com.bestcallerapp.smswebhook.presentation.components.SwitchRow
import com.bestcallerapp.smswebhook.presentation.components.TestResultDialog
import com.bestcallerapp.smswebhook.presentation.components.describeFilter
import com.bestcallerapp.smswebhook.presentation.components.label

@Composable
fun WebhookEditorScreen(onClose: () -> Unit, viewModel: WebhookEditorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val preview by viewModel.preview.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is EditorEvent.Saved -> onClose()
                is EditorEvent.Message -> snackbar.showSnackbar(event.text)
            }
        }
    }
    BackHandler(enabled = !state.loading && state.loadError == null) {
        if (!viewModel.back()) confirmDiscard = true
    }

    ScreenScaffold(
        title = if (state.isNew) "New webhook" else "Edit webhook",
        topLevel = false,
        snackbarHostState = snackbar,
        actions = {
            IconButton(onClick = { confirmDiscard = true }) { Icon(Icons.Outlined.Close, contentDescription = "Close without saving") }
        },
        bottomBar = {
            if (!state.loading && state.loadError == null) {
                WizardBottomBar(
                    state = state,
                    onBack = { viewModel.back() },
                    onNext = viewModel::next,
                    onSave = viewModel::save,
                )
            }
        },
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            state.loadError != null -> EmptyState(Icons.Outlined.SearchOff, "Cannot edit webhook", state.loadError!!, Modifier.padding(padding))
            else -> Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
                StepHeader(state.step)
                // Each step starts scrolled to the top.
                val scrollState = key(state.step) { rememberScrollState() }
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    val sectionIssues = state.step.section?.let { state.issuesFor(it) }.orEmpty()
                        .filter { it.target == null }
                    sectionIssues.forEach { issue ->
                        Text(issue.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                    when (state.step) {
                        EditorStep.BASICS -> BasicsStep(state, viewModel)
                        EditorStep.HEADERS -> HeadersStep(state, viewModel)
                        EditorStep.FILTERS -> FiltersStep(state, preview, viewModel)
                        EditorStep.PAYLOAD -> PayloadStep(state, preview, viewModel)
                        EditorStep.EXTRACTION -> ExtractionStep(state, preview, viewModel)
                        EditorStep.DELIVERY -> DeliveryStep(state, viewModel)
                        EditorStep.REVIEW -> ReviewStep(state, viewModel)
                    }
                    Spacer(Modifier.size(Spacing.xl))
                }
            }
        }
    }

    state.testResult?.let { TestResultDialog(it, onDismiss = viewModel::dismissTestResult) }
    if (confirmDiscard) {
        ConfirmDialog(
            title = "Discard changes?",
            message = "Your changes to this webhook will not be saved.",
            confirmLabel = "Discard",
            destructive = true,
            onConfirm = onClose,
            onDismiss = { confirmDiscard = false },
        )
    }
}

@Composable
private fun StepHeader(step: EditorStep) {
    val total = EditorStep.entries.size
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
        Text(
            "Step ${step.ordinal + 1} of $total",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(step.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        LinearProgressIndicator(
            progress = { (step.ordinal + 1f) / total },
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        )
    }
}

@Composable
private fun WizardBottomBar(state: EditorUiState, onBack: () -> Unit, onNext: () -> Unit, onSave: () -> Unit) {
    Surface(tonalElevation = 2.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.step.ordinal > 0) OutlinedButton(onClick = onBack) { Text("Back") }
            Spacer(Modifier.weight(1f))
            if (state.step == EditorStep.REVIEW) {
                Button(onClick = onSave, enabled = !state.saving) {
                    if (state.saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Save webhook")
                }
            } else {
                Button(onClick = onNext) { Text("Next") }
            }
        }
    }
}

@Composable
private fun ReviewStep(state: EditorUiState, viewModel: WebhookEditorViewModel) {
    val webhook = state.webhook
    ReviewSection("Webhook", EditorStep.BASICS, viewModel) {
        KeyValueRow("Name", webhook.name)
        KeyValueRow("URL", webhook.url, valueStyleMonospace = true)
        KeyValueRow("Method", webhook.method.name)
    }
    ReviewSection("Headers", EditorStep.HEADERS, viewModel) {
        if (webhook.headers.isEmpty()) KeyValueRow("Headers", "None")
        webhook.headers.forEach { KeyValueRow(it.name, if (it.isSecret) "•••••• (encrypted)" else it.value) }
    }
    ReviewSection("Filters", EditorStep.FILTERS, viewModel) {
        Text(if (webhook.scope == WebhookScope.ALL_MESSAGES) "All messages" else describeFilter(webhook.filter))
    }
    ReviewSection("Payload", EditorStep.PAYLOAD, viewModel) {
        KeyValueRow("Format", webhook.payload.format.label())
        if (webhook.payload.format.usesFields) {
            KeyValueRow("Fields", webhook.payload.fields.filter { it.enabled }.joinToString { it.outputName })
        }
        if (webhook.payload.extractors.isNotEmpty()) {
            KeyValueRow("Extracted", webhook.payload.extractors.joinToString { it.name })
        }
    }
    ReviewSection("Delivery", EditorStep.DELIVERY, viewModel) {
        KeyValueRow("Timeout", "${webhook.timeoutSeconds} s")
        KeyValueRow("Retries", "${webhook.maxRetries}")
        KeyValueRow("First retry after", "${webhook.retryDelaySeconds} s (then doubling)")
    }
    Panel {
        SwitchRow(
            title = "Enable webhook",
            subtitle = "Start forwarding matching messages immediately after saving",
            checked = webhook.enabled,
            onCheckedChange = viewModel::setEnabled,
        )
        TextButton(onClick = viewModel::sendTest, enabled = !state.testing) {
            Text(if (state.testing) "Sending test…" else "Send test request")
        }
    }
    val remaining = state.issues
    if (remaining.isNotEmpty()) {
        Text(
            remaining.joinToString("\n") { "• ${it.message}" },
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ReviewSection(
    title: String,
    step: EditorStep,
    viewModel: WebhookEditorViewModel,
    content: @Composable () -> Unit,
) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = { viewModel.goTo(step) }) { Text("Edit") }
        }
        content()
    }
}

/** Error text for a specific target, shown under the related input. */
@Composable
fun FieldError(message: String?) {
    message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
}

