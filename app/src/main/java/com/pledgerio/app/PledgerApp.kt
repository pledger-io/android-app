package com.pledgerio.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.Coil
import com.pledgerio.app.util.AppLog
import com.pledgerio.app.util.CurrencyProvider
import com.pledgerio.app.util.SyncWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

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

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        appLog.install()
        CurrencyProvider.setInstance(currencyProvider)
        Coil.setImageLoader(imageLoader)
        SyncWorker.schedule(this)
    }
}
