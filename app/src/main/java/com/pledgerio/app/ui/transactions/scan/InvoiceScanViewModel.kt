package com.pledgerio.app.ui.transactions.scan

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pledgerio.app.R
import com.pledgerio.app.data.ocr.InvoiceTextExtractor
import com.pledgerio.app.domain.model.TransactionExtractionDraft
import com.pledgerio.app.domain.repository.TransactionRepository
import com.pledgerio.app.util.Resource
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class InvoiceScanStage {
    IDLE,
    READING_TEXT,
    EXTRACTING_TRANSACTION,
}

data class InvoiceScanUiState(
    val stage: InvoiceScanStage = InvoiceScanStage.IDLE,
    val selectedImageUri: Uri? = null,
    val extractedText: String = "",
    val draft: TransactionExtractionDraft? = null,
    val error: String? = null,
) {
    val isWorking: Boolean
        get() = stage != InvoiceScanStage.IDLE
}

@HiltViewModel
class InvoiceScanViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val invoiceTextExtractor: InvoiceTextExtractor,
    private val transactionRepository: TransactionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(InvoiceScanUiState())
    val uiState: StateFlow<InvoiceScanUiState> = _uiState.asStateFlow()

    fun onTextChanged(value: String) {
        _uiState.update {
            it.copy(
                extractedText = value,
                error = null,
                draft = null,
            )
        }
    }

    fun onImageSelected(uri: Uri) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    selectedImageUri = uri,
                    stage = InvoiceScanStage.READING_TEXT,
                    error = null,
                    draft = null,
                )
            }
            when (val ocrResult = invoiceTextExtractor.extractText(uri)) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            extractedText = ocrResult.data,
                            stage = InvoiceScanStage.EXTRACTING_TRANSACTION,
                        )
                    }
                    extractFromCurrentText()
                }

                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            stage = InvoiceScanStage.IDLE,
                            error = ocrResult.message,
                        )
                    }
                }

                is Resource.Loading -> Unit
            }
        }
    }

    fun onCameraCaptureLaunchFailed() {
        _uiState.update {
            it.copy(error = context.getString(R.string.invoice_scan_error_open_camera_failed))
        }
    }

    fun extractFromCurrentText() {
        val text = _uiState.value.extractedText.trim()
        if (text.isBlank()) {
            _uiState.update {
                it.copy(error = context.getString(R.string.invoice_scan_error_no_text))
            }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    stage = InvoiceScanStage.EXTRACTING_TRANSACTION,
                    error = null,
                    draft = null,
                )
            }
            when (val result = transactionRepository.extractTransactionFromText(text)) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            stage = InvoiceScanStage.IDLE,
                            draft = result.data.copy(rawText = text),
                        )
                    }
                }

                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            stage = InvoiceScanStage.IDLE,
                            error = context.getString(R.string.invoice_scan_error_extract_failed),
                        )
                    }
                }

                is Resource.Loading -> Unit
            }
        }
    }
}
