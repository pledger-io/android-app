package com.pledgerio.app.util

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BiometricAuthenticator @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun getAvailability(): BiometricAvailability {
        val manager = BiometricManager.from(context)
        val authenticators = allowedAuthenticators()
        return when (manager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.Available
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NotEnrolled
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            -> BiometricAvailability.NotAvailable
            else -> BiometricAvailability.Unsupported
        }
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        negativeButtonText: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onCancel: () -> Unit = {},
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                when (errorCode) {
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_CANCELED,
                    -> onCancel()
                    else -> onError(errString.toString())
                }
            }

            override fun onAuthenticationFailed() {
                onError("Authentication failed. Try again.")
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)
        prompt.authenticate(buildPromptInfo(title, subtitle, negativeButtonText))
    }

    private fun buildPromptInfo(
        title: String,
        subtitle: String,
        negativeButtonText: String,
    ): BiometricPrompt.PromptInfo {
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(allowedAuthenticators())
        } else {
            @Suppress("DEPRECATION")
            builder.setDeviceCredentialAllowed(true)
            builder.setNegativeButtonText(negativeButtonText)
        }

        return builder.build()
    }

    private fun allowedAuthenticators(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        } else {
            BIOMETRIC_STRONG
        }
    }
}
