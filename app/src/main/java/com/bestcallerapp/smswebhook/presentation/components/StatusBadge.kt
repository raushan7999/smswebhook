package com.bestcallerapp.smswebhook.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bestcallerapp.smswebhook.domain.model.DeliveryStatus
import com.bestcallerapp.smswebhook.domain.model.MessageStatus
import com.bestcallerapp.smswebhook.presentation.theme.LocalStatusColors

enum class StatusTone { SUCCESS, WARNING, ERROR, NEUTRAL, INFO }

data class StatusLabel(val text: String, val tone: StatusTone)

fun MessageStatus.label(): StatusLabel = when (this) {
    MessageStatus.RECEIVED -> StatusLabel("Received", StatusTone.INFO)
    MessageStatus.PENDING -> StatusLabel("Pending", StatusTone.WARNING)
    MessageStatus.PROCESSING -> StatusLabel("Sending", StatusTone.INFO)
    MessageStatus.RETRYING -> StatusLabel("Retrying", StatusTone.WARNING)
    MessageStatus.SENT -> StatusLabel("Delivered", StatusTone.SUCCESS)
    MessageStatus.FAILED -> StatusLabel("Failed", StatusTone.ERROR)
    MessageStatus.FILTERED -> StatusLabel("Filtered", StatusTone.NEUTRAL)
}

fun DeliveryStatus.label(): StatusLabel = when (this) {
    DeliveryStatus.PENDING -> StatusLabel("Pending", StatusTone.WARNING)
    DeliveryStatus.PROCESSING -> StatusLabel("Sending", StatusTone.INFO)
    DeliveryStatus.RETRYING -> StatusLabel("Retrying", StatusTone.WARNING)
    DeliveryStatus.SENT -> StatusLabel("Sent", StatusTone.SUCCESS)
    DeliveryStatus.FAILED -> StatusLabel("Failed", StatusTone.ERROR)
    DeliveryStatus.FILTERED -> StatusLabel("Filtered", StatusTone.NEUTRAL)
}

@Composable
fun StatusTone.contentColor(): Color {
    val status = LocalStatusColors.current
    return when (this) {
        StatusTone.SUCCESS -> status.success
        StatusTone.WARNING -> status.warning
        StatusTone.ERROR -> MaterialTheme.colorScheme.error
        StatusTone.NEUTRAL -> status.neutral
        StatusTone.INFO -> MaterialTheme.colorScheme.primary
    }
}

/** Compact status pill: coloured dot + text. Colour is never the only signal. */
@Composable
fun StatusBadge(label: StatusLabel, modifier: Modifier = Modifier) {
    val color = label.tone.contentColor()
    Row(
        modifier = modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .semantics { contentDescription = "Status: ${label.text}" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StatusDot(color)
        Text(label.text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(8.dp).background(color, CircleShape))
}
