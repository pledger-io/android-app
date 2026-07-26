package com.pledgerio.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pledgerio.app.domain.model.ThemeMode
import com.pledgerio.app.domain.repository.AuthRepository
import com.pledgerio.app.util.BiometricAuthenticator
import com.pledgerio.app.util.BiometricLockManager
import com.pledgerio.app.util.NetworkMonitor
import com.pledgerio.app.util.SessionManager
import com.pledgerio.app.util.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    val sessionManager: SessionManager,
    val biometricLockManager: BiometricLockManager,
    val biometricAuthenticator: BiometricAuthenticator,
    private val authRepository: AuthRepository,
    userPreferences: UserPreferences,
    networkMonitor: NetworkMonitor,
) : ViewModel() {
    val themeMode: StateFlow<ThemeMode> = userPreferences.themeMode
    private val _biometricSignOutFailed = MutableStateFlow(false)
    val biometricSignOutFailed: StateFlow<Boolean> = _biometricSignOutFailed.asStateFlow()
    private val _biometricSignOutInProgress = MutableStateFlow(false)
    val biometricSignOutInProgress: StateFlow<Boolean> =
        _biometricSignOutInProgress.asStateFlow()

    /**
     * Initial value `true` avoids a single-frame "offline" flash on cold start before the
     * first [NetworkMonitor] emission arrives.
     */
    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = true,
    )

    fun signOutFromBiometricLock(onComplete: () -> Unit) {
        viewModelScope.launch {
            _biometricSignOutFailed.value = false
            _biometricSignOutInProgress.value = true
            try {
                authRepository.logout()
                biometricLockManager.onBiometricDisabled()
                onComplete()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _biometricSignOutFailed.value = true
            } finally {
                _biometricSignOutInProgress.value = false
            }
        }
    }
}
