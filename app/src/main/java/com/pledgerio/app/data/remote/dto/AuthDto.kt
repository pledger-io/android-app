package com.pledgerio.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LoginRequest(
    @Json(name = "username") val username: String,
    @Json(name = "password") val password: String,
)

@JsonClass(generateAdapter = true)
data class LoginResponse(
    @Json(name = "username") val username: String = "",
    @Json(name = "access_token") val accessToken: String = "",
    @Json(name = "refresh_token") val refreshToken: String? = null,
    @Json(name = "token_type") val tokenType: String = "Bearer",
    @Json(name = "expires_in") val expiresIn: Long = 0,
)

@JsonClass(generateAdapter = true)
data class RefreshTokenRequest(
    @Json(name = "grant_type") val grantType: String = "refresh_token",
    @Json(name = "refresh_token") val refreshToken: String,
)

@JsonClass(generateAdapter = true)
data class OpenIdConfigResponse(
    @Json(name = "authority") val authority: String,
    @Json(name = "client-id") val clientId: String,
    @Json(name = "client-secret") val clientSecret: String,
)

@JsonClass(generateAdapter = true)
data class SessionRequest(
    @Json(name = "description") val description: String,
    @Json(name = "expires") val expires: String,
)

@JsonClass(generateAdapter = true)
data class SessionResponse(
    @Json(name = "id") val id: Long? = null,
    @Json(name = "description") val description: String = "",
    @Json(name = "token") val token: String = "",
    @Json(name = "valid") val valid: DateRangeDto? = null,
)

@JsonClass(generateAdapter = true)
data class UserProfileResponse(
    @Json(name = "theme") val theme: String = "dark",
    @Json(name = "currency") val currency: String = "EUR",
    @Json(name = "profilePicture") val profilePicture: String? = null,
    @Json(name = "mfa") val mfa: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class Verify2FactorRequest(
    @Json(name = "verificationCode") val verificationCode: String,
)

@JsonClass(generateAdapter = true)
data class Patch2FactorRequest(
    @Json(name = "action") val action: String,
    @Json(name = "verificationCode") val verificationCode: String? = null,
) {
    companion object {
        fun enable(verificationCode: String) = Patch2FactorRequest(
            action = "ENABLE",
            verificationCode = verificationCode,
        )

        fun disable() = Patch2FactorRequest(action = "DISABLE")
    }
}
