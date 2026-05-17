package com.pledgerio.app.util

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BiometricLockManager @Inject constructor(
    private val sessionManager: SessionManager,
) : DefaultLifecycleObserver {

    private val _requiresUnlock = MutableStateFlow(false)
    val requiresUnlock: StateFlow<Boolean> = _requiresUnlock.asStateFlow()

    fun onColdStart() {
        if (shouldLock()) {
            _requiresUnlock.value = true
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        if (shouldLock()) {
            _requiresUnlock.value = true
        }
    }

    fun onUnlocked() {
        _requiresUnlock.value = false
    }

    fun onBiometricDisabled() {
        _requiresUnlock.value = false
    }

    private fun shouldLock(): Boolean =
        sessionManager.isBiometricEnabled() && sessionManager.isLoggedIn()
}
