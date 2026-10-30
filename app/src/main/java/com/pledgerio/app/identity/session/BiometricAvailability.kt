package com.pledgerio.app.identity.session

sealed class BiometricAvailability {
    data object Available : BiometricAvailability()

    data object NotAvailable : BiometricAvailability()

    data object NotEnrolled : BiometricAvailability()

    data object Unsupported : BiometricAvailability()

    val canEnable: Boolean
        get() = this is Available
}
