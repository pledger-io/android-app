package com.pledgerio.app.ui.settings

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.pledgerio.app.BuildConfig
import com.pledgerio.app.R
import com.pledgerio.app.ui.components.PledgerTopBar
import com.pledgerio.app.ui.theme.ExpenseRed
import com.pledgerio.app.ui.util.localizedDescription
import com.pledgerio.app.ui.util.localizedName
import com.pledgerio.app.util.BiometricAvailability
import com.pledgerio.app.util.BudgetAlertLogic
import kotlinx.coroutines.launch

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
    val signOutFailedMessage = stringResource(R.string.settings_sign_out_failed)
    val permissionDeniedMessage = stringResource(R.string.settings_budget_alerts_permission_denied)
    val openSettingsLabel = stringResource(R.string.settings_budget_alerts_open_system_settings)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showLogoutDialog by remember { mutableStateOf(false) }
    var osNotificationsEnabled by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            osNotificationsEnabled =
                NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        osNotificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (!granted) {
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = permissionDeniedMessage,
                    actionLabel = openSettingsLabel,
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        },
                    )
                }
            }
        }
    }

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

    LaunchedEffect(uiState.logoutFailed) {
        if (uiState.logoutFailed) {
            snackbarHostState.showSnackbar(signOutFailedMessage)
            viewModel.consumeLogoutFailure()
        }
    }

    ReportIssueDialog(
        state = issueReportState,
        onDismiss = issueReportViewModel::dismissDialog,
        onTitleChange = issueReportViewModel::onTitleChange,
        onDescriptionChange = issueReportViewModel::onDescriptionChange,
        onSubmit = issueReportViewModel::submit,
    )

    if (showLogoutDialog) {
        SettingsLogoutDialog(
            onDismiss = { showLogoutDialog = false },
            onConfirm = {
                showLogoutDialog = false
                viewModel.logout(onLoggedOut = onLogout)
            },
        )
    }

    if (uiState.showCurrencyPicker) {
        SettingsCurrencyPickerDialog(
            uiState = uiState,
            onDismiss = viewModel::dismissCurrencyPicker,
            onSelectCurrency = viewModel::selectCurrency,
        )
    }

    if (uiState.showThemePicker) {
        SettingsThemePickerDialog(
            selected = uiState.themeMode,
            onDismiss = viewModel::dismissThemePicker,
            onSelect = viewModel::selectTheme,
        )
    }

    if (uiState.showLanguagePicker) {
        val activity = LocalActivity.current as? AppCompatActivity
        SettingsLanguagePickerDialog(
            selected = uiState.appLocale,
            onDismiss = viewModel::dismissLanguagePicker,
            onSelect = { locale ->
                viewModel.selectAppLocale(locale) {
                    activity?.recreate()
                }
            },
        )
    }

    if (uiState.showExperiencePicker) {
        SettingsExperiencePickerDialog(
            selected = uiState.financeExperienceMode,
            onDismiss = viewModel::dismissExperiencePicker,
            onSelect = viewModel::selectFinanceExperienceMode,
        )
    }

    if (uiState.showBudgetAlertThresholdPicker) {
        SettingsBudgetAlertThresholdPickerDialog(
            selected = uiState.budgetAlertThresholdPercent,
            options = BudgetAlertLogic.VALID_THRESHOLDS,
            onDismiss = viewModel::dismissBudgetAlertThresholdPicker,
            onSelect = viewModel::selectBudgetAlertThreshold,
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
                val biometricEnableTitle =
                    stringResource(R.string.settings_biometric_enable_title)
                val biometricEnableSubtitle =
                    stringResource(R.string.settings_biometric_enable_subtitle)
                val cancelLabel = stringResource(R.string.cancel)
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
                                enableTitle = biometricEnableTitle,
                                enableSubtitle = biometricEnableSubtitle,
                                cancelLabel = cancelLabel,
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
                val budgetAlertsSubtitle = when {
                    uiState.budgetAlertsEnabled && !osNotificationsEnabled ->
                        stringResource(R.string.settings_budget_alerts_os_disabled)
                    else -> stringResource(R.string.settings_budget_alerts_subtitle)
                }
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
                    SettingsToggle(
                        icon = Icons.Default.Notifications,
                        title = stringResource(R.string.settings_budget_alerts),
                        subtitle = budgetAlertsSubtitle,
                        checked = uiState.budgetAlertsEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.setBudgetAlertsEnabled(enabled)
                            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS,
                                ) == PackageManager.PERMISSION_GRANTED
                                if (!granted) {
                                    notificationPermissionLauncher.launch(
                                        Manifest.permission.POST_NOTIFICATIONS,
                                    )
                                }
                            }
                            osNotificationsEnabled =
                                NotificationManagerCompat.from(context).areNotificationsEnabled()
                        },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsItem(
                        icon = Icons.Default.Notifications,
                        title = stringResource(R.string.settings_budget_alert_threshold),
                        subtitle = stringResource(
                            R.string.settings_budget_alert_threshold_option,
                            uiState.budgetAlertThresholdPercent,
                        ),
                        onClick = viewModel::openBudgetAlertThresholdPicker,
                        enabled = uiState.budgetAlertsEnabled,
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
