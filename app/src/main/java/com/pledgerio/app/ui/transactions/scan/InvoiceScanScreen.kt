package com.pledgerio.app.ui.transactions.scan

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.pledgerio.app.R
import com.pledgerio.app.domain.model.TransactionExtractionDraft
import com.pledgerio.app.ui.components.PledgerCard
import com.pledgerio.app.ui.components.PledgerTopBar
import com.pledgerio.app.util.formatCurrency

@Composable
fun InvoiceScanScreen(
    onNavigateBack: () -> Unit,
    onUseDraft: (TransactionExtractionDraft) -> Unit,
    viewModel: InvoiceScanViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) viewModel.onImageSelected(uri)
    }

    Scaffold(
        topBar = {
            PledgerTopBar(
                title = stringResource(R.string.invoice_scan_title),
                subtitle = stringResource(R.string.invoice_scan_subtitle),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back),
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = { imagePickerLauncher.launch("image/*") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isWorking,
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                )
                Text(
                    text = stringResource(R.string.invoice_scan_pick_image),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            if (uiState.isWorking) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = when (uiState.stage) {
                        InvoiceScanStage.READING_TEXT -> stringResource(R.string.invoice_scan_reading_text)
                        InvoiceScanStage.EXTRACTING_TRANSACTION -> stringResource(R.string.invoice_scan_extracting_transaction)
                        InvoiceScanStage.IDLE -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            uiState.selectedImageUri?.let { uri ->
                PledgerCard {
                    AsyncImage(
                        model = uri,
                        contentDescription = stringResource(R.string.invoice_scan_image_preview),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                    )
                }
            }

            OutlinedTextField(
                value = uiState.extractedText,
                onValueChange = viewModel::onTextChanged,
                label = { Text(stringResource(R.string.invoice_scan_text_label)) },
                placeholder = { Text(stringResource(R.string.invoice_scan_text_placeholder)) },
                minLines = 4,
                maxLines = 10,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    onClick = viewModel::extractFromCurrentText,
                    enabled = !uiState.isWorking,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.invoice_scan_extract_button))
                }
            }

            uiState.error?.takeIf { it.isNotBlank() }?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            uiState.draft?.let { draft ->
                DraftPreviewCard(
                    draft = draft,
                    onUseDraft = { onUseDraft(draft) },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DraftPreviewCard(
    draft: TransactionExtractionDraft,
    onUseDraft: () -> Unit,
) {
    PledgerCard {
        Text(
            text = stringResource(R.string.invoice_scan_preview_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        DraftLine(
            label = stringResource(R.string.transaction_description_label),
            value = draft.description,
        )
        DraftLine(
            label = stringResource(R.string.transaction_amount_label),
            value = draft.amount?.formatCurrency(draft.currency ?: "EUR"),
        )
        DraftLine(
            label = stringResource(R.string.transaction_date_label),
            value = draft.date?.toString(),
        )
        DraftLine(
            label = stringResource(R.string.invoice_scan_preview_counterparty),
            value = draft.targetName ?: draft.sourceName,
        )
        DraftLine(
            label = stringResource(R.string.invoice_scan_preview_confidence),
            value = draft.confidence?.let { String.format("%.0f%%", it * 100) },
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onUseDraft,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.invoice_scan_use_draft_button))
        }
    }
}

@Composable
private fun DraftLine(
    label: String,
    value: String?,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = value?.takeIf { it.isNotBlank() } ?: stringResource(R.string.settings_not_configured),
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(modifier = Modifier.height(6.dp))
}
