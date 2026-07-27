package com.pledgerio.app.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevBuildExpiryTest {

    @Test
    fun `zero expiry is not time limited and never expired`() {
        assertFalse(DevBuildExpiry.isTimeLimited(expiresAtEpochMs = 0L))
        assertFalse(DevBuildExpiry.isExpired(expiresAtEpochMs = 0L, nowEpochMs = Long.MAX_VALUE))
    }

    @Test
    fun `future expiry is time limited but not expired yet`() {
        assertTrue(DevBuildExpiry.isTimeLimited(expiresAtEpochMs = 2_000L))
        assertFalse(DevBuildExpiry.isExpired(expiresAtEpochMs = 2_000L, nowEpochMs = 1_000L))
    }

    @Test
    fun `past or equal expiry is expired`() {
        assertTrue(DevBuildExpiry.isExpired(expiresAtEpochMs = 1_000L, nowEpochMs = 1_000L))
        assertTrue(DevBuildExpiry.isExpired(expiresAtEpochMs = 1_000L, nowEpochMs = 1_001L))
    }
}
