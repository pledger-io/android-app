package com.pledgerio.app.ui.transactions.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.South
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pledgerio.app.domain.model.Account
import com.pledgerio.app.domain.model.Transaction
import com.pledgerio.app.domain.model.TransactionSplit
import com.pledgerio.app.domain.model.TransactionType
import com.pledgerio.app.ui.components.AccountIcon
import com.pledgerio.app.ui.components.PledgerCard
import com.pledgerio.app.ui.theme.ExpenseRed
import com.pledgerio.app.ui.theme.IncomeGreen
import com.pledgerio.app.ui.theme.PledgerGreen
import com.pledgerio.app.ui.theme.PledgerThemeExt
import com.pledgerio.app.ui.transactions.form.TransactionFormLabels
import com.pledgerio.app.util.formatCurrency
import com.pledgerio.app.util.formatDisplay
import java.util.Currency

data class TransactionTypeStyle(
    val label: String,
    val icon: ImageVector,
    val color: Color,
)

fun transactionTypeStyle(type: TransactionType): TransactionTypeStyle = when (type) {
    TransactionType.DEBIT -> TransactionTypeStyle("Income", Icons.Default.TrendingUp, IncomeGreen)
    TransactionType.CREDIT -> TransactionTypeStyle("Expense", Icons.Default.TrendingDown, ExpenseRed)
    TransactionType.TRANSFER -> TransactionTypeStyle("Transfer", Icons.Default.SwapHoriz, PledgerGreen)
}

fun transactionAmountPrefix(type: TransactionType): String = when (type) {
    TransactionType.DEBIT -> "+"
    TransactionType.CREDIT, TransactionType.TRANSFER -> "-"
}

@Composable
fun TransactionDetailHeroCard(
    transaction: Transaction,
    modifier: Modifier = Modifier,
) {
    val style = transactionTypeStyle(transaction.type)
    val currency = runCatching { Currency.getInstance(transaction.currency) }.getOrNull()
    val currencyLabel = currency?.let { "${it.symbol} · ${it.currencyCode}" } ?: transaction.currency

    PledgerCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(style.color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = style.icon,
                    contentDescription = null,
                    tint = style.color,
                    modifier = Modifier.size(28.dp),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = style.color.copy(alpha = 0.12f),
            ) {
                Text(
                    text = style.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = style.color,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "${transactionAmountPrefix(transaction.type)}${transaction.amount.formatCurrency(transaction.currency)}",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = style.color,
                textAlign = TextAlign.Center,
            )

            Text(
                text = currencyLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = transaction.description.ifBlank { "Transaction" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = transaction.date.formatDisplay(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun TransactionDetailFlowCard(
    transaction: Transaction,
    sourceAccount: Account?,
    destinationAccount: Account?,
    modifier: Modifier = Modifier,
) {
    PledgerCard(modifier = modifier) {
        Text(
            text = "Money flow",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(12.dp))

        FlowAccountSlot(
            label = TransactionFormLabels.sourceLabel(transaction.type),
            accountName = transaction.sourceAccountName,
            account = sourceAccount,
        )

        Icon(
            imageVector = Icons.Default.South,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 4.dp)
                .size(20.dp),
        )

        FlowAccountSlot(
            label = TransactionFormLabels.targetLabel(transaction.type),
            accountName = transaction.destinationAccountName,
            account = destinationAccount,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = TransactionFormLabels.flowHelperText(transaction.type),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FlowAccountSlot(
    label: String,
    accountName: String,
    account: Account?,
) {
    val displayName = accountName.ifBlank { account?.name ?: "—" }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            .padding(12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccountIcon(
                iconFileCode = account?.iconFileCode,
                size = 40.dp,
                contentDescription = displayName,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun TransactionDetailClassificationCard(
    budgetName: String?,
    categoryName: String?,
    contractName: String?,
    modifier: Modifier = Modifier,
) {
    PledgerCard(modifier = modifier) {
        Text(
            text = "Classification",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        budgetName?.let { DetailInfoRow("Category", it) }
        categoryName?.let { DetailInfoRow("Sub category", it) }
        contractName?.let { DetailInfoRow("Contract", it) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransactionDetailTagsCard(
    tags: List<String>,
    modifier: Modifier = Modifier,
) {
    PledgerCard(modifier = modifier) {
        Text(
            text = "Tags",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tags.forEach { tag ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = PledgerThemeExt.brandAccent.copy(alpha = 0.12f),
                ) {
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.labelLarge,
                        color = PledgerThemeExt.brandAccent,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun TransactionDetailSplitCard(
    transaction: Transaction,
    modifier: Modifier = Modifier,
) {
    PledgerCard(modifier = modifier) {
        Text(
            text = "Split",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        transaction.split.forEachIndexed { index, part ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                )
            }
            SplitLineRow(part, transaction.currency)
        }
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 12.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Total",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = transaction.splitTotal.formatCurrency(transaction.currency),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun SplitLineRow(part: TransactionSplit, currency: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = part.description.ifBlank { "Part" },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = part.amount.formatCurrency(currency),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun DetailInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}
