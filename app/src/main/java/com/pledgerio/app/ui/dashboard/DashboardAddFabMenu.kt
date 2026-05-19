package com.pledgerio.app.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pledgerio.app.R
import com.pledgerio.app.ui.theme.EmeraldGreen

@Composable
fun DashboardAddFabMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onAddTransaction: () -> Unit,
    onScanInvoice: () -> Unit,
    onAddAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fabRotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "fab_rotation",
    )

    Box(modifier = modifier.fillMaxSize()) {
        if (expanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onExpandedChange(false) },
                    ),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(180)) + slideInVertically(
                    initialOffsetY = { it / 2 },
                    animationSpec = tween(220),
                ),
                exit = fadeOut(tween(120)) + slideOutVertically(
                    targetOffsetY = { it / 2 },
                    animationSpec = tween(160),
                ),
            ) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 8.dp),
                    ) {
                        AddMenuActionRow(
                            icon = Icons.Default.Receipt,
                            iconTint = EmeraldGreen,
                            title = stringResource(R.string.fab_new_transaction),
                            subtitle = stringResource(R.string.fab_new_transaction_subtitle),
                            onClick = {
                                onExpandedChange(false)
                                onAddTransaction()
                            },
                        )
                        AddMenuActionRow(
                            icon = Icons.Default.DocumentScanner,
                            iconTint = MaterialTheme.colorScheme.primary,
                            title = stringResource(R.string.fab_scan_invoice),
                            subtitle = stringResource(R.string.fab_scan_invoice_subtitle),
                            onClick = {
                                onExpandedChange(false)
                                onScanInvoice()
                            },
                        )
                        AddMenuActionRow(
                            icon = Icons.Default.AccountBalance,
                            iconTint = MaterialTheme.colorScheme.tertiary,
                            title = stringResource(R.string.fab_new_account),
                            subtitle = stringResource(R.string.fab_new_account_subtitle),
                            onClick = {
                                onExpandedChange(false)
                                onAddAccount()
                            },
                        )
                    }
                }
            }

            FloatingActionButton(
                onClick = { onExpandedChange(!expanded) },
                containerColor = EmeraldGreen,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = if (expanded) "Close menu" else "Add",
                    modifier = Modifier.rotate(fabRotation),
                )
            }
        }
    }
}

@Composable
private fun AddMenuActionRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
