package com.pledgerio.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import com.pledgerio.app.util.BiometricLockManager
import com.pledgerio.app.util.LocaleManager
import com.pledgerio.app.util.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import coil.ImageLoader
import coil.Coil
import com.pledgerio.app.util.AppLog
import com.pledgerio.app.util.AuthenticatedSessionCoordinator
import com.pledgerio.app.util.CurrencyProvider
import com.pledgerio.app.util.DebugLogcatNoiseFilter
import com.pledgerio.app.util.OutboxFlushOnReconnect
import com.pledgerio.app.di.ApplicationScope
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class PledgerApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var currencyProvider: CurrencyProvider

    @Inject
    lateinit var imageLoader: ImageLoader

    @Inject
    lateinit var appLog: AppLog

    @Inject
    lateinit var biometricLockManager: BiometricLockManager

    @Inject
    lateinit var userPreferences: UserPreferences

    @Inject
    lateinit var authenticatedSessionCoordinator: AuthenticatedSessionCoordinator

    @Inject
    lateinit var outboxFlushOnReconnect: OutboxFlushOnReconnect

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        DebugLogcatNoiseFilter.installIfDebug()
        runBlocking(Dispatchers.IO) {
            LocaleManager.apply(userPreferences.appLocaleOnce())
        }
        appLog.install()
        Coil.setImageLoader(imageLoader)
        applicationScope.launch {
            authenticatedSessionCoordinator.reconcileAtStartup()
        }
        outboxFlushOnReconnect.start()
        ProcessLifecycleOwner.get().lifecycle.addObserver(biometricLockManager)
        biometricLockManager.onColdStart()
    }
}
