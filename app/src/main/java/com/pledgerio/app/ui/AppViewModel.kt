package com.pledgerio.app.ui

import androidx.lifecycle.ViewModel
import com.pledgerio.app.util.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    val sessionManager: SessionManager,
) : ViewModel()
