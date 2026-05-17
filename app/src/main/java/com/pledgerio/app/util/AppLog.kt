package com.pledgerio.app.util

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLog @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val collector = AppLogCollector(context)

    fun install() {
        collector.installUncaughtExceptionHandler()
        i("AppLog", "Application logging initialized")
    }

    fun d(tag: String, message: String) = collector.log(Log.DEBUG, tag, message)

    fun i(tag: String, message: String) = collector.log(Log.INFO, tag, message)

    fun w(tag: String, message: String, throwable: Throwable? = null) =
        collector.log(Log.WARN, tag, message, throwable)

    fun e(tag: String, message: String, throwable: Throwable? = null) =
        collector.log(Log.ERROR, tag, message, throwable)

    fun export(): String = collector.export()
}
