package com.pledgerio.app.domain.usecase

import com.pledgerio.app.domain.model.TransactionExtractionDraft
import com.pledgerio.app.domain.repository.InvoiceTextReader
import com.pledgerio.app.domain.repository.TransactionRepository
import com.pledgerio.app.util.Resource
import javax.inject.Inject

class ProcessInvoiceScanUseCase @Inject constructor(
    private val invoiceTextReader: InvoiceTextReader,
    private val transactionRepository: TransactionRepository,
) {
    suspend fun extractTextFromImage(imageUri: String): Resource<String> =
        invoiceTextReader.extractText(imageUri)

    suspend fun extractDraftFromText(text: String): Resource<TransactionExtractionDraft> {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return Resource.Error("No text found in document")
        return when (val result = transactionRepository.extractTransactionFromText(trimmed)) {
            is Resource.Success -> Resource.Success(result.data.copy(rawText = trimmed))
            is Resource.Error -> result
            is Resource.Loading -> result
        }
    }
}
