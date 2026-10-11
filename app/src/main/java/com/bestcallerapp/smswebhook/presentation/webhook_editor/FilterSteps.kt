package com.bestcallerapp.smswebhook.presentation.webhook_editor

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.bestcallerapp.smswebhook.core.constants.WebhookLimits
import com.bestcallerapp.smswebhook.domain.engine.WebhookPreview
import com.bestcallerapp.smswebhook.domain.model.WebhookScope
import com.bestcallerapp.smswebhook.domain.model.filter.FilterCondition
import com.bestcallerapp.smswebhook.domain.model.filter.FilterField
import com.bestcallerapp.smswebhook.domain.model.filter.FilterGroup
import com.bestcallerapp.smswebhook.domain.model.filter.FilterOperator
import com.bestcallerapp.smswebhook.domain.model.filter.LogicalOperator
import com.bestcallerapp.smswebhook.domain.model.filter.ValueKind
import com.bestcallerapp.smswebhook.presentation.components.DropdownField
import com.bestcallerapp.smswebhook.presentation.components.Panel
import com.bestcallerapp.smswebhook.presentation.components.Spacing
import com.bestcallerapp.smswebhook.presentation.components.StatusBadge
import com.bestcallerapp.smswebhook.presentation.components.StatusLabel
import com.bestcallerapp.smswebhook.presentation.components.StatusTone
import com.bestcallerapp.smswebhook.presentation.components.label

@Composable
fun FiltersStep(state: EditorUiState, preview: WebhookPreview?, viewModel: WebhookEditorViewModel) {
    var regexTest by rememberSaveable { mutableStateOf<Pair<String, Boolean>?>(null) }

    Column(Modifier.selectableGroup()) {
        ScopeOption(
            selected = state.webhook.scope == WebhookScope.ALL_MESSAGES,
            title = "All messages",
            subtitle = "Every incoming SMS is forwarded (common webhook)",
            onClick = { viewModel.setScope(WebhookScope.ALL_MESSAGES) },
        )
        ScopeOption(
            selected = state.webhook.scope == WebhookScope.MATCHING_RULES,
            title = "Only matching messages",
            subtitle = "Forward SMS that satisfy the rules below",
            onClick = { viewModel.setScope(WebhookScope.MATCHING_RULES) },
        )
    }

    if (state.webhook.scope == WebhookScope.MATCHING_RULES) {
        GroupEditor(
            group = state.webhook.filter,
            depth = 1,
            isRoot = true,
            state = state,
            viewModel = viewModel,
            onTestRegex = { pattern, caseSensitive -> regexTest = pattern to caseSensitive },
        )
    }

    SampleSmsPanel(state, viewModel) {
        val matches = preview?.matches
        if (matches != null) {
            StatusBadge(
                if (matches) StatusLabel("Would be forwarded", StatusTone.SUCCESS) else StatusLabel("Would be filtered out", StatusTone.NEUTRAL),
            )
        }
    }

    regexTest?.let { (pattern, caseSensitive) ->
        RegexTestDialog(
            initialPattern = pattern,
            caseSensitive = caseSensitive,
            initialSample = state.sampleBody,
            onTest = viewModel::testRegex,
            onDismiss = { regexTest = null },
        )
    }
}

@Composable
private fun ScopeOption(selected: Boolean, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = Spacing.sm)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GroupEditor(
    group: FilterGroup,
    depth: Int,
    isRoot: Boolean,
    state: EditorUiState,
    viewModel: WebhookEditorViewModel,
    onTestRegex: (String, Boolean) -> Unit,
) {
    val container = if (isRoot) Modifier else Modifier
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
        .padding(Spacing.md)
    Column(container, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DropdownField(
                label = if (isRoot) "Match" else "Group matches",
                selected = group.operator,
                options = LogicalOperator.entries,
                optionLabel = { it.label() },
                onSelected = { op -> viewModel.updateFilterNode(group.id) { (it as FilterGroup).copy(operator = op) } },
                modifier = Modifier.weight(1f),
            )
            if (!isRoot) {
                IconButton(onClick = { viewModel.removeFilterNode(group.id) }) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Remove group")
                }
            }
        }
        if (!isRoot) {
            CheckRow("NOT — invert this group", group.negate) { negate ->
                viewModel.updateFilterNode(group.id) { (it as FilterGroup).copy(negate = negate) }
            }
        }
        group.children.forEachIndexed { index, child ->
            if (index > 0) {
                Text(
                    group.operator.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            when (child) {
                is FilterCondition -> ConditionEditor(child, state.issueFor(child.id), viewModel, onTestRegex)
                is FilterGroup -> GroupEditor(child, depth + 1, isRoot = false, state, viewModel, onTestRegex)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            TextButton(onClick = { viewModel.addCondition(group.id) }) { Text("+ Condition") }
            if (depth < WebhookLimits.MAX_FILTER_DEPTH) {
                TextButton(onClick = { viewModel.addGroup(group.id) }) { Text("+ Group") }
            }
        }
    }
}

@Composable
private fun ConditionEditor(
    condition: FilterCondition,
    error: String?,
    viewModel: WebhookEditorViewModel,
    onTestRegex: (String, Boolean) -> Unit,
) {
    fun update(transform: (FilterCondition) -> FilterCondition) =
        viewModel.updateFilterNode(condition.id) { transform(it as FilterCondition) }

    val kind = condition.field.valueKind
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            DropdownField(
                label = "Field",
                selected = condition.field,
                options = FilterField.entries,
                optionLabel = { it.label() },
                onSelected = { field ->
                    update {
                        val operator = if (it.operator in field.operators) it.operator else field.operators.first()
                        it.copy(field = field, operator = operator)
                    }
                },
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { viewModel.removeFilterNode(condition.id) }) {
                Icon(Icons.Outlined.Delete, contentDescription = "Remove condition")
            }
        }
        DropdownField(
            label = "Operator",
            selected = condition.operator,
            options = condition.field.operators,
            optionLabel = { it.label(kind) },
            onSelected = { op -> update { it.copy(operator = op) } },
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        )
        when {
            condition.operator.usesKeywords -> OutlinedTextField(
                value = condition.keywords.joinToString("\n"),
                onValueChange = { text -> update { it.copy(keywords = text.split("\n")) } },
                label = { Text("Keywords (one per line)") },
                minLines = 3,
                isError = error != null,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            )
            else -> Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.sm)) {
                OutlinedTextField(
                    value = condition.value,
                    onValueChange = { v -> update { it.copy(value = v) } },
                    label = { Text(valueLabel(kind, condition.operator, first = true)) },
                    singleLine = true,
                    isError = error != null,
                    modifier = Modifier.weight(1f),
                )
                if (condition.operator.usesSecondValue) {
                    OutlinedTextField(
                        value = condition.secondValue,
                        onValueChange = { v -> update { it.copy(secondValue = v) } },
                        label = { Text(valueLabel(kind, condition.operator, first = false)) },
                        singleLine = true,
                        isError = error != null,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        FieldError(error)
        if (kind == ValueKind.TEXT) {
            CheckRow("Case-sensitive", condition.caseSensitive) { cs -> update { it.copy(caseSensitive = cs) } }
        }
        CheckRow("NOT — invert this condition", condition.negate) { negate -> update { it.copy(negate = negate) } }
        if (condition.operator == FilterOperator.MATCHES_REGEX) {
            TextButton(onClick = { onTestRegex(condition.value, condition.caseSensitive) }) { Text("Test regex") }
        }
    }
}

private fun valueLabel(kind: ValueKind, operator: FilterOperator, first: Boolean): String = when (kind) {
    ValueKind.TEXT -> if (operator == FilterOperator.MATCHES_REGEX) "Regular expression" else "Value"
    ValueKind.NUMBER -> if (!operator.usesSecondValue) "Number" else if (first) "From" else "To"
    ValueKind.TIME_OF_DAY -> if (!operator.usesSecondValue) "Time (HH:mm)" else if (first) "From (HH:mm)" else "To (HH:mm)"
}

@Composable
fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = Spacing.sm))
    }
}

/** Editable sample SMS shared by the filter, payload and extraction previews. */
@Composable
fun SampleSmsPanel(state: EditorUiState, viewModel: WebhookEditorViewModel, result: @Composable () -> Unit) {
    Panel {
        Text("Try it with a sample SMS", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = state.sampleSender,
            onValueChange = viewModel::setSampleSender,
            label = { Text("Sample sender") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        )
        OutlinedTextField(
            value = state.sampleBody,
            onValueChange = viewModel::setSampleBody,
            label = { Text("Sample message") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        )
        Column(Modifier.padding(top = Spacing.sm)) { result() }
    }
}
