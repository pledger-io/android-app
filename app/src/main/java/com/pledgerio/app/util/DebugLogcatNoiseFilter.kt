package com.pledgerio.app.util

import com.pledgerio.app.BuildConfig

/**
 * Lowers noisy platform log tags during local debugging.
 *
 * On Android 15+, [android.view.View.setRequestedFrameRate] logs at INFO on every draw when
 * Compose passes `NaN` (see AndroidComposeView / Api35Impl). That floods Logcat and drowns out
 * app logs. Raising the tag floor to WARN hides those INFO lines unless you explicitly lower
 * the Logcat level filter for the `View` tag.
 */
object DebugLogcatNoiseFilter {

    private val NOISY_TAGS = listOf(
        "View",
    )

    fun installIfDebug() {
        if (!BuildConfig.DEBUG) return
        NOISY_TAGS.forEach { tag ->
            System.setProperty("log.tag.$tag", "WARN")
        }
    }
}
