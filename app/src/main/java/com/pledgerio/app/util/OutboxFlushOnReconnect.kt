package com.pledgerio.app.util

import com.pledgerio.app.di.ApplicationScope
import com.pledgerio.app.di.IoDispatcher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Schedules a one-shot outbox flush when connectivity returns while a session is active.
 */
@Singleton
class OutboxFlushOnReconnect @Inject constructor(
    private val networkMonitor: NetworkMonitor,
    private val sessionManager: SessionManager,
    private val outboxFlushScheduler: OutboxFlushScheduler,
    private val appLog: AppLog,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    fun start() {
        applicationScope.launch(ioDispatcher) {
            networkMonitor.isOnline
                .distinctUntilChanged()
                .drop(1) // skip the initial emission from monitor registration
                .filter { online -> online }
                .collect {
                    val generation = sessionManager.getSyncGeneration() ?: return@collect
                    runCatching {
                        withContext(ioDispatcher) {
                            outboxFlushScheduler.schedule(generation)
                        }
                    }.onFailure { error ->
                        appLog.w("OutboxFlush", "Could not schedule outbox flush after reconnect", error)
                    }
                }
        }
    }
}
