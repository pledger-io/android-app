package com.pledgerio.app.domain.model

import java.time.LocalDate

enum class InsightType {
    UNUSUAL_AMOUNT,
    UNUSUAL_FREQUENCY,
    UNUSUAL_MERCHANT,
    UNUSUAL_TIMING,
    POTENTIAL_DUPLICATE,
    BUDGET_EXCEEDED,
    SPENDING_SPIKE,
    UNUSUAL_LOCATION,
    UNKNOWN,
    ;

    companion object {
        fun fromString(value: String?): InsightType =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}

enum class InsightSeverity {
    INFO,
    WARNING,
    ALERT,
    ;

    companion object {
        fun fromString(value: String?): InsightSeverity =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: INFO
    }
}

enum class PatternType {
    RECURRING_MONTHLY,
    RECURRING_WEEKLY,
    SEASONAL,
    INCREASING_TREND,
    DECREASING_TREND,
    UNKNOWN,
    ;

    companion object {
        fun fromString(value: String?): PatternType =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}

/**
 * A single anomaly the backend detected in the user's spending.
 *
 * [categoryId] is resolved locally from [category]; it is null when the category is
 * unknown to the client, in which case the insight cannot be drilled down by category.
 */
data class SpendingInsight(
    val type: InsightType,
    val severity: InsightSeverity,
    val category: String,
    val message: String,
    val score: Double,
    val detectedDate: LocalDate?,
    val transactionId: Long? = null,
    val categoryId: Long? = null,
)

/** A recurring or trending spending behaviour the backend detected for a category. */
data class SpendingPattern(
    val type: PatternType,
    val category: String,
    val confidence: Double,
    val detectedDate: LocalDate?,
    val categoryId: Long? = null,
)

data class SpendingInsights(
    val insights: List<SpendingInsight> = emptyList(),
    val patterns: List<SpendingPattern> = emptyList(),
) {
    val isEmpty: Boolean get() = insights.isEmpty() && patterns.isEmpty()

    /** Insights worth surfacing outside the reports screen, most severe first. */
    fun highlights(limit: Int): List<SpendingInsight> = insights
        .sortedWith(compareByDescending<SpendingInsight> { it.severity.ordinal }.thenByDescending { it.score })
        .take(limit)
}
