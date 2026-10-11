package com.bestcallerapp.smswebhook.presentation.webhook_editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.bestcallerapp.smswebhook.domain.engine.RegexTestResult
import com.bestcallerapp.smswebhook.presentation.components.Spacing
import com.bestcallerapp.smswebhook.presentation.components.StatusBadge
import com.bestcallerapp.smswebhook.presentation.components.StatusLabel
import com.bestcallerapp.smswebhook.presentation.components.StatusTone
import com.bestcallerapp.smswebhook.presentation.theme.MonospaceStyle

/** Lets the user try a pattern against sample text. Changes to the pattern here are not saved. */
@Composable
fun RegexTestDialog(
    initialPattern: String,
    caseSensitive: Boolean,
    initialSample: String,
    onTest: (String, Boolean, String) -> RegexTestResult,
    onDismiss: () -> Unit,
) {
    var pattern by rememberSaveable { mutableStateOf(initialPattern) }
    var sample by rememberSaveable { mutableStateOf(initialSample) }
    val result = remember(pattern, sample, caseSensitive) { onTest(pattern, caseSensitive, sample) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Test regex") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text("Pattern") },
                    textStyle = MonospaceStyle,
                    isError = result.error != null,
                )
                OutlinedTextField(value = sample, onValueChange = { sample = it }, label = { Text("Sample SMS text") }, minLines = 3)
                when {
                    result.error != null -> Text(result.error, color = MaterialTheme.colorScheme.error)
                    result.matched -> {
                        StatusBadge(StatusLabel("Matches", StatusTone.SUCCESS))
                        Text("Match: ${result.match}", style = MonospaceStyle)
                        result.groups.forEachIndexed { index, group ->
                            Text("Group ${index + 1}: ${group ?: "—"}", style = MonospaceStyle)
                        }
                    }
                    else -> StatusBadge(StatusLabel("No match", StatusTone.NEUTRAL))
                }
                if (!caseSensitive) {
                    Text(
                        "Case-insensitive matching is on for this condition.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
