package com.pledgerio.app.domain.repository

import com.pledgerio.app.shared.Resource

interface InvoiceTextReader {
    suspend fun extractText(imageUri: String): Resource<String>
}
