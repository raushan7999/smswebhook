package com.bestcallerapp.smswebhook.presentation.webhook_editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.bestcallerapp.smswebhook.domain.engine.WebhookPreview
import com.bestcallerapp.smswebhook.domain.model.payload.PayloadConfig
import com.bestcallerapp.smswebhook.domain.model.payload.PayloadField
import com.bestcallerapp.smswebhook.domain.model.payload.PayloadFieldKey
import com.bestcallerapp.smswebhook.domain.model.payload.PayloadFormat
import com.bestcallerapp.smswebhook.domain.model.payload.RegexExtractor
import com.bestcallerapp.smswebhook.presentation.components.DropdownField
import com.bestcallerapp.smswebhook.presentation.components.NumberField
import com.bestcallerapp.smswebhook.presentation.components.Panel
import com.bestcallerapp.smswebhook.presentation.components.Spacing
import com.bestcallerapp.smswebhook.presentation.components.label
import com.bestcallerapp.smswebhook.presentation.theme.MonospaceStyle

@Composable
fun PayloadStep(state: EditorUiState, preview: WebhookPreview?, viewModel: WebhookEditorViewModel) {
    val payload = state.webhook.payload
    DropdownField(
        label = "Body format",
        selected = payload.format,
        options = PayloadFormat.entries,
        optionLabel = { it.label() },
        onSelected = viewModel::setFormat,
        modifier = Modifier.fillMaxWidth(),
    )

    if (payload.format.usesFields) {
        Text(
            "Choose which fields to send and how they are named.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        payload.fields.forEach { field ->
            FieldRow(field, state.issueFor(field.key.name), viewModel::updateField)
        }
        if (payload.extractors.isNotEmpty()) {
            Text(
                "Extracted values (${payload.extractors.joinToString { it.name }}) are added as extra fields.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        OutlinedTextField(
            value = payload.template,
            onValueChange = viewModel::setTemplate,
            label = { Text("Template") },
            textStyle = MonospaceStyle,
            minLines = 6,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Variables: " + (PayloadFieldKey.entries.map { "{{${it.variable}}}" } +
                payload.extractors.filter { it.name.isNotBlank() }.map { "{{${PayloadConfig.EXTRACTED_VARIABLE_PREFIX}${it.name}}}" })
                .joinToString(", ") +
                if (payload.format == PayloadFormat.JSON_TEMPLATE) ". Values are JSON-escaped automatically; keep placeholders inside quotes." else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    SampleSmsPanel(state, viewModel) {
        Text("Request body preview", style = MaterialTheme.typography.labelLarge)
        Text(preview?.payload.orEmpty(), style = MonospaceStyle, modifier = Modifier.padding(top = Spacing.xs))
    }
}

@Composable
private fun FieldRow(field: PayloadField, error: String?, onChange: (PayloadField) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .toggleable(value = field.enabled, role = Role.Checkbox, onValueChange = { onChange(field.copy(enabled = it)) }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = field.enabled, onCheckedChange = null)
            Text(field.key.label(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = Spacing.sm))
        }
        OutlinedTextField(
            value = field.outputName,
            onValueChange = { onChange(field.copy(outputName = it.trim())) },
            label = { Text("Name") },
            singleLine = true,
            enabled = field.enabled,
            isError = error != null,
            textStyle = MonospaceStyle,
            modifier = Modifier.weight(1f),
        )
    }
    FieldError(error)
}

@Composable
fun ExtractionStep(state: EditorUiState, preview: WebhookPreview?, viewModel: WebhookEditorViewModel) {
    Text(
        "Pull values out of the message with a regular expression and capture group, e.g. " +
            "amount = credited with Rs ([0-9,]+), group 1.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    state.webhook.payload.extractors.forEachIndexed { index, extractor ->
        ExtractorEditor(
            extractor = extractor,
            error = state.issueFor("extractor:$index"),
            extractedValue = preview?.extracted?.get(extractor.name),
            onChange = { viewModel.updateExtractor(index, it) },
            onRemove = { viewModel.removeExtractor(index) },
        )
    }
    TextButton(onClick = viewModel::addExtractor) { Text("+ Add extracted field") }
    SampleSmsPanel(state, viewModel) {
        val values = preview?.extracted.orEmpty()
        if (values.isEmpty()) {
            Text("No extracted fields defined.", style = MaterialTheme.typography.bodySmall)
        } else {
            values.forEach { (name, value) -> Text("$name = ${value ?: "(no match)"}", style = MonospaceStyle) }
        }
    }
}

@Composable
private fun ExtractorEditor(
    extractor: RegexExtractor,
    error: String?,
    extractedValue: String?,
    onChange: (RegexExtractor) -> Unit,
    onRemove: () -> Unit,
) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedTextField(
                value = extractor.name,
                onValueChange = { onChange(extractor.copy(name = it.trim())) },
                label = { Text("Field name") },
                singleLine = true,
                textStyle = MonospaceStyle,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRemove) { Icon(Icons.Outlined.Delete, contentDescription = "Remove extracted field") }
        }
        OutlinedTextField(
            value = extractor.pattern,
            onValueChange = { onChange(extractor.copy(pattern = it)) },
            label = { Text("Regex") },
            textStyle = MonospaceStyle,
            isError = error != null,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        )
        Column(Modifier.padding(top = Spacing.sm)) {
            NumberField(
                label = "Capture group",
                value = extractor.captureGroup.toString(),
                onValueChange = { onChange(extractor.copy(captureGroup = it.toIntOrNull() ?: 0)) },
                supportingText = "0 = whole match, 1 = first (…) group",
            )
        }
        CheckRow("Case-sensitive", extractor.caseSensitive) { onChange(extractor.copy(caseSensitive = it)) }
        FieldError(error)
        if (error == null && extractor.pattern.isNotEmpty()) {
            Text(
                "Sample result: ${extractedValue ?: "(no match)"}",
                style = MonospaceStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
