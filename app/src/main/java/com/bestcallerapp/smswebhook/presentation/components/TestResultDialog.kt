package com.bestcallerapp.smswebhook.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bestcallerapp.smswebhook.domain.usecase.WebhookTestResult
import com.bestcallerapp.smswebhook.presentation.theme.MonospaceStyle

@Composable
fun TestResultDialog(result: WebhookTestResult, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (result.success) "Test succeeded" else "Test failed") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                StatusBadge(
                    if (result.success) StatusLabel("Success", StatusTone.SUCCESS) else StatusLabel("Failed", StatusTone.ERROR),
                )
                KeyValueRow("HTTP status", result.httpStatus?.toString() ?: "—")
                KeyValueRow("Response time", "${result.durationMillis} ms")
                result.errorMessage?.let { KeyValueRow("Error", it) }
                SectionHeader("Request body")
                Text(result.requestBody, style = MonospaceStyle)
                SectionHeader("Response body")
                Text(
                    result.responseBody?.takeIf { it.isNotBlank() } ?: "(empty)",
                    style = MonospaceStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
