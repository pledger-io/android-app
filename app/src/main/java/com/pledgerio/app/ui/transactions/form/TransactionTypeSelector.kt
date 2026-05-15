package com.pledgerio.app.ui.transactions.form

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pledgerio.app.domain.model.TransactionType
import com.pledgerio.app.ui.theme.EmeraldGreen
import com.pledgerio.app.ui.theme.ExpenseRed
import com.pledgerio.app.ui.theme.IncomeGreen

@Composable
fun TransactionTypeSelector(
    selected: TransactionType,
    subtitle: String,
    onSelected: (TransactionType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TypeSegment(
                label = "Income",
                icon = Icons.Default.TrendingUp,
                selected = selected == TransactionType.DEBIT,
                accent = IncomeGreen,
                onClick = { onSelected(TransactionType.DEBIT) },
                modifier = Modifier.weight(1f),
            )
            TypeSegment(
                label = "Expense",
                icon = Icons.Default.TrendingDown,
                selected = selected == TransactionType.CREDIT,
                accent = ExpenseRed,
                onClick = { onSelected(TransactionType.CREDIT) },
                modifier = Modifier.weight(1f),
            )
            TypeSegment(
                label = "Transfer",
                icon = Icons.Default.SwapHoriz,
                selected = selected == TransactionType.TRANSFER,
                accent = EmeraldGreen,
                onClick = { onSelected(TransactionType.TRANSFER) },
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun TypeSegment(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(
                if (selected) accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
            )
            .then(
                if (selected) {
                    Modifier.border(1.5.dp, accent, shape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .semantics { role = Role.Button }
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}
