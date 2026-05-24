package com.pledgerio.app.ui.reports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pledgerio.app.R
import com.pledgerio.app.domain.model.DatedAmount
import com.pledgerio.app.domain.model.IncomeExpenseSummary
import com.pledgerio.app.ui.components.PledgerCard
import com.pledgerio.app.ui.theme.ExpenseRed
import com.pledgerio.app.ui.theme.IncomeGreen
import com.pledgerio.app.ui.theme.PledgerThemeExt
import com.pledgerio.app.util.formatCurrency
import kotlin.math.abs
import kotlin.math.max

@Composable
fun IncomeExpenseCard(
    summary: IncomeExpenseSummary,
    modifier: Modifier = Modifier,
) {
    val income = summary.income
    val expense = summary.expense
    val net = income - expense
    val maxSide = maxOf(income, expense, 0.01)
    val incomeFraction = (income / maxSide).toFloat().coerceIn(0f, 1f)
    val expenseFraction = (expense / maxSide).toFloat().coerceIn(0f, 1f)
    val savingsRate = if (income > 0.0) ((net / income) * 100).toInt() else null

    PledgerCard(modifier = modifier) {
        Text(
            text = stringResource(R.string.report_type_income_expense),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(
                if (net >= 0) R.string.reports_net_positive else R.string.reports_net_negative,
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = net.formatCurrency(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = if (net >= 0) IncomeGreen else ExpenseRed,
        )
        savingsRate?.let { rate ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.reports_savings_rate, rate),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(stringResource(R.string.report_income), style = MaterialTheme.typography.labelSmall)
                Text(
                    income.formatCurrency(),
                    style = MaterialTheme.typography.titleMedium,
                    color = IncomeGreen,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(stringResource(R.string.report_expenses), style = MaterialTheme.typography.labelSmall)
                Text(
                    expense.formatCurrency(),
                    style = MaterialTheme.typography.titleMedium,
                    color = ExpenseRed,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            LinearProgressIndicator(
                progress = { incomeFraction },
                modifier = Modifier
                    .weight(incomeFraction.coerceAtLeast(0.08f))
                    .height(8.dp),
                color = IncomeGreen,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            LinearProgressIndicator(
                progress = { expenseFraction },
                modifier = Modifier
                    .weight(expenseFraction.coerceAtLeast(0.08f))
                    .height(8.dp),
                color = ExpenseRed,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.reports_income_expense_bar_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun PartitionList(
    partitions: List<PartitionAmountUi>,
    modifier: Modifier = Modifier,
    emptyMessage: String = stringResource(R.string.reports_no_data),
    maxItems: Int? = null,
) {
    if (partitions.isEmpty()) {
        Text(emptyMessage, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
        return
    }
    val shown = if (maxItems != null) partitions.take(maxItems) else partitions
    val total = partitions.sumOf { it.amount }.coerceAtLeast(0.01)
    val maxAmount = partitions.maxOf { it.amount }.coerceAtLeast(0.01)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PledgerCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.reports_total),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = total.formatCurrency(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        shown.forEach { item ->
            PledgerCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.label, style = MaterialTheme.typography.bodyMedium)
                        val share = ((item.amount / total) * 100).toInt()
                        Text(
                            text = stringResource(R.string.reports_share_of_total, share),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        item.amount.formatCurrency(),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (item.amount / maxAmount).toFloat().coerceIn(0.05f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = PledgerThemeExt.brandAccent,
                )
            }
        }
        if (maxItems != null && partitions.size > maxItems) {
            Text(
                text = stringResource(R.string.reports_more_items, partitions.size - maxItems),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

/** UI wrapper so partition list can stay in the reports package without exposing domain in previews. */
data class PartitionAmountUi(val label: String, val amount: Double)

fun com.pledgerio.app.domain.model.PartitionAmount.toUi() =
    PartitionAmountUi(label = label, amount = amount)

@Composable
fun NetWorthSection(
    points: List<DatedAmount>,
    modifier: Modifier = Modifier,
) {
    if (points.isEmpty()) {
        Text(
            stringResource(R.string.reports_no_data),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }
    val latest = points.last()
  val first = points.first()
    val change = latest.amount - first.amount

    PledgerCard(modifier = modifier) {
        Text(
            text = stringResource(R.string.report_type_net_worth),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = latest.amount.formatCurrency(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(
                if (change >= 0) R.string.reports_net_worth_change_up else R.string.reports_net_worth_change_down,
                abs(change).formatCurrency(),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = if (change >= 0) IncomeGreen else ExpenseRed,
        )
        Spacer(modifier = Modifier.height(12.dp))
        NetWorthSparkline(
            amounts = points.map { it.amount },
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.reports_net_worth_chart_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun NetWorthSparkline(
    amounts: List<Double>,
    modifier: Modifier = Modifier,
) {
    if (amounts.size < 2) return
    val lineColor = PledgerThemeExt.brandAccent
    Canvas(modifier = modifier) {
        val min = amounts.min()
        val max = amounts.max()
        val range = (max - min).coerceAtLeast(0.01)
        val stepX = size.width / (amounts.size - 1).coerceAtLeast(1)
        val path = Path()
        amounts.forEachIndexed { index, value ->
            val x = index * stepX
            val y = size.height - ((value - min) / range * size.height).toFloat()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 4f, cap = StrokeCap.Round),
        )
        val lastIndex = amounts.lastIndex
        val lastX = lastIndex * stepX
        val lastY = size.height - ((amounts.last() - min) / range * size.height).toFloat()
        drawCircle(color = lineColor, radius = 6f, center = Offset(lastX, lastY))
    }
}

@Composable
fun BudgetPerformanceList(
    items: List<com.pledgerio.app.domain.model.BudgetPerformanceItem>,
    modifier: Modifier = Modifier,
    maxItems: Int? = null,
) {
    if (items.isEmpty()) {
        Text(
            stringResource(R.string.reports_no_data),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }
    val overCount = items.count { it.spent > it.budgeted && it.budgeted > 0 }
    val onTrack = items.size - overCount
    val shown = if (maxItems != null) items.take(maxItems) else items

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PledgerCard {
            Text(
                text = stringResource(R.string.reports_budget_summary, onTrack, items.size),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            if (overCount > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.reports_budget_over_count, overCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = ExpenseRed,
                )
            }
        }
        shown.forEach { item ->
            PledgerCard {
                Text(item.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "${item.spent.formatCurrency()} / ${item.budgeted.formatCurrency()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.spent > item.budgeted) ExpenseRed else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (item.budgeted > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (item.spent / item.budgeted).toFloat().coerceIn(0f, 1.5f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = if (item.spent > item.budgeted) ExpenseRed else IncomeGreen,
                    )
                }
            }
        }
    }
}
