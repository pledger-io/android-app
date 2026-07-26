package com.pledgerio.app.di

import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkModuleTest {

    @Test
    fun `debug HTTP logging excludes credential headers and bodies`() {
        assertEquals(
            HttpLoggingInterceptor.Level.BASIC,
            NetworkModule.secretSafeHttpLoggingLevel(isDebug = true),
        )
    }

    @Test
    fun `release HTTP logging remains disabled`() {
        assertEquals(
            HttpLoggingInterceptor.Level.NONE,
            NetworkModule.secretSafeHttpLoggingLevel(isDebug = false),
        )
    }
}
