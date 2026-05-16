package com.pledgerio.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pledgerio.app.domain.model.ThemeMode
import com.pledgerio.app.util.NetworkMonitor
import com.pledgerio.app.util.SessionManager
import com.pledgerio.app.util.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    val sessionManager: SessionManager,
    userPreferences: UserPreferences,
    networkMonitor: NetworkMonitor,
) : ViewModel() {
    val themeMode: StateFlow<ThemeMode> = userPreferences.themeMode

    /**
     * Initial value `true` avoids a single-frame "offline" flash on cold start before the
     * first [NetworkMonitor] emission arrives.
     */
    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = true,
    )
}
