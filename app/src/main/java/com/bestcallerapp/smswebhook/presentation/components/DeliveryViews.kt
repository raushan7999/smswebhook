package com.bestcallerapp.smswebhook.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.bestcallerapp.smswebhook.domain.model.AttemptOutcome
import com.bestcallerapp.smswebhook.domain.model.Delivery
import com.bestcallerapp.smswebhook.domain.model.DeliveryAttempt
import com.bestcallerapp.smswebhook.domain.model.DeliveryStatus
import com.bestcallerapp.smswebhook.presentation.theme.MonospaceStyle

/** One message→webhook delivery with its latest result and an optional retry action. */
@Composable
fun DeliveryItem(
    delivery: Delivery,
    title: String,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onOpen: (() -> Unit)? = null,
) {
    var expanded by rememberSaveable(delivery.id) { mutableStateOf(false) }
    Panel(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            StatusBadge(delivery.status.label())
        }
        if (delivery.status != DeliveryStatus.FILTERED) {
            KeyValueRow("Attempts", delivery.attemptCount.toString())
            delivery.httpStatus?.let { KeyValueRow("HTTP status", it.toString()) }
            delivery.lastAttemptAtMillis?.let { KeyValueRow("Last attempt", absoluteTime(it)) }
            if (delivery.status == DeliveryStatus.RETRYING) {
                delivery.nextAttemptAtMillis?.let { KeyValueRow("Next attempt", "≈ ${absoluteTime(it)}") }
            }
            delivery.errorMessage?.let { KeyValueRow("Error", it) }
        } else {
            Text(
                "Message did not match this webhook's filters.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) {
            delivery.payloadBody?.let {
                SectionHeader("Request body")
                Text(it, style = MonospaceStyle)
            }
            delivery.responseBody?.takeIf { it.isNotBlank() }?.let {
                SectionHeader("Response")
                Text(it, style = MonospaceStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.xs)) {
            if (delivery.payloadBody != null || delivery.responseBody != null) {
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide details" else "Show details") }
            }
            onOpen?.let { TextButton(onClick = it) { Text("Open message") } }
            if (onRetry != null && delivery.status == DeliveryStatus.FAILED) {
                OutlinedButton(onClick = onRetry) { Text("Retry") }
            }
        }
    }
}

@Composable
fun AttemptItem(attempt: DeliveryAttempt, modifier: Modifier = Modifier, onOpenMessage: (() -> Unit)? = null) {
    val success = attempt.outcome == AttemptOutcome.SUCCESS
    Panel(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(attempt.webhookName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${attempt.requestMethod} ${attempt.requestUrl}",
                    style = MonospaceStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            StatusBadge(
                when (attempt.outcome) {
                    AttemptOutcome.SUCCESS -> StatusLabel("Success", StatusTone.SUCCESS)
                    AttemptOutcome.RETRYABLE_FAILURE -> StatusLabel("Will retry", StatusTone.WARNING)
                    AttemptOutcome.PERMANENT_FAILURE -> StatusLabel("Failed", StatusTone.ERROR)
                },
            )
        }
        KeyValueRow("Status", attempt.httpStatus?.let { httpStatusText(it) } ?: "No response")
        KeyValueRow("Attempt", attempt.attemptNumber.toString())
        KeyValueRow("Duration", "${attempt.durationMillis} ms")
        KeyValueRow("Time", absoluteTime(attempt.startedAtMillis))
        if (!success) attempt.errorMessage?.let { KeyValueRow("Error", it) }
        onOpenMessage?.let { TextButton(onClick = it) { Text("Open message") } }
    }
}

fun httpStatusText(code: Int): String = when (code) {
    200 -> "200 OK"
    201 -> "201 Created"
    202 -> "202 Accepted"
    204 -> "204 No Content"
    400 -> "400 Bad Request"
    401 -> "401 Unauthorized"
    403 -> "403 Forbidden"
    404 -> "404 Not Found"
    408 -> "408 Request Timeout"
    429 -> "429 Too Many Requests"
    500 -> "500 Internal Server Error"
    502 -> "502 Bad Gateway"
    503 -> "503 Service Unavailable"
    504 -> "504 Gateway Timeout"
    else -> code.toString()
}

