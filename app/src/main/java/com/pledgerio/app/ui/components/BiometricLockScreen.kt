package com.pledgerio.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.pledgerio.app.R
import com.pledgerio.app.util.BiometricAuthenticator

@Composable
fun BiometricLockScreen(
    activity: FragmentActivity,
    biometricAuthenticator: BiometricAuthenticator,
    onUnlocked: () -> Unit,
    onSignOut: () -> Unit,
    autoPrompt: Boolean = true,
) {
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var promptShown by remember { mutableStateOf(false) }

    fun showPrompt() {
        biometricAuthenticator.authenticate(
            activity = activity,
            title = activity.getString(R.string.biometric_unlock_title),
            subtitle = activity.getString(R.string.biometric_unlock_subtitle),
            negativeButtonText = activity.getString(R.string.cancel),
            onSuccess = {
                errorMessage = null
                onUnlocked()
            },
            onError = { errorMessage = it },
        )
    }

    LaunchedEffect(autoPrompt) {
        if (autoPrompt && !promptShown) {
            promptShown = true
            showPrompt()
        }
    }

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Fingerprint,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.biometric_unlock_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.biometric_unlock_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            errorMessage?.let { message ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = { showPrompt() }) {
                Text(stringResource(R.string.biometric_unlock_action))
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onSignOut) {
                Text(stringResource(R.string.biometric_unlock_sign_out))
            }
        }
    }
}
