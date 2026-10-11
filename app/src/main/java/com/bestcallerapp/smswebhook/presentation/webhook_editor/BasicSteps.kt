package com.bestcallerapp.smswebhook.presentation.webhook_editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import com.bestcallerapp.smswebhook.BuildConfig
import com.bestcallerapp.smswebhook.domain.model.SensitiveHeaders
import com.bestcallerapp.smswebhook.domain.model.WebhookHeader
import com.bestcallerapp.smswebhook.presentation.components.NumberField
import com.bestcallerapp.smswebhook.presentation.components.Panel
import com.bestcallerapp.smswebhook.presentation.components.Spacing
import com.bestcallerapp.smswebhook.presentation.theme.MonospaceStyle

@Composable
fun BasicsStep(state: EditorUiState, viewModel: WebhookEditorViewModel) {
    OutlinedTextField(
        value = state.webhook.name,
        onValueChange = viewModel::setName,
        label = { Text("Webhook name") },
        placeholder = { Text("e.g. Payment Server") },
        singleLine = true,
        isError = state.issueFor("name") != null,
        supportingText = state.issueFor("name")?.let { { Text(it) } },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.webhook.url,
        onValueChange = viewModel::setUrl,
        label = { Text("Webhook URL") },
        placeholder = { Text("https://example.com/sms") },
        singleLine = true,
        textStyle = MonospaceStyle,
        isError = state.issueFor("url") != null,
        supportingText = {
            Text(
                state.issueFor("url")
                    ?: if (BuildConfig.ALLOW_CLEARTEXT_WEBHOOKS) "HTTPS recommended (HTTP allowed in debug builds only)" else "Must use HTTPS",
            )
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth(),
    )
    Text("HTTP method: POST", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun HeadersStep(state: EditorUiState, viewModel: WebhookEditorViewModel) {
    var dialog by rememberSaveable { mutableStateOf<AuthDialog?>(null) }

    Text(
        "Headers are sent with every request. Values marked secret are encrypted on this device with the Android Keystore " +
            "and never shown in logs.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text("Authentication", style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        OutlinedButton(onClick = { dialog = AuthDialog.BEARER }) { Text("Bearer token") }
        OutlinedButton(onClick = { dialog = AuthDialog.BASIC }) { Text("Basic auth") }
        OutlinedButton(onClick = { viewModel.addHeader("X-API-Key", "", secret = true) }) { Text("API key header") }
    }

    Text("Headers", style = MaterialTheme.typography.titleSmall)
    state.webhook.headers.forEachIndexed { index, header ->
        // Stable per-header key so UI state (e.g. "show value") never moves to another header.
        key(state.headerKeys.getOrElse(index) { -index.toLong() - 1 }) {
        HeaderEditor(
            header = header,
            error = state.issueFor("header:$index"),
            onChange = { viewModel.updateHeader(index, it) },
            onRemove = { viewModel.removeHeader(index) },
        )
        }
    }
    if (state.webhook.headers.isEmpty()) {
        Text(
            "No custom headers. Content-Type is set automatically from the payload format.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    TextButton(onClick = { viewModel.addHeader() }) { Text("+ Add header") }

    when (dialog) {
        AuthDialog.BEARER -> SecretInputDialog(
            title = "Bearer token",
            fields = listOf("Token"),
            onConfirm = { viewModel.addBearerToken(it[0]) },
            onDismiss = { dialog = null },
        )
        AuthDialog.BASIC -> SecretInputDialog(
            title = "Basic authentication",
            fields = listOf("Username", "Password"),
            onConfirm = { viewModel.addBasicAuth(it[0], it[1]) },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

private enum class AuthDialog { BEARER, BASIC }

@Composable
private fun HeaderEditor(header: WebhookHeader, error: String?, onChange: (WebhookHeader) -> Unit, onRemove: () -> Unit) {
    var reveal by rememberSaveable { mutableStateOf(false) }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedTextField(
                value = header.name,
                onValueChange = { name ->
                    // Suggest secret storage as soon as the name looks like a credential.
                    val secret = header.isSecret || SensitiveHeaders.looksSensitive(name)
                    onChange(header.copy(name = name.trim(), isSecret = secret))
                },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRemove) { Icon(Icons.Outlined.Delete, contentDescription = "Remove header ${header.name}") }
        }
        OutlinedTextField(
            value = header.value,
            onValueChange = { onChange(header.copy(value = it.replace("\n", "").replace("\r", ""))) },
            label = { Text(if (header.needsReentry) "Value (re-enter)" else "Value") },
            singleLine = true,
            visualTransformation = if (header.isSecret && !reveal) PasswordVisualTransformation() else VisualTransformation.None,
            trailingIcon = {
                if (header.isSecret) {
                    IconButton(onClick = { reveal = !reveal }) {
                        Icon(
                            if (reveal) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (reveal) "Hide value" else "Show value",
                        )
                    }
                }
            },
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            Modifier.fillMaxWidth().toggleable(
                value = header.isSecret,
                role = Role.Checkbox,
                onValueChange = { onChange(header.copy(isSecret = it)) },
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = header.isSecret, onCheckedChange = null)
            Text("Secret (encrypt and hide)", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SecretInputDialog(title: String, fields: List<String>, onConfirm: (List<String>) -> Unit, onDismiss: () -> Unit) {
    var values by rememberSaveable { mutableStateOf(List(fields.size) { "" }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                fields.forEachIndexed { index, label ->
                    OutlinedTextField(
                        value = values[index],
                        onValueChange = { new -> values = values.toMutableList().also { it[index] = new } },
                        label = { Text(label) },
                        singleLine = true,
                        visualTransformation = if (index == fields.lastIndex) PasswordVisualTransformation() else VisualTransformation.None,
                    )
                }
                Text(
                    "Stored as an encrypted Authorization header.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = values.last().isNotEmpty(), onClick = { onConfirm(values); onDismiss() }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun DeliveryStep(state: EditorUiState, viewModel: WebhookEditorViewModel) {
    NumberField(
        label = "Timeout (seconds)",
        value = state.timeoutText,
        onValueChange = viewModel::setTimeoutText,
        supportingText = "How long to wait for the server (1–120)",
        error = state.issueFor("timeout"),
    )
    NumberField(
        label = "Maximum retries",
        value = state.retriesText,
        onValueChange = viewModel::setRetriesText,
        supportingText = "Retries after the first attempt fails (0–10)",
        error = state.issueFor("retries"),
    )
    NumberField(
        label = "First retry delay (seconds)",
        value = state.delayText,
        onValueChange = viewModel::setDelayText,
        supportingText = "Doubles after each failure, e.g. 30 s → 1 min → 2 min (10–3600)",
        error = state.issueFor("delay"),
    )
    Panel {
        Text("How delivery works", style = MaterialTheme.typography.titleSmall)
        Text(
            "A delivery succeeds on any 2xx response. Timeouts, connection problems, 408, 429 and 5xx responses are " +
                "retried with exponential backoff. Other 4xx responses are treated as permanent failures. " +
                "Without internet, deliveries wait and resume automatically when the connection returns.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

