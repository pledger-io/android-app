package com.pledgerio.app.ui.settings

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pledgerio.app.R
import com.pledgerio.app.ui.components.PledgerTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MfaSetupScreen(
    onNavigateBack: () -> Unit,
    viewModel: MfaSetupViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val enabledMessage = stringResource(R.string.mfa_setup_enabled_success)
    val disabledMessage = stringResource(R.string.mfa_setup_disabled_success)

    LaunchedEffect(uiState.completedMessage) {
        when (uiState.completedMessage) {
            "enabled" -> {
                snackbarHostState.showSnackbar(enabledMessage)
                viewModel.consumeCompletedMessage()
            }
            "disabled" -> {
                snackbarHostState.showSnackbar(disabledMessage)
                viewModel.consumeCompletedMessage()
            }
        }
    }

    if (uiState.showDisableConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDisableConfirm,
            title = { Text(stringResource(R.string.mfa_setup_disable_title)) },
            text = { Text(stringResource(R.string.mfa_setup_disable_confirm)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDisable) {
                    Text(stringResource(R.string.mfa_setup_disable))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDisableConfirm) {
                    Text(stringResource(R.string.mfa_setup_cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            PledgerTopBar(
                title = stringResource(R.string.mfa_setup_title),
                subtitle = stringResource(R.string.mfa_setup_subtitle),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.mfa_setup_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            uiState.isLoading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.mfaEnabled == true -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = stringResource(R.string.mfa_setup_disable_explain),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    uiState.error?.let { message ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(message, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    OutlinedButton(
                        onClick = viewModel::requestDisable,
                        enabled = !uiState.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(stringResource(R.string.mfa_setup_disable))
                        }
                    }
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.mfa_setup_enroll_explain),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    val qrBytes = uiState.qrPng
                    if (qrBytes != null) {
                        val bitmap = remember(qrBytes) {
                            BitmapFactory.decodeByteArray(qrBytes, 0, qrBytes.size)
                        }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = stringResource(R.string.mfa_setup_qr_content_description),
                                modifier = Modifier.size(200.dp),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    } else if (uiState.error == null) {
                        CircularProgressIndicator()
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    OutlinedTextField(
                        value = uiState.code,
                        onValueChange = viewModel::onCodeChanged,
                        label = { Text(stringResource(R.string.mfa_setup_code_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { viewModel.enable() },
                        ),
                        isError = uiState.error != null,
                        supportingText = {
                            uiState.error?.let {
                                Text(it, color = MaterialTheme.colorScheme.error)
                            }
                        },
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = viewModel::enable,
                        enabled = uiState.code.length >= 4 && !uiState.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text(stringResource(R.string.mfa_setup_enable))
                        }
                    }
                    if (uiState.qrPng == null && uiState.error != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(onClick = viewModel::refresh) {
                            Text(stringResource(R.string.mfa_setup_retry))
                        }
                    }
                }
            }
        }
    }
}
