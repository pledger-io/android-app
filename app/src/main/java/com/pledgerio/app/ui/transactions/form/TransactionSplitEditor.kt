package com.pledgerio.app.ui.transactions.form

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pledgerio.app.ui.components.PledgerCard
import com.pledgerio.app.ui.transactions.TransactionSplitLineUi
import com.pledgerio.app.util.formatCurrency

@Composable
fun TransactionSplitEditor(
    expanded: Boolean,
    onToggle: () -> Unit,
    transactionAmount: Double,
    currency: String,
    lines: List<TransactionSplitLineUi>,
    splitTotal: Double,
    remaining: Double,
    validationError: String?,
    onLineDescriptionChange: (String, String) -> Unit,
    onLineAmountChange: (String, String) -> Unit,
    onRemoveLine: (String) -> Unit,
    onAddLine: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Split transaction",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Divide the amount across multiple descriptions (existing transactions only).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
            )
        }

        AnimatedVisibility(visible = expanded) {
            PledgerCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (lines.isEmpty()) {
                        Text(
                            text = "Add lines that sum to ${transactionAmount.formatCurrency(currency)}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        lines.forEach { line ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                OutlinedTextField(
                                    value = line.description,
                                    onValueChange = { onLineDescriptionChange(line.id, it) },
                                    label = { Text("Part") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedTextField(
                                    value = line.amount,
                                    onValueChange = { onLineAmountChange(line.id, it) },
                                    label = { Text("Amount") },
                                    singleLine = true,
                                    modifier = Modifier.weight(0.55f),
                                )
                                IconButton(onClick = { onRemoveLine(line.id) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove line")
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "Split total",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = splitTotal.formatCurrency(currency),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "Remaining",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = remaining.formatCurrency(currency),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = if (validationError != null) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    }

                    if (validationError != null) {
                        Text(
                            text = validationError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    OutlinedButton(
                        onClick = onAddLine,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("Add split line", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}
