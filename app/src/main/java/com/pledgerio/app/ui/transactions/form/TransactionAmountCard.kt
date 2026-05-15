package com.pledgerio.app.ui.transactions.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pledgerio.app.ui.components.PledgerCard
import com.pledgerio.app.ui.theme.EmeraldGreen
import com.pledgerio.app.util.formatDisplay
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionAmountCard(
    amount: String,
    onAmountChange: (String) -> Unit,
    amountError: String?,
    currency: String,
    currencies: List<String>,
    onCurrencyChange: (String) -> Unit,
    date: LocalDate,
    isToday: Boolean,
    isYesterday: Boolean,
    onTodayClick: () -> Unit,
    onYesterdayClick: () -> Unit,
    onPickDateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PledgerCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Amount",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CompactCurrencySelector(
                selected = currency,
                options = currencies,
                onSelected = onCurrencyChange,
            )
        }

        OutlinedTextField(
            value = amount,
            onValueChange = onAmountChange,
            placeholder = { Text("0.00") },
            singleLine = true,
            isError = amountError != null,
            supportingText = amountError?.let { { Text(it) } },
            textStyle = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                lineHeight = 24.sp,
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp)
                .heightIn(min = 28.dp),
        )

        Text(
            text = "Date",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = isToday,
                onClick = onTodayClick,
                label = { Text("Today") },
            )
            FilterChip(
                selected = isYesterday,
                onClick = onYesterdayClick,
                label = { Text("Yesterday") },
            )
            FilterChip(
                selected = !isToday && !isYesterday,
                onClick = onPickDateClick,
                label = { Text(date.formatDisplay()) },
            )
        }
    }
}

@Composable
private fun CompactCurrencySelector(
    selected: String,
    options: List<String>,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        FilterChip(
            selected = true,
            onClick = { expanded = true },
            label = { Text(selected) },
            trailingIcon = {
                Text(
                    text = "▾",
                    style = MaterialTheme.typography.labelSmall,
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = EmeraldGreen.copy(alpha = 0.18f),
                selectedLabelColor = EmeraldGreen,
            ),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { code ->
                DropdownMenuItem(
                    text = { Text(code) },
                    onClick = {
                        onSelected(code)
                        expanded = false
                    },
                )
            }
        }
    }
}
