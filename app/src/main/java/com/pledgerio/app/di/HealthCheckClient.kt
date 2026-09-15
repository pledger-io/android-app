package com.pledgerio.app.di

import javax.inject.Qualifier

/**
 * Client without the dynamic base URL and authentication interceptors, so a health check runs
 * against the candidate host instead of the currently configured server.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class HealthCheckClient
