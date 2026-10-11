package com.bestcallerapp.smswebhook.presentation.settings

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bestcallerapp.smswebhook.BuildConfig
import com.bestcallerapp.smswebhook.core.constants.AppLinks
import com.bestcallerapp.smswebhook.core.constants.WebhookLimits
import com.bestcallerapp.smswebhook.domain.model.AppSettings
import com.bestcallerapp.smswebhook.domain.model.DeleteSmsPolicy
import com.bestcallerapp.smswebhook.domain.model.RetentionPolicy
import com.bestcallerapp.smswebhook.presentation.components.ConfirmDialog
import com.bestcallerapp.smswebhook.presentation.components.LoadingState
import com.bestcallerapp.smswebhook.presentation.components.ScreenScaffold
import com.bestcallerapp.smswebhook.presentation.components.SectionHeader
import com.bestcallerapp.smswebhook.presentation.components.Spacing
import com.bestcallerapp.smswebhook.presentation.components.SwitchRow
import com.bestcallerapp.smswebhook.presentation.components.ThinDivider
import com.bestcallerapp.smswebhook.presentation.components.formatCount
import com.bestcallerapp.smswebhook.presentation.home.openAppSettings
import com.bestcallerapp.smswebhook.presentation.home.openBatteryOptimizationSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class SettingsDialog {
    RETENTION_MODE, RETENTION_AGE, RETENTION_COUNT, RETENTION_CUSTOM, DELETE_POLICY,
    RETRIES, RETRY_DELAY, TIMEOUT, LOG_RETENTION,
    CLEAR_HISTORY, CLEAR_LOGS, CLEAR_ALL, EXPORT, EXPORT_SECRETS_WARNING,
}

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val batteryUnrestricted by viewModel.batteryUnrestricted.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var dialog by rememberSaveable { mutableStateOf<SettingsDialog?>(null) }
    var exportWithSecrets by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.events.collect { snackbar.showSnackbar(it) } }
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = runCatching {
                val json = viewModel.export(exportWithSecrets)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                }
            }.isSuccess
            viewModel.onExported(ok)
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)!!.use { stream ->
                        stream.readNBytesCompat(MAX_IMPORT_BYTES).toString(Charsets.UTF_8)
                    }
                }
            }.getOrNull()
            viewModel.import(text)
        }
    }

    ScreenScaffold(title = "Settings", topLevel = true, snackbarHostState = snackbar) { padding ->
        val current = settings
        if (current == null) {
            LoadingState(Modifier.padding(padding))
            return@ScreenScaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg),
        ) {
            SectionHeader("SMS")
            SwitchRow(
                title = "SMS listener",
                subtitle = "Receive and forward incoming SMS",
                checked = current.listenerEnabled,
                onCheckedChange = { viewModel.setListener(it) },
            )
            SettingsItem(
                title = "SIM handling",
                subtitle = "SIM slot and subscription are recorded when the device reports them. " +
                    "Use the \"SIM slot\" filter to route messages per SIM.",
            )
            SettingsItem(
                title = "Delete SMS after forwarding",
                subtitle = if (viewModel.smsDeletionSupported) deletePolicyLabel(current.deleteSmsPolicy) else
                    "Not available: Android only lets the default SMS app delete messages. " +
                    "SMS Webhook is not an SMS app, so forwarded messages stay in your messaging app.",
                enabled = viewModel.smsDeletionSupported,
                onClick = { dialog = SettingsDialog.DELETE_POLICY },
            )

            ThinDivider()
            SectionHeader("History")
            SettingsItem("Retention", retentionLabel(current.retention), onClick = { dialog = SettingsDialog.RETENTION_MODE })
            SettingsItem(
                "Clear history",
                "Remove stored messages (messages still being delivered are kept)",
                onClick = { dialog = SettingsDialog.CLEAR_HISTORY },
            )

            ThinDivider()
            SectionHeader("Delivery")
            SettingsItem("Default retry count", "${current.defaultMaxRetries} — used for new webhooks", onClick = { dialog = SettingsDialog.RETRIES })
            SettingsItem("Default retry delay", "${current.defaultRetryDelaySeconds} s, doubling each retry", onClick = { dialog = SettingsDialog.RETRY_DELAY })
            SettingsItem("Default timeout", "${current.defaultTimeoutSeconds} s", onClick = { dialog = SettingsDialog.TIMEOUT })
            SwitchRow(
                title = "Only on Wi-Fi",
                subtitle = "Queue deliveries until an unmetered network is available",
                checked = current.wifiOnly,
                onCheckedChange = viewModel::setWifiOnly,
            )

            ThinDivider()
            SectionHeader("Logs")
            SwitchRow(
                title = "Keep delivery logs",
                subtitle = "Record each webhook attempt (status, duration, error). Header values are never logged.",
                checked = current.keepDeliveryLogs,
                onCheckedChange = viewModel::setKeepLogs,
            )
            SettingsItem(
                "Log retention",
                "${current.logRetentionDays} days",
                enabled = current.keepDeliveryLogs,
                onClick = { dialog = SettingsDialog.LOG_RETENTION },
            )
            SettingsItem("Clear logs", onClick = { dialog = SettingsDialog.CLEAR_LOGS })

            ThinDivider()
            SectionHeader("Reliability")
            SettingsItem(
                "Battery optimisation",
                if (batteryUnrestricted) "Unrestricted — background delivery is not limited" else
                    "Optimised — some devices may delay forwarding. Tap to change.",
                onClick = { context.openBatteryOptimizationSettings() },
            )
            SettingsItem("App permissions", "Manage SMS permission in system settings", onClick = { context.openAppSettings() })

            ThinDivider()
            SectionHeader("Privacy & data")
            SettingsItem(
                "Local data",
                "Messages, logs and configuration are stored only on this device and sent only to your webhooks. " +
                    "No analytics or crash reporting is included. Device backup is disabled.",
            )
            SettingsItem(
                "Data encryption",
                "App data is protected by Android file-based encryption. Secret header values are additionally " +
                    "encrypted with a hardware-backed Android Keystore key.",
            )
            SettingsItem("Export configuration", "Save webhook settings to a JSON file", onClick = { dialog = SettingsDialog.EXPORT })
            SettingsItem("Import configuration", "Add webhooks from an exported file (imported disabled)", onClick = {
                importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            })
            SettingsItem(
                "Clear all application data",
                "Delete all messages, webhooks, credentials and settings",
                titleColor = MaterialTheme.colorScheme.error,
                onClick = { dialog = SettingsDialog.CLEAR_ALL },
            )

            ThinDivider()
            SectionHeader("About")
            SettingsItem("How-to guide", "Step-by-step tutorials for every feature", onClick = { context.openUrl(AppLinks.GUIDE) })
            SettingsItem("Privacy policy", onClick = { context.openUrl(AppLinks.PRIVACY) })
            SettingsItem("Terms of use", onClick = { context.openUrl(AppLinks.TERMS) })
            SettingsItem("Contact support", AppLinks.SUPPORT_EMAIL, onClick = { context.emailSupport() })
            SettingsItem("Website", AppLinks.WEBSITE.removePrefix("https://").trimEnd('/'), onClick = { context.openUrl(AppLinks.WEBSITE) })
            SettingsItem("Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            Text("")
        }

        SettingsDialogs(
            dialog = dialog,
            settings = current,
            onDismiss = { dialog = null },
            onOpen = { dialog = it },
            viewModel = viewModel,
            onExport = { includeSecrets ->
                exportWithSecrets = includeSecrets
                exportLauncher.launch("sms-webhook-config.json")
            },
            onClearAll = {
                context.getSystemService(ActivityManager::class.java).clearApplicationUserData()
            },
        )
    }
}

@Composable
private fun SettingsDialogs(
    dialog: SettingsDialog?,
    settings: AppSettings,
    onDismiss: () -> Unit,
    onOpen: (SettingsDialog) -> Unit,
    viewModel: SettingsViewModel,
    onExport: (Boolean) -> Unit,
    onClearAll: () -> Unit,
) {
    when (dialog) {
        null -> Unit
        SettingsDialog.RETENTION_MODE -> ChoiceDialog(
            title = "Keep history",
            options = listOf("By age", "By number of messages", "Forever"),
            selected = when (settings.retention) {
                is RetentionPolicy.ByAge -> "By age"
                is RetentionPolicy.ByCount -> "By number of messages"
                RetentionPolicy.Forever -> "Forever"
            },
            label = { it },
            onSelect = {
                when (it) {
                    "By age" -> onOpen(SettingsDialog.RETENTION_AGE)
                    "By number of messages" -> onOpen(SettingsDialog.RETENTION_COUNT)
                    else -> viewModel.setRetention(RetentionPolicy.Forever)
                }
            },
            onDismiss = { if (dialog == SettingsDialog.RETENTION_MODE) onDismiss() },
        )
        SettingsDialog.RETENTION_AGE -> ChoiceDialog(
            title = "Keep messages for",
            options = RetentionPolicy.AGE_PRESETS,
            selected = (settings.retention as? RetentionPolicy.ByAge)?.days,
            label = { "$it days" },
            onSelect = { viewModel.setRetention(RetentionPolicy.ByAge(it)) },
            onDismiss = onDismiss,
        )
        SettingsDialog.RETENTION_COUNT -> ChoiceDialog(
            title = "Keep the newest",
            options = RetentionPolicy.COUNT_PRESETS + CUSTOM_COUNT,
            selected = (settings.retention as? RetentionPolicy.ByCount)?.maxMessages
                ?.let { if (it in RetentionPolicy.COUNT_PRESETS) it else CUSTOM_COUNT },
            label = { if (it == CUSTOM_COUNT) "Custom…" else "${formatCount(it)} messages" },
            onSelect = {
                if (it == CUSTOM_COUNT) onOpen(SettingsDialog.RETENTION_CUSTOM) else viewModel.setRetention(RetentionPolicy.ByCount(it))
            },
            onDismiss = { if (dialog == SettingsDialog.RETENTION_COUNT) onDismiss() },
        )
        SettingsDialog.RETENTION_CUSTOM -> NumberDialog(
            title = "Messages to keep",
            initial = (settings.retention as? RetentionPolicy.ByCount)?.maxMessages ?: 5_000,
            range = 10..1_000_000,
            onConfirm = { viewModel.setRetention(RetentionPolicy.ByCount(it)) },
            onDismiss = onDismiss,
        )
        SettingsDialog.DELETE_POLICY -> ChoiceDialog(
            title = "Delete SMS after forwarding",
            options = DeleteSmsPolicy.entries,
            selected = settings.deleteSmsPolicy,
            label = ::deletePolicyLabel,
            onSelect = viewModel::setDeletePolicy,
            onDismiss = onDismiss,
        )
        SettingsDialog.RETRIES -> NumberDialog(
            "Default retry count", settings.defaultMaxRetries, WebhookLimits.MIN_RETRIES..WebhookLimits.MAX_RETRIES,
            viewModel::setDefaultRetries, onDismiss,
        )
        SettingsDialog.RETRY_DELAY -> NumberDialog(
            "Default retry delay (seconds)", settings.defaultRetryDelaySeconds,
            WebhookLimits.MIN_RETRY_DELAY_SECONDS..WebhookLimits.MAX_RETRY_DELAY_SECONDS, viewModel::setDefaultRetryDelay, onDismiss,
        )
        SettingsDialog.TIMEOUT -> NumberDialog(
            "Default timeout (seconds)", settings.defaultTimeoutSeconds,
            WebhookLimits.MIN_TIMEOUT_SECONDS..WebhookLimits.MAX_TIMEOUT_SECONDS, viewModel::setDefaultTimeout, onDismiss,
        )
        SettingsDialog.LOG_RETENTION -> ChoiceDialog(
            title = "Keep logs for",
            options = listOf(7, 30, 90, 365),
            selected = settings.logRetentionDays,
            label = { "$it days" },
            onSelect = { viewModel.setLogRetention(it) },
            onDismiss = onDismiss,
        )
        SettingsDialog.CLEAR_HISTORY -> ConfirmDialog(
            "Clear message history?",
            "All stored messages and their delivery records are removed, except messages still waiting to be delivered.",
            "Clear", { viewModel.clearHistory() }, onDismiss, destructive = true,
        )
        SettingsDialog.CLEAR_LOGS -> ConfirmDialog(
            "Clear delivery logs?", "All recorded attempts are removed.", "Clear", { viewModel.clearLogs() }, onDismiss, destructive = true,
        )
        SettingsDialog.CLEAR_ALL -> ConfirmDialog(
            "Clear all application data?",
            "This deletes every message, webhook, credential, log and setting, and closes the app. This cannot be undone.",
            "Delete everything", onClearAll, onDismiss, destructive = true,
        )
        SettingsDialog.EXPORT -> ChoiceDialog(
            title = "Export configuration",
            options = listOf(false, true),
            selected = false,
            label = { if (it) "Include secret header values" else "Without secrets (recommended)" },
            onSelect = { include -> if (include) onOpen(SettingsDialog.EXPORT_SECRETS_WARNING) else onExport(false) },
            onDismiss = { if (dialog == SettingsDialog.EXPORT) onDismiss() },
            footer = {
                Text(
                    "Without secrets, credentials such as Authorization headers must be re-entered after import.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
        SettingsDialog.EXPORT_SECRETS_WARNING -> ConfirmDialog(
            title = "Export credentials in plain text?",
            message = "The file will contain your API keys and tokens unencrypted. Anyone with the file can use them. " +
                "Store it securely and delete it after use.",
            confirmLabel = "Export with secrets",
            destructive = true,
            onConfirm = { onExport(true) },
            onDismiss = onDismiss,
        )
    }
}

private fun retentionLabel(policy: RetentionPolicy): String = when (policy) {
    RetentionPolicy.Forever -> "Keep forever"
    is RetentionPolicy.ByAge -> "Keep the last ${policy.days} days"
    is RetentionPolicy.ByCount -> "Keep the newest ${formatCount(policy.maxMessages)} messages"
}

private fun deletePolicyLabel(policy: DeleteSmsPolicy): String = when (policy) {
    DeleteSmsPolicy.DISABLED -> "Disabled"
    DeleteSmsPolicy.AFTER_ALL_SUCCEEDED -> "After all matching webhooks succeed"
    DeleteSmsPolicy.AFTER_FIRST_SUCCESS -> "After the first successful webhook"
}

private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val buffer = java.io.ByteArrayOutputStream()
    val chunk = ByteArray(8_192)
    while (buffer.size() < limit) {
        val read = read(chunk, 0, minOf(chunk.size, limit - buffer.size()))
        if (read < 0) break
        buffer.write(chunk, 0, read)
    }
    return buffer.toByteArray()
}

private fun Context.openUrl(url: String) {
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun Context.emailSupport() {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${AppLinks.SUPPORT_EMAIL}"))
        .putExtra(Intent.EXTRA_SUBJECT, "SMS Webhook ${BuildConfig.VERSION_NAME} support")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}

private const val CUSTOM_COUNT = -1
private const val MAX_IMPORT_BYTES = 2 * 1024 * 1024
