package com.pledgerio.app.util

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.pledgerio.app.domain.model.AppLocale
import java.util.Locale

object LocaleManager {

    fun apply(locale: AppLocale) {
        AppCompatDelegate.setApplicationLocales(toLocaleList(locale))
    }

    fun toLocaleList(locale: AppLocale): LocaleListCompat =
        locale.languageTag?.let { LocaleListCompat.forLanguageTags(it) }
            ?: LocaleListCompat.getEmptyLocaleList()

    /**
     * Applies [locale] to [base] for contexts that do not go through AppCompat (e.g. early startup).
     */
    fun wrapContext(base: Context, locale: AppLocale): Context {
        val tag = locale.languageTag ?: return base
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(tag))
        return base.createConfigurationContext(configuration)
    }
}
