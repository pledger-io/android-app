package com.pledgerio.app.util

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountIconUrlProvider @Inject constructor(
    private val sessionManager: SessionManager,
) {
    fun fileUrl(fileCode: String?): String? {
        if (fileCode.isNullOrBlank()) return null
        val baseUrl = sessionManager.getBaseUrl()?.trimEnd('/') ?: return null
        return "$baseUrl/v2/api/files/$fileCode"
    }
}
