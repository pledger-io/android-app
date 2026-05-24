package com.pledgerio.app.domain.repository

import com.pledgerio.app.domain.common.Resource

interface InvoiceTextReader {
    suspend fun extractText(imageUri: String): Resource<String>
}
