package com.pledgerio.app.ui.transactions.scan

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.pledgerio.app.R
import com.pledgerio.app.domain.model.TransactionExtractionDraft
import com.pledgerio.app.ui.components.PledgerCard
import com.pledgerio.app.ui.components.PledgerTopBar
import com.pledgerio.app.util.formatCurrency
import java.io.File
import kotlin.math.roundToInt

@Composable
fun InvoiceScanScreen(
    onNavigateBack: () -> Unit,
    onUseDraft: (TransactionExtractionDraft) -> Unit,
    onManualEntry: () -> Unit,
    viewModel: InvoiceScanViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val extractedDraft = uiState.draft
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    val scanProgress = when (uiState.stage) {
        InvoiceScanStage.IDLE -> 0f
        InvoiceScanStage.READING_TEXT -> 0.4f
        InvoiceScanStage.EXTRACTING_TRANSACTION -> 0.8f
    }
    val canExtract = uiState.extractedText.isNotBlank() && !uiState.isWorking
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) viewModel.onImageSelected(uri)
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        val capturedUri = pendingCameraUri
        pendingCameraUri = null
        if (success && capturedUri != null) {
            viewModel.onImageSelected(capturedUri)
        }
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
        bottomBar = {
            if (extractedDraft != null) {
                PledgerCard(
                    modifier = Modifier.padding(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 8.dp,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.invoice_scan_ready_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { onUseDraft(extractedDraft) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp),
                    ) {
                        Text(stringResource(R.string.invoice_scan_use_draft_button))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onManualEntry,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.invoice_scan_manual_entry))
                    }
                }
            }
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

            PledgerCard {
                Text(
                    text = stringResource(R.string.invoice_scan_steps_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                StepPillRow(
                    currentStage = uiState.stage,
                    hasSelectedImage = uiState.selectedImageUri != null,
                    hasDraft = uiState.draft != null,
                )
                if (uiState.isWorking) {
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { scanProgress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = when (uiState.stage) {
                            InvoiceScanStage.READING_TEXT ->
                                stringResource(R.string.invoice_scan_reading_text)
                            InvoiceScanStage.EXTRACTING_TRANSACTION ->
                                stringResource(R.string.invoice_scan_extracting_transaction)
                            InvoiceScanStage.IDLE -> ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            PledgerCard {
                Text(
                    text = stringResource(R.string.invoice_scan_import_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.invoice_scan_import_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        runCatching {
                            createTempImageUri(context).also { uri ->
                                pendingCameraUri = uri
                                cameraLauncher.launch(uri)
                            }
                        }.onFailure {
                            pendingCameraUri = null
                            viewModel.onCameraCaptureLaunchFailed()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp),
                    enabled = !uiState.isWorking,
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = null,
                    )
                    Text(
                        text = stringResource(R.string.invoice_scan_take_photo),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp),
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
            }

            uiState.selectedImageUri?.let { uri ->
                PledgerCard {
                    Text(
                        text = stringResource(R.string.invoice_scan_preview_image_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    AsyncImage(
                        model = uri,
                        contentDescription = stringResource(R.string.invoice_scan_image_preview),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .height(180.dp),
                    )
                }
            }

            PledgerCard {
                Text(
                    text = stringResource(R.string.invoice_scan_text_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.invoice_scan_text_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = uiState.extractedText,
                    onValueChange = viewModel::onTextChanged,
                    label = { Text(stringResource(R.string.invoice_scan_text_label)) },
                    placeholder = { Text(stringResource(R.string.invoice_scan_text_placeholder)) },
                    minLines = 4,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = viewModel::extractFromCurrentText,
                    enabled = canExtract,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null)
                    Text(
                        text = stringResource(R.string.invoice_scan_extract_button),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            uiState.error?.takeIf { it.isNotBlank() }?.let { message ->
                PledgerCard(
                    modifier = Modifier
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(16.dp),
                        ),
                ) {
                    Text(
                        text = stringResource(R.string.error_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            uiState.draft?.let { draft ->
                DraftPreviewCard(
                    draft = draft,
                )
            }

            Spacer(
                modifier = Modifier.height(
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp,
                ),
            )
        }
    }
}

private fun createTempImageUri(context: Context): Uri {
    val imageDirectory = File(context.cacheDir, "invoice-scan").apply {
        if (!exists()) mkdirs()
    }
    val imageFile = File.createTempFile(
        "invoice-scan-",
        ".jpg",
        imageDirectory,
    )
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        imageFile,
    )
}

@Composable
private fun DraftPreviewCard(
    draft: TransactionExtractionDraft,
) {
    val confidence = draft.confidence?.coerceIn(0.0, 1.0)
    val confidencePercent = confidence?.times(100)?.roundToInt()
    val confidenceColor = when {
        confidence == null -> MaterialTheme.colorScheme.onSurfaceVariant
        confidence >= 0.85 -> MaterialTheme.colorScheme.primary
        confidence >= 0.60 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    val confidenceLabel = when {
        confidence == null -> stringResource(R.string.settings_not_configured)
        confidence >= 0.85 -> stringResource(R.string.invoice_scan_confidence_high, confidencePercent ?: 0)
        confidence >= 0.60 -> stringResource(R.string.invoice_scan_confidence_medium, confidencePercent ?: 0)
        else -> stringResource(R.string.invoice_scan_confidence_low, confidencePercent ?: 0)
    }

    PledgerCard {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        )
        {
            Text(
                text = stringResource(R.string.invoice_scan_preview_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            ConfidenceBadge(
                text = confidenceLabel,
                color = confidenceColor,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        DraftLine(
            icon = Icons.Default.Description,
            label = stringResource(R.string.transaction_description_label),
            value = draft.description,
        )
        DraftLine(
            icon = Icons.Default.Sell,
            label = stringResource(R.string.transaction_amount_label),
            value = draft.amount?.formatCurrency(draft.currency ?: "EUR"),
        )
        DraftLine(
            icon = Icons.Default.CalendarMonth,
            label = stringResource(R.string.transaction_date_label),
            value = draft.date?.toString(),
        )
        DraftLine(
            icon = Icons.Default.Person,
            label = stringResource(R.string.invoice_scan_preview_counterparty),
            value = draft.targetName ?: draft.sourceName,
        )
        if (draft.description.isNullOrBlank() || draft.amount == null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.invoice_scan_draft_incomplete_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DraftLine(
    icon: ImageVector,
    label: String,
    value: String?,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(18.dp)
                .padding(top = 2.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value?.takeIf { it.isNotBlank() } ?: stringResource(R.string.settings_not_configured),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun ConfidenceBadge(
    text: String,
    color: Color,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun StepPillRow(
    currentStage: InvoiceScanStage,
    hasSelectedImage: Boolean,
    hasDraft: Boolean,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        StepPill(
            label = stringResource(R.string.invoice_scan_step_import),
            isActive = currentStage == InvoiceScanStage.IDLE && !hasSelectedImage,
            isCompleted = hasSelectedImage,
            modifier = Modifier.weight(1f),
        )
        StepPill(
            label = stringResource(R.string.invoice_scan_step_extract),
            isActive = currentStage == InvoiceScanStage.READING_TEXT ||
                currentStage == InvoiceScanStage.EXTRACTING_TRANSACTION ||
                (hasSelectedImage && !hasDraft),
            isCompleted = hasDraft,
            modifier = Modifier.weight(1f),
        )
        StepPill(
            label = stringResource(R.string.invoice_scan_step_review),
            isActive = hasDraft,
            isCompleted = hasDraft,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StepPill(
    label: String,
    isActive: Boolean,
    isCompleted: Boolean,
    modifier: Modifier = Modifier,
) {
    val tone = when {
        isCompleted -> MaterialTheme.colorScheme.primary
        isActive -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val backgroundAlpha = when {
        isCompleted -> 0.18f
        isActive -> 0.18f
        else -> 0.08f
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(tone.copy(alpha = backgroundAlpha))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tone,
            fontWeight = if (isActive || isCompleted) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}
