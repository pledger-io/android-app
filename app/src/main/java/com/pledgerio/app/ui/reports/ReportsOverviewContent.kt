package com.pledgerio.app.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pledgerio.app.R
import com.pledgerio.app.domain.model.ReportsOverview
import com.pledgerio.app.ui.components.PledgerCard
import com.pledgerio.app.ui.theme.ExpenseRed
import com.pledgerio.app.ui.theme.IncomeGreen
import com.pledgerio.app.util.formatCurrency

@Composable
fun ReportsOverviewContent(
    overview: ReportsOverview,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        overview.incomeExpense?.let { summary ->
            IncomeExpenseCard(summary = summary)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val totalAssets = overview.accountBalances.sumOf { it.amount }
            QuickStatCard(
                icon = { Icon(Icons.Default.AccountBalance, contentDescription = null) },
                label = stringResource(R.string.reports_total_assets),
                value = totalAssets.formatCurrency(),
                modifier = Modifier.weight(1f),
            )
            val overBudget = overview.budgetItems.count { it.spent > it.budgeted && it.budgeted > 0 }
            val budgetLabel = if (overview.budgetItems.isEmpty()) {
                stringResource(R.string.reports_no_budgets)
            } else {
                stringResource(R.string.reports_budgets_on_track, overview.budgetItems.size - overBudget, overview.budgetItems.size)
            }
            QuickStatCard(
                icon = { Icon(Icons.Default.PieChart, contentDescription = null) },
                label = stringResource(R.string.report_type_budget),
                value = budgetLabel,
                valueColor = if (overBudget > 0) ExpenseRed else IncomeGreen,
                modifier = Modifier.weight(1f),
            )
        }

        if (overview.topCategories.isNotEmpty()) {
            Text(
                text = stringResource(R.string.reports_top_categories),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp),
            )
            PartitionList(
                partitions = overview.topCategories.map { it.toUi() },
                maxItems = 5,
            )
        }

        if (overview.netWorthInMonth.isNotEmpty()) {
            NetWorthSection(points = overview.netWorthInMonth)
        }

        PledgerCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                Text(
                    text = stringResource(R.string.reports_drill_down_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun QuickStatCard(
    icon: @Composable () -> Unit,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    PledgerCard(modifier = modifier) {
        icon()
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
        )
    }
}
