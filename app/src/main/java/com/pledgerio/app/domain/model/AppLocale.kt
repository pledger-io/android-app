package com.pledgerio.app.domain.model

/**
 * User-selected app language. [SYSTEM] follows the device locale when a matching
 * translation exists, otherwise falls back to English (default `values/`).
 */
enum class AppLocale(
    val storageValue: String,
    /** BCP 47 language tag for [androidx.appcompat.app.AppCompatDelegate.setApplicationLocales]. */
    val languageTag: String?,
) {
    SYSTEM("system", null),
    ENGLISH("en", "en"),
    DUTCH("nl", "nl"),
    GERMAN("de", "de"),
    ;

    companion object {
        fun fromStorage(value: String?): AppLocale =
            entries.find { it.storageValue == value } ?: SYSTEM

        /** Locales exposed in Settings (excluding system). */
        val selectable: List<AppLocale> = listOf(ENGLISH, DUTCH, GERMAN)
    }
}
