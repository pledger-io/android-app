package com.pledgerio.app.identity.domain.model

/**
 * Subset of the user-account profile used by Settings (MFA status is read-only for now).
 */
data class UserProfile(
    val mfa: Boolean,
)
