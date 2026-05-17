package com.pledgerio.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.pledgerio.app.domain.model.FinanceExperienceMode
import com.pledgerio.app.domain.model.ThemeMode
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.activity.compose.LocalActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.pledgerio.app.domain.model.AppLocale
import com.pledgerio.app.ui.util.localizedDescription
import com.pledgerio.app.ui.util.localizedName
import com.pledgerio.app.util.BiometricAvailability
import com.pledgerio.app.BuildConfig
import com.pledgerio.app.R
import com.pledgerio.app.ui.components.PledgerTopBar
import com.pledgerio.app.ui.theme.ExpenseRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCategories: () -> Unit,
    onNavigateToTags: () -> Unit,
    onNavigateToChangeServer: () -> Unit,
    onLogout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    issueReportViewModel: IssueReportViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val issueReportState by issueReportViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showLogoutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(issueReportState.readyToOpen) {
        val report = issueReportState.readyToOpen ?: return@LaunchedEffect
        report.clipboardText?.let { text ->
            val clipboard = ContextCompat.getSystemService(context, ClipboardManager::class.java)
            clipboard?.setPrimaryClip(ClipData.newPlainText("Pledger bug report", text))
        }
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(report.issueUrl)))
        snackbarHostState.showSnackbar(
            if (report.clipboardText != null) {
                context.getString(R.string.report_issue_open_github_with_clipboard)
            } else {
                context.getString(R.string.report_issue_open_github)
            },
        )
        issueReportViewModel.clearReadyToOpen()
    }

    ReportIssueDialog(
        state = issueReportState,
        onDismiss = issueReportViewModel::dismissDialog,
        onTitleChange = issueReportViewModel::onTitleChange,
        onDescriptionChange = issueReportViewModel::onDescriptionChange,
        onSubmit = issueReportViewModel::submit,
    )

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Sign Out") },
            text = { Text("Are you sure you want to sign out?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.logout(onLoggedOut = onLogout)
                    }
                ) {
                    Text("Sign Out", color = ExpenseRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (uiState.showCurrencyPicker) {
        AlertDialog(
            onDismissRequest = viewModel::dismissCurrencyPicker,
            title = { Text("Display currency") },
            text = {
                if (uiState.currencies.isEmpty()) {
                    Text("Loading currencies…")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(uiState.currencies, key = { it.code }) { currency ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.selectCurrency(currency.code) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = currency.code == uiState.displayCurrencyCode,
                                    onClick = { viewModel.selectCurrency(currency.code) },
                                )
                                Column {
                                    Text(
                                        text = currency.code,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        text = "${currency.name} (${currency.symbol})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissCurrencyPicker) {
                    Text("Cancel")
                }
            },
        )
    }

    if (uiState.showThemePicker) {
        AlertDialog(
            onDismissRequest = viewModel::dismissThemePicker,
            title = { Text(stringResource(R.string.settings_theme)) },
            text = {
                Column {
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.selectTheme(mode) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = uiState.themeMode == mode,
                                onClick = { viewModel.selectTheme(mode) },
                            )
                            Text(
                                text = mode.localizedName(),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissThemePicker) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (uiState.showLanguagePicker) {
        val activity = LocalActivity.current as? AppCompatActivity
        AlertDialog(
            onDismissRequest = viewModel::dismissLanguagePicker,
            title = { Text(stringResource(R.string.settings_language)) },
            text = {
                Column {
                    AppLocale.entries.forEach { locale ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.selectAppLocale(locale) {
                                        activity?.recreate()
                                    }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = uiState.appLocale == locale,
                                onClick = {
                                    viewModel.selectAppLocale(locale) {
                                        activity?.recreate()
                                    }
                                },
                            )
                            Text(
                                text = locale.localizedName(),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissLanguagePicker) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (uiState.showExperiencePicker) {
        AlertDialog(
            onDismissRequest = viewModel::dismissExperiencePicker,
            title = { Text(stringResource(R.string.settings_experience)) },
            text = {
                Column {
                    FinanceExperienceMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.selectFinanceExperienceMode(mode) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = uiState.financeExperienceMode == mode,
                                onClick = { viewModel.selectFinanceExperienceMode(mode) },
                            )
                            Column {
                                Text(
                                    text = mode.localizedName(),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    text = mode.localizedDescription(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissExperiencePicker) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            PledgerTopBar(
                title = stringResource(R.string.settings_title),
                subtitle = stringResource(R.string.settings_subtitle),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            item {
                SettingsSection(stringResource(R.string.settings_section_server)) {
                    SettingsItem(
                        icon = Icons.Default.Storage,
                        title = stringResource(R.string.settings_change_server),
                        subtitle = uiState.serverUrl
                            ?: stringResource(R.string.settings_not_configured),
                        onClick = onNavigateToChangeServer,
                    )
                }
            }

            item {
                val biometricSubtitle = when (uiState.biometricAvailability) {
                    BiometricAvailability.NotEnrolled ->
                        stringResource(R.string.settings_biometric_not_enrolled)
                    BiometricAvailability.NotAvailable,
                    BiometricAvailability.Unsupported,
                    -> stringResource(R.string.settings_biometric_unavailable)
                    BiometricAvailability.Available ->
                        if (uiState.biometricEnabled) {
                            stringResource(R.string.settings_biometric_enabled)
                        } else {
                            stringResource(R.string.settings_biometric_disabled)
                        }
                }
                SettingsSection(stringResource(R.string.settings_section_security)) {
                    SettingsToggle(
                        icon = Icons.Default.Fingerprint,
                        title = stringResource(R.string.settings_biometric_login),
                        subtitle = biometricSubtitle,
                        checked = uiState.biometricEnabled,
                        enabled = uiState.biometricAvailability.canEnable,
                        onCheckedChange = { enabled ->
                            if (!enabled) {
                                viewModel.toggleBiometric(false)
                                return@SettingsToggle
                            }
                            val activity = context as? AppCompatActivity ?: return@SettingsToggle
                            viewModel.enableBiometric(
                                activity = activity,
                                enableTitle = context.getString(R.string.settings_biometric_enable_title),
                                enableSubtitle = context.getString(R.string.settings_biometric_enable_subtitle),
                                cancelLabel = context.getString(R.string.cancel),
                                onError = { message ->
                                    scope.launch {
                                        snackbarHostState.showSnackbar(message)
                                    }
                                },
                            )
                        },
                    )
                }
            }

            item {
                SettingsSection(stringResource(R.string.settings_section_data)) {
                    SettingsItem(
                        icon = Icons.Default.Category,
                        title = stringResource(R.string.settings_categories),
                        subtitle = stringResource(R.string.settings_categories_subtitle),
                        onClick = onNavigateToCategories,
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsItem(
                        icon = Icons.Default.Tag,
                        title = stringResource(R.string.settings_tags),
                        subtitle = stringResource(R.string.settings_tags_subtitle),
                        onClick = onNavigateToTags,
                    )
                }
            }

            item {
                SettingsSection(stringResource(R.string.settings_section_preferences)) {
                    SettingsItem(
                        icon = Icons.Default.Language,
                        title = stringResource(R.string.settings_language),
                        subtitle = uiState.appLocale.localizedName(),
                        onClick = viewModel::openLanguagePicker,
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsItem(
                        icon = Icons.Default.Language,
                        title = stringResource(R.string.settings_display_currency),
                        subtitle = uiState.displayCurrencyLabel,
                        onClick = viewModel::openCurrencyPicker,
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsItem(
                        icon = Icons.Default.DarkMode,
                        title = stringResource(R.string.settings_theme),
                        subtitle = uiState.themeMode.localizedName(),
                        onClick = viewModel::openThemePicker,
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsItem(
                        icon = Icons.Default.Notifications,
                        title = stringResource(R.string.settings_notifications),
                        subtitle = stringResource(R.string.settings_notifications_coming_soon),
                        onClick = {},
                        enabled = false,
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsItem(
                        icon = Icons.Default.Speed,
                        title = stringResource(R.string.settings_experience),
                        subtitle = stringResource(
                            R.string.settings_experience_format,
                            uiState.financeExperienceMode.localizedName(),
                            uiState.financeExperienceMode.localizedDescription(),
                        ),
                        onClick = viewModel::openExperiencePicker,
                    )
                }
            }

            item {
                SettingsSection(stringResource(R.string.settings_section_about)) {
                    SettingsItem(
                        icon = Icons.Default.BugReport,
                        title = stringResource(R.string.report_issue_settings_title),
                        subtitle = stringResource(R.string.report_issue_settings_subtitle),
                        onClick = issueReportViewModel::openDialog,
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsItem(
                        icon = Icons.Default.Info,
                        title = stringResource(R.string.settings_version),
                        subtitle = BuildConfig.VERSION_NAME,
                        onClick = { },
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !uiState.isLoggingOut) { showLogoutDialog = true }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        tint = ExpenseRed,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        stringResource(R.string.settings_sign_out),
                        style = MaterialTheme.typography.bodyLarge,
                        color = ExpenseRed,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        content()
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsToggle(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}
