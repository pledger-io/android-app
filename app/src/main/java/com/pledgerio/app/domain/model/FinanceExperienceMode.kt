package com.pledgerio.app.domain.model

enum class FinanceExperienceMode(
    val storageValue: String,
    val displayName: String,
    val description: String,
) {
    GUIDED(
        storageValue = "guided",
        displayName = "Guided",
        description = "Simpler defaults for new users",
    ),
    POWER(
        storageValue = "power",
        displayName = "Power",
        description = "Advanced options stay visible for faster entry",
    ),
    ;

    companion object {
        fun fromStorage(value: String?): FinanceExperienceMode =
            entries.find { it.storageValue == value } ?: GUIDED
    }
}
