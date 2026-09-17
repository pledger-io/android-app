package com.pledgerio.app.ui.reports

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pledgerio.app.R
import com.pledgerio.app.domain.model.InsightSeverity
import com.pledgerio.app.domain.model.InsightType
import com.pledgerio.app.domain.model.PatternType
import com.pledgerio.app.domain.model.SpendingInsight
import com.pledgerio.app.domain.model.SpendingInsights
import com.pledgerio.app.domain.model.SpendingPattern
import com.pledgerio.app.ui.components.PledgerCard
import com.pledgerio.app.ui.theme.ExpenseRed
import com.pledgerio.app.util.formatDisplay

@Composable
fun InsightsSection(
    insights: SpendingInsights,
    modifier: Modifier = Modifier,
    onInsightClick: (SpendingInsight) -> Unit = {},
    onPatternClick: (SpendingPattern) -> Unit = {},
) {
    if (insights.isEmpty) {
        PledgerCard(modifier = modifier) {
            Text(
                text = stringResource(R.string.insights_empty_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.insights_empty_message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (insights.insights.isNotEmpty()) {
            SectionHeader(stringResource(R.string.insights_detected_title))
            insights.insights.forEach { insight ->
                InsightRow(
                    insight = insight,
                    onClick = { onInsightClick(insight) },
                )
            }
        }
        if (insights.patterns.isNotEmpty()) {
            SectionHeader(stringResource(R.string.insights_patterns_title))
            insights.patterns.forEach { pattern ->
                PatternRow(pattern = pattern, onClick = { onPatternClick(pattern) })
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
fun InsightRow(
    insight: SpendingInsight,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val clickable = insight.transactionId != null || insight.categoryId != null
    val openLabel = when {
        insight.transactionId != null -> stringResource(R.string.insights_open_transaction)
        insight.categoryId != null -> stringResource(
            R.string.reports_open_category,
            insight.category,
        )
        else -> null
    }
    PledgerCard(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (clickable) {
                    Modifier.clickable(onClickLabel = openLabel, onClick = onClick)
                } else {
                    Modifier
                },
            ),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = insight.severity.icon(),
                contentDescription = null,
                tint = insight.severity.tint(),
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (insight.type != InsightType.UNKNOWN) {
                    Text(
                        text = insight.type.localizedLabel(),
                        style = MaterialTheme.typography.labelMedium,
                        color = insight.severity.tint(),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                Text(
                    text = insight.message.ifBlank { insight.type.localizedLabel() },
                    style = MaterialTheme.typography.bodyMedium,
                )
                val subtitle = listOfNotNull(
                    insight.category.takeIf { it.isNotBlank() },
                    insight.detectedDate?.formatDisplay(),
                ).joinToString(" · ")
                if (subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PatternRow(
    pattern: SpendingPattern,
    onClick: () -> Unit,
) {
    val confidencePercent = (pattern.confidence * 100).toInt().coerceIn(0, 100)
    val openLabel = stringResource(R.string.reports_open_category, pattern.category)
    PledgerCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (pattern.categoryId != null) {
                    Modifier.clickable(onClickLabel = openLabel, onClick = onClick)
                } else {
                    Modifier
                },
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = pattern.type.icon(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pattern.category.ifBlank { stringResource(R.string.insights_uncategorized) },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                if (pattern.type != PatternType.UNKNOWN) {
                    Text(
                        text = pattern.type.localizedLabel(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = stringResource(R.string.insights_confidence, confidencePercent),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InsightSeverity.icon(): ImageVector = when (this) {
    InsightSeverity.INFO -> Icons.Default.Info
    InsightSeverity.WARNING -> Icons.Default.Warning
    InsightSeverity.ALERT -> Icons.Default.Warning
}

@Composable
private fun InsightSeverity.tint(): Color = when (this) {
    InsightSeverity.INFO -> MaterialTheme.colorScheme.primary
    InsightSeverity.WARNING -> MaterialTheme.colorScheme.tertiary
    InsightSeverity.ALERT -> ExpenseRed
}

@Composable
private fun PatternType.icon(): ImageVector = when (this) {
    PatternType.RECURRING_MONTHLY, PatternType.RECURRING_WEEKLY -> Icons.Default.Repeat
    PatternType.SEASONAL -> Icons.Default.WbSunny
    PatternType.INCREASING_TREND -> Icons.AutoMirrored.Filled.TrendingUp
    PatternType.DECREASING_TREND -> Icons.AutoMirrored.Filled.TrendingDown
    PatternType.UNKNOWN -> Icons.Default.Info
}

@Composable
fun InsightType.localizedLabel(): String = stringResource(
    when (this) {
        InsightType.UNUSUAL_AMOUNT -> R.string.insight_type_unusual_amount
        InsightType.UNUSUAL_FREQUENCY -> R.string.insight_type_unusual_frequency
        InsightType.UNUSUAL_MERCHANT -> R.string.insight_type_unusual_merchant
        InsightType.UNUSUAL_TIMING -> R.string.insight_type_unusual_timing
        InsightType.POTENTIAL_DUPLICATE -> R.string.insight_type_potential_duplicate
        InsightType.BUDGET_EXCEEDED -> R.string.insight_type_budget_exceeded
        InsightType.SPENDING_SPIKE -> R.string.insight_type_spending_spike
        InsightType.UNUSUAL_LOCATION -> R.string.insight_type_unusual_location
        InsightType.UNKNOWN -> R.string.insight_type_unknown
    },
)

@Composable
private fun PatternType.localizedLabel(): String = stringResource(
    when (this) {
        PatternType.RECURRING_MONTHLY -> R.string.pattern_type_recurring_monthly
        PatternType.RECURRING_WEEKLY -> R.string.pattern_type_recurring_weekly
        PatternType.SEASONAL -> R.string.pattern_type_seasonal
        PatternType.INCREASING_TREND -> R.string.pattern_type_increasing_trend
        PatternType.DECREASING_TREND -> R.string.pattern_type_decreasing_trend
        PatternType.UNKNOWN -> R.string.pattern_type_unknown
    },
)
