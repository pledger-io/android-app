# ADR-006: EncryptedSharedPreferences for Auth Token Storage

**Date:** 2026-05-13
**Status:** Accepted

## Context

The app authenticates with the Pledger.io backend via JWT tokens. These tokens grant full access to the user's financial data and must be stored securely on device.

Options considered:
- **Regular SharedPreferences** — Plaintext on disk, readable with root access or device backup extraction
- **EncryptedSharedPreferences** — AES-256-GCM encryption via Android Keystore, part of AndroidX Security
- **DataStore** — Modern preference storage, but no built-in encryption
- **Room (encrypted with SQLCipher)** — Overkill for key-value auth data
- **Android Keystore directly** — Low-level, complex API for simple key-value storage

## Decision

Use **EncryptedSharedPreferences** from `androidx.security:security-crypto` for storing:
- JWT access token and refresh token
- Token expiry (`expires_in` from login/refresh)
- Server base URL
- Username
- Biometric preference flag

Implementation via `SessionManager` — a singleton class that wraps all secure preference access.

Key configuration:
- Master key: `AES256_GCM` key scheme
- Key encryption: `AES256_SIV`
- Value encryption: `AES256_GCM`

## Consequences

### Positive
- Data encrypted at rest using hardware-backed Android Keystore
- Simple key-value API identical to regular SharedPreferences
- No additional dependencies beyond AndroidX Security
- `SessionManager` centralizes all auth state, making it easy to clear on logout

### Negative
- `security-crypto` is still in alpha (`1.1.0-alpha06`), though widely used in production
- First access is slower than regular SharedPreferences due to decryption
- Cannot be backed up/restored across devices (keys are device-bound)
- If the Keystore is compromised (rooted device), encryption is ineffective

### Security Considerations
- Access token refreshed proactively; failed refresh or 401 after retry clears auth via `clearAuthTokens()`
- `clearAuthTokens()` removes tokens and username but **keeps** server URL (and biometric flag) so logout/auth failure does not force re-entering the server address
- `SessionManager.clearSession()` wipes all prefs (full reset)
- Biometric unlock is an additional optional layer (via `BiometricPrompt`)
