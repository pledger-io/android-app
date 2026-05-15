package com.pledgerio.app.domain.model

enum class ThemeMode(val storageValue: String, val displayName: String) {
    SYSTEM("system", "System default"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark"),
    ;

    companion object {
        fun fromStorage(value: String?): ThemeMode =
            entries.find { it.storageValue == value } ?: SYSTEM
    }
}
