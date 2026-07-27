package com.pledgerio.app.util

import com.pledgerio.app.BuildConfig

/**
 * Time-box for sideloaded development APKs from the Development build workflow.
 *
 * Normal local/CI debug builds leave [BuildConfig.DEV_BUILD_EXPIRES_AT_EPOCH_MS] at 0
 * (no expiry). The workflow stamps a future epoch so the APK stops after ~7 days.
 *
 * This is a soft anti-abuse gate (device clock can be changed); it is not DRM.
 */
object DevBuildExpiry {

    fun isTimeLimited(
        expiresAtEpochMs: Long = BuildConfig.DEV_BUILD_EXPIRES_AT_EPOCH_MS,
    ): Boolean = expiresAtEpochMs > 0L

    fun isExpired(
        expiresAtEpochMs: Long = BuildConfig.DEV_BUILD_EXPIRES_AT_EPOCH_MS,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): Boolean = isTimeLimited(expiresAtEpochMs) && nowEpochMs >= expiresAtEpochMs
}
