package com.pledgerio.app.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pledgerio.app.domain.model.AccountTypeCatalog
import com.pledgerio.app.domain.model.AccountTypeOption
import com.pledgerio.app.ui.components.LoadingScreen
import com.pledgerio.app.ui.components.PledgerCard
import com.pledgerio.app.ui.components.PledgerTopBar
import com.pledgerio.app.ui.theme.EmeraldGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountFormScreen(
    onNavigateBack: () -> Unit,
    viewModel: AccountFormViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            PledgerTopBar(
                title = if (uiState.isEditing) "Edit Account" else "New Account",
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { paddingValues ->
        when {
            uiState.isLoading -> LoadingScreen(modifier = Modifier.padding(paddingValues))
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (uiState.isSaving) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    uiState.error?.let { error ->
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    OutlinedTextField(
                        value = uiState.name,
                        onValueChange = viewModel::onNameChanged,
                        label = { Text("Account name *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = uiState.error != null && uiState.name.isBlank(),
                    )

                    OutlinedTextField(
                        value = uiState.description,
                        onValueChange = viewModel::onDescriptionChanged,
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                    )

                    AccountTypeDropdown(
                        selectedCode = uiState.typeCode,
                        ownedPickerEntries = uiState.ownedPickerEntries,
                        counterpartyTypes = uiState.counterpartyAccountTypes,
                        onSelected = viewModel::onTypeChanged,
                    )

                    uiState.typeVariantChoice?.let { variant ->
                        AccountOwnershipSelector(
                            variant = variant,
                            onJointChanged = viewModel::onOwnershipVariantChanged,
                        )
                    }

                    val typeMeta = AccountTypeCatalog.metadataFor(uiState.typeCode)
                    PledgerCard {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Icon(
                                imageVector = accountTypeIcon(uiState.typeCode),
                                contentDescription = null,
                                tint = EmeraldGreen,
                                modifier = Modifier.size(28.dp),
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = typeMeta.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                )
                                Text(
                                    text = typeMeta.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    CurrencyDropdown(
                        selected = uiState.currency,
                        currencies = uiState.availableCurrencies.map { it.code to "${it.name} (${it.symbol})" },
                        onSelected = viewModel::onCurrencyChanged,
                    )

                    if (typeMeta.showBankDetails) {
                        OutlinedTextField(
                            value = uiState.iban,
                            onValueChange = viewModel::onIbanChanged,
                            label = { Text("IBAN") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        OutlinedTextField(
                            value = uiState.bic,
                            onValueChange = viewModel::onBicChanged,
                            label = { Text("BIC") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    if (!uiState.isEditing && typeMeta.showOpeningBalance) {
                        OutlinedTextField(
                            value = uiState.openingBalance,
                            onValueChange = viewModel::onOpeningBalanceChanged,
                            label = { Text("Opening balance") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = viewModel::save,
                        enabled = uiState.isValid && !uiState.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (uiState.isEditing) "Save Changes" else "Create Account")
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun AccountOwnershipSelector(
    variant: AccountTypeVariantChoice,
    onJointChanged: (Boolean) -> Unit,
) {
    val (personalLabel, jointLabel) = when (variant.family) {
        AccountTypeFamily.CHECKING -> "Personal" to "Joint"
        AccountTypeFamily.SAVINGS -> "Personal" to "Joint"
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = when (variant.family) {
                AccountTypeFamily.CHECKING -> "Checking account"
                AccountTypeFamily.SAVINGS -> "Savings account"
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !variant.isJoint,
                onClick = { onJointChanged(false) },
                label = { Text(personalLabel) },
            )
            FilterChip(
                selected = variant.isJoint,
                onClick = { onJointChanged(true) },
                label = { Text(jointLabel) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountTypeDropdown(
    selectedCode: String,
    ownedPickerEntries: List<AccountTypePickerEntry>,
    counterpartyTypes: List<AccountTypeOption>,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = ownedPickerEntries.firstOrNull { entry ->
        entry.soloTypeCode.equals(selectedCode, ignoreCase = true) ||
            entry.jointTypeCode?.equals(selectedCode, ignoreCase = true) == true
    }?.label
        ?: counterpartyTypes.firstOrNull { it.code == selectedCode }?.displayName
        ?: selectedCode.replace("_", " ").replaceFirstChar { it.uppercase() }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Account type *") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            if (ownedPickerEntries.isNotEmpty()) {
                DropdownMenuItem(
                    text = {
                        Text(
                            "Your accounts",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    onClick = {},
                    enabled = false,
                )
                ownedPickerEntries.forEach { entry ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(entry.label)
                                Text(
                                    text = if (entry.jointTypeCode != null) {
                                        "${entry.description} · personal or joint below"
                                    } else {
                                        entry.description
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        onClick = {
                            onSelected(entry.soloTypeCode)
                            expanded = false
                        },
                    )
                }
            }
            if (counterpartyTypes.isNotEmpty()) {
                DropdownMenuItem(
                    text = {
                        Text(
                            "Counterparties",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    onClick = {},
                    enabled = false,
                )
                counterpartyTypes.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(option.displayName)
                                if (option.description.isNotBlank()) {
                                    Text(
                                        text = option.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                        onClick = {
                            onSelected(option.code)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrencyDropdown(
    selected: String,
    currencies: List<Pair<String, String>>,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = currencies.firstOrNull { it.first == selected }?.second ?: selected

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Currency *") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            if (currencies.isEmpty()) {
                DropdownMenuItem(
                    text = { Text(selected) },
                    onClick = { expanded = false },
                )
            } else {
                currencies.forEach { (code, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onSelected(code)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
