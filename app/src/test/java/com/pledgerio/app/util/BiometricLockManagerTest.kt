package com.pledgerio.app.util

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BiometricLockManagerTest {

    private val sessionManager = mockk<SessionManager>()
    private lateinit var lockManager: BiometricLockManager

    @Before
    fun setUp() {
        lockManager = BiometricLockManager(sessionManager)
    }

    @Test
    fun `onColdStart requires unlock when biometric enabled and logged in`() {
        every { sessionManager.isBiometricEnabled() } returns true
        every { sessionManager.isLoggedIn() } returns true

        lockManager.onColdStart()

        assertTrue(lockManager.requiresUnlock.value)
    }

    @Test
    fun `onColdStart does not lock when biometric disabled`() {
        every { sessionManager.isBiometricEnabled() } returns false
        every { sessionManager.isLoggedIn() } returns true

        lockManager.onColdStart()

        assertFalse(lockManager.requiresUnlock.value)
    }

    @Test
    fun `onUnlocked clears lock requirement`() {
        every { sessionManager.isBiometricEnabled() } returns true
        every { sessionManager.isLoggedIn() } returns true
        lockManager.onColdStart()

        lockManager.onUnlocked()

        assertFalse(lockManager.requiresUnlock.value)
    }
}
