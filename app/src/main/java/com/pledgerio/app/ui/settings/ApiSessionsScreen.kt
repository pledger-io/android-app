package com.pledgerio.app.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.pledgerio.app.R
import com.pledgerio.app.domain.model.ApiSession
import com.pledgerio.app.ui.components.EmptyScreen
import com.pledgerio.app.ui.components.ErrorScreen
import com.pledgerio.app.ui.components.LoadingScreen
import com.pledgerio.app.ui.components.PledgerCard
import com.pledgerio.app.ui.components.PledgerTopBar
import com.pledgerio.app.ui.theme.ExpenseRed
import com.pledgerio.app.ui.theme.PledgerThemeExt
import com.pledgerio.app.util.formatDisplay
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiSessionsScreen(
    onNavigateBack: () -> Unit,
    viewModel: ApiSessionsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tokenCopiedMessage = stringResource(R.string.api_sessions_token_copied)
    val zone = remember { ZoneId.systemDefault() }

    LaunchedEffect(uiState.snackbarMessage) {
        val message = uiState.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeSnackbar()
    }

    uiState.createForm?.let { form ->
        CreateSessionSheet(
            form = form,
            isSaving = uiState.isSaving,
            onDescriptionChange = viewModel::onDescriptionChanged,
            onPickDateClick = viewModel::showDatePicker,
            onDismiss = viewModel::dismissCreateSheet,
            onCreate = viewModel::createSession,
        )
        if (form.showDatePicker) {
            val initialMillis = form.expires
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = initialMillis,
            )
            DatePickerDialog(
                onDismissRequest = viewModel::dismissDatePicker,
                confirmButton = {
                    TextButton(
                        onClick = {
                            datePickerState.selectedDateMillis?.let { millis ->
                                val selected = Instant.ofEpochMilli(millis)
                                    .atZone(zone)
                                    .toLocalDate()
                                viewModel.onExpiresSelected(selected)
                            } ?: viewModel.dismissDatePicker()
                        },
                    ) {
                        Text(stringResource(R.string.action_ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissDatePicker) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            ) {
                DatePicker(state = datePickerState)
            }
        }
    }

    uiState.createdToken?.let { token ->
        AlertDialog(
            onDismissRequest = viewModel::dismissCreatedToken,
            title = { Text(stringResource(R.string.api_sessions_token_ready_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.api_sessions_token_ready_message))
                    Spacer(modifier = Modifier.height(12.dp))
                    SelectionContainer {
                        Text(
                            text = token,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val clipboard =
                            ContextCompat.getSystemService(context, ClipboardManager::class.java)
                        clipboard?.setPrimaryClip(ClipData.newPlainText("API token", token))
                        viewModel.dismissCreatedToken()
                        scope.launch {
                            snackbarHostState.showSnackbar(tokenCopiedMessage)
                        }
                    },
                ) {
                    Text(stringResource(R.string.api_sessions_copy_token))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissCreatedToken) {
                    Text(stringResource(R.string.action_ok))
                }
            },
        )
    }

    uiState.pendingRevoke?.let { session ->
        AlertDialog(
            onDismissRequest = viewModel::dismissRevoke,
            title = { Text(stringResource(R.string.api_sessions_revoke_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.api_sessions_revoke_message,
                        session.description,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmRevoke,
                    enabled = !uiState.isSaving,
                ) {
                    Text(
                        stringResource(R.string.api_sessions_revoke),
                        color = ExpenseRed,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = viewModel::dismissRevoke,
                    enabled = !uiState.isSaving,
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            PledgerTopBar(
                title = stringResource(R.string.api_sessions_title),
                subtitle = stringResource(R.string.api_sessions_subtitle),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.api_sessions_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = viewModel::openCreateSheet,
                containerColor = PledgerThemeExt.brandAccent,
                text = { Text(stringResource(R.string.api_sessions_create)) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
            )
        },
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            when {
                uiState.isLoading && uiState.sessions.isEmpty() -> LoadingScreen()
                uiState.error != null && uiState.sessions.isEmpty() -> ErrorScreen(
                    message = uiState.error ?: "",
                    onRetry = viewModel::refresh,
                )
                uiState.sessions.isEmpty() -> EmptyScreen(
                    icon = Icons.Default.Key,
                    title = stringResource(R.string.api_sessions_empty_title),
                    message = stringResource(R.string.api_sessions_empty_message),
                )
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item { Spacer(modifier = Modifier.height(4.dp)) }
                        items(uiState.sessions, key = { it.id }) { session ->
                            ApiSessionRow(
                                session = session,
                                onRevoke = { viewModel.requestRevoke(session) },
                            )
                        }
                        item { Spacer(modifier = Modifier.height(88.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ApiSessionRow(
    session: ApiSession,
    onRevoke: () -> Unit,
) {
    PledgerCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.description,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = sessionValidityLabel(session),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.api_sessions_token_masked,
                        session.maskedToken(),
                    ),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRevoke) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = stringResource(R.string.api_sessions_revoke),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun sessionValidityLabel(session: ApiSession): String {
    val from = session.validFrom?.formatDisplay()
    val until = session.validUntil?.formatDisplay()
    return when {
        from != null && until != null ->
            stringResource(R.string.api_sessions_valid_range, from, until)
        until != null ->
            stringResource(R.string.api_sessions_valid_until, until)
        from != null ->
            stringResource(R.string.api_sessions_valid_from, from)
        else -> stringResource(R.string.api_sessions_valid_unknown)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateSessionSheet(
    form: CreateSessionFormState,
    isSaving: Boolean,
    onDescriptionChange: (String) -> Unit,
    onPickDateClick: () -> Unit,
    onDismiss: () -> Unit,
    onCreate: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = stringResource(R.string.api_sessions_create_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = form.description,
                onValueChange = onDescriptionChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.api_sessions_description_label)) },
                supportingText = {
                    Text(stringResource(R.string.api_sessions_description_hint))
                },
                isError = form.descriptionError,
                enabled = !isSaving,
                singleLine = true,
            )
            if (form.descriptionError) {
                Text(
                    text = stringResource(R.string.api_sessions_description_too_short),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = form.expires.formatDisplay(),
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                enabled = !isSaving,
                label = { Text(stringResource(R.string.api_sessions_expires_label)) },
                trailingIcon = {
                    TextButton(onClick = onPickDateClick, enabled = !isSaving) {
                        Text(stringResource(R.string.api_sessions_pick_date))
                    }
                },
            )
            form.serverError?.let { message ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onCreate,
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.api_sessions_create))
                }
            }
            TextButton(
                onClick = onDismiss,
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}
