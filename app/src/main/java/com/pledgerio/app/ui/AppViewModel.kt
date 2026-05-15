package com.pledgerio.app.ui

import androidx.lifecycle.ViewModel
import com.pledgerio.app.domain.model.ThemeMode
import com.pledgerio.app.util.SessionManager
import com.pledgerio.app.util.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    val sessionManager: SessionManager,
    userPreferences: UserPreferences,
) : ViewModel() {
    val themeMode: StateFlow<ThemeMode> = userPreferences.themeMode
}
