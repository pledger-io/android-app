package com.pledgerio.app.domain.model

/**
 * Outcome of password authentication before the UI proceeds past login.
 *
 * [MfaRequired] means a pre-verification JWT is held in memory only; full
 * [FullyAuthenticated] session activation happens after TOTP verify.
 */
sealed class LoginResult {
    data object FullyAuthenticated : LoginResult()
    data object MfaRequired : LoginResult()
}
