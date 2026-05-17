package com.pledgerio.app.ui.accounts

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pledgerio.app.R
import com.pledgerio.app.domain.model.AccountTypeGroup
import com.pledgerio.app.domain.model.AccountTypeOption
import com.pledgerio.app.ui.theme.EmeraldGreen

@Composable
fun AccountAddFabMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    accountTypeOptions: List<AccountTypeOption>,
    onAddAccount: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fabRotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        animationSpec = tween(200),
        label = "fab_rotation",
    )

    val ownedEntries = AccountTypePicker.ownedPickerEntries(
        accountTypeOptions.filter { !it.isCounterparty },
    )
    val counterpartyOptions = accountTypeOptions.filter { it.isCounterparty }

    Box(modifier = modifier.fillMaxSize()) {
        if (expanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = MutableInteractionSource(),
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
                    modifier = Modifier.width(300.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 8.dp),
                    ) {
                        if (ownedEntries.isNotEmpty()) {
                            AddTypeSectionHeader(stringResource(R.string.account_section_your_accounts))
                            ownedEntries.forEach { entry ->
                                AddPickerEntryRow(entry = entry, onAddAccount = {
                                    onExpandedChange(false)
                                    onAddAccount(entry.soloTypeCode)
                                })
                            }
                        }
                        if (counterpartyOptions.isNotEmpty()) {
                            AddTypeSectionHeader(AccountTypeGroup.COUNTERPARTY.localizedTitle())
                            counterpartyOptions.forEach { option ->
                                AddTypeRow(option = option, onAddAccount = {
                                    onExpandedChange(false)
                                    onAddAccount(option.code)
                                })
                            }
                        }
                    }
                }
            }

            FloatingActionButton(
                onClick = { onExpandedChange(!expanded) },
                containerColor = EmeraldGreen,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(
                        if (expanded) R.string.content_description_close else R.string.content_description_add_account,
                    ),
                    modifier = Modifier.rotate(fabRotation),
                )
            }
        }
    }
}

@Composable
private fun AddTypeSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun AddPickerEntryRow(
    entry: AccountTypePickerEntry,
    onAddAccount: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onAddAccount)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(EmeraldGreen.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = accountTypeIcon(entry.iconTypeCode),
                contentDescription = null,
                tint = EmeraldGreen,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.label.resolve(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = if (entry.jointTypeCode != null) {
                    stringResource(
                        R.string.account_picker_joint_hint_fab,
                        entry.description.resolve(),
                    )
                } else {
                    entry.description.resolve()
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AddTypeRow(
    option: AccountTypeOption,
    onAddAccount: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onAddAccount)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(EmeraldGreen.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = accountTypeIcon(option.code),
                contentDescription = null,
                tint = EmeraldGreen,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            val meta = com.pledgerio.app.domain.model.AccountTypeCatalog.metadataFor(option.code)
            Text(
                text = meta.localizedDisplayName(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            val description = option.description.takeIf { it.isNotBlank() } ?: meta.localizedDescription()
            if (description.isNotBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
