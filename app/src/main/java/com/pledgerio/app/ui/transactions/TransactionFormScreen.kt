package com.pledgerio.app.ui.transactions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pledgerio.app.ui.components.LoadingScreen
import com.pledgerio.app.ui.components.PledgerTopBar
import com.pledgerio.app.ui.transactions.form.OwnedAccountPickerSheet
import com.pledgerio.app.ui.transactions.form.TransactionAmountCard
import com.pledgerio.app.ui.transactions.form.TransactionFlowCard
import com.pledgerio.app.ui.transactions.form.TransactionFormFooter
import com.pledgerio.app.ui.transactions.form.SaveTemplateDialog
import com.pledgerio.app.ui.transactions.form.TransactionFormMoreOptions
import com.pledgerio.app.ui.transactions.form.TransactionTemplatesSection
import com.pledgerio.app.ui.transactions.form.TransactionTypeSelector
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionFormScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAddAccount: (typeCode: String) -> Unit = {},
    viewModel: TransactionFormViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now() }
    val yesterday = remember { today.minusDays(1) }

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            onNavigateBack()
        }
    }

    if (uiState.showSaveTemplateDialog) {
        SaveTemplateDialog(
            name = uiState.saveTemplateName,
            onNameChange = viewModel::onSaveTemplateNameChanged,
            onDismiss = viewModel::dismissSaveTemplateDialog,
            onConfirm = viewModel::confirmSaveTemplate,
        )
    }

    if (uiState.showDatePicker) {
        val initialMillis = uiState.date
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
        )
        DatePickerDialog(
            onDismissRequest = viewModel::dismissDatePicker,
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val selected = Instant.ofEpochMilli(millis)
                                .atZone(zone)
                                .toLocalDate()
                            viewModel.onDateSelected(selected)
                        } ?: viewModel.dismissDatePicker()
                    },
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDatePicker) {
                    Text("Cancel")
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    when (uiState.ownedAccountPickerSide) {
        OwnedAccountPickerSide.SOURCE -> {
            OwnedAccountPickerSheet(
                title = uiState.sourceLabel,
                accounts = uiState.ownedAccounts,
                selectedId = uiState.sourceAccountId,
                onDismiss = viewModel::dismissOwnedAccountPicker,
                onAccountSelected = viewModel::onSourceDropdownSelected,
            )
        }
        OwnedAccountPickerSide.TARGET -> {
            OwnedAccountPickerSheet(
                title = uiState.targetLabel,
                accounts = uiState.ownedAccounts,
                selectedId = uiState.targetAccountId,
                onDismiss = viewModel::dismissOwnedAccountPicker,
                onAccountSelected = viewModel::onTargetDropdownSelected,
            )
        }
        null -> {}
    }

    Scaffold(
        topBar = {
            PledgerTopBar(
                title = uiState.screenTitle,
                subtitle = uiState.typeSubtitle,
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            if (!uiState.isLoading) {
                TransactionFormFooter(
                    canSubmit = uiState.canSubmit,
                    isSaving = uiState.isSaving,
                    validationSummary = uiState.validationSummary,
                    serverError = uiState.error,
                    submitLabel = uiState.submitLabel,
                    onSubmit = viewModel::submit,
                )
            }
        },
    ) { paddingValues ->
        when {
            uiState.isLoading -> LoadingScreen(modifier = Modifier.padding(paddingValues))
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                ) {
                    if (uiState.isSaving) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp)
                            .padding(top = 8.dp),
                    ) {
                        TransactionTypeSelector(
                            selected = uiState.type,
                            subtitle = uiState.typeSubtitle,
                            onSelected = viewModel::onTypeChanged,
                        )

                        if (!uiState.isEditing) {
                            Spacer(modifier = Modifier.height(12.dp))
                            TransactionTemplatesSection(
                                templates = uiState.templates,
                                onApplyTemplate = viewModel::applyTemplate,
                                onSaveAsTemplate = viewModel::showSaveTemplateDialog,
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        TransactionAmountCard(
                            amount = uiState.amount,
                            onAmountChange = viewModel::onAmountChanged,
                            amountError = uiState.fieldErrors.amount,
                            currency = uiState.currency,
                            currencies = uiState.currencies,
                            onCurrencyChange = viewModel::onCurrencyChanged,
                            date = uiState.date,
                            isToday = uiState.date == today,
                            isYesterday = uiState.date == yesterday,
                            onTodayClick = viewModel::setDateToday,
                            onYesterdayClick = viewModel::setDateYesterday,
                            onPickDateClick = viewModel::showDatePicker,
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        TransactionFlowCard(
                            sourceLabel = uiState.sourceLabel,
                            targetLabel = uiState.targetLabel,
                            helperText = uiState.flowHelperText,
                            sourceKind = uiState.sourceInputKind,
                            targetKind = uiState.targetInputKind,
                            sourceAccountId = uiState.sourceAccountId,
                            sourceSelected = uiState.sourceSelected,
                            sourceQuery = uiState.sourceQuery,
                            sourceSuggestions = uiState.sourceSuggestions,
                            isSearchingSource = uiState.isSearchingSource,
                            targetAccountId = uiState.targetAccountId,
                            targetSelected = uiState.targetSelected,
                            targetQuery = uiState.targetQuery,
                            targetSuggestions = uiState.targetSuggestions,
                            isSearchingTarget = uiState.isSearchingTarget,
                            ownedAccounts = uiState.ownedAccounts,
                            sourceError = uiState.fieldErrors.source,
                            targetError = uiState.fieldErrors.target,
                            onSourceQueryChange = viewModel::onSourceQueryChanged,
                            onSourceAutocompleteSelected = viewModel::selectSourceAutocomplete,
                            onSourceAutocompleteClear = viewModel::clearSourceAccount,
                            onSourceDropdownSelected = viewModel::onSourceDropdownSelected,
                            onSourceOpenOwnedAccountSearch = {
                                viewModel.openOwnedAccountPicker(OwnedAccountPickerSide.SOURCE)
                            },
                            onSourceAddNewParty = viewModel.partyTypeCodeForNewAccount(isSource = true)?.let { typeCode ->
                                { onNavigateToAddAccount(typeCode) }
                            },
                            onTargetQueryChange = viewModel::onTargetQueryChanged,
                            onTargetAutocompleteSelected = viewModel::selectTargetAutocomplete,
                            onTargetAutocompleteClear = viewModel::clearTargetAccount,
                            onTargetDropdownSelected = viewModel::onTargetDropdownSelected,
                            onTargetOpenOwnedAccountSearch = {
                                viewModel.openOwnedAccountPicker(OwnedAccountPickerSide.TARGET)
                            },
                            onTargetAddNewParty = viewModel.partyTypeCodeForNewAccount(isSource = false)?.let { typeCode ->
                                { onNavigateToAddAccount(typeCode) }
                            },
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = uiState.description,
                            onValueChange = viewModel::onDescriptionChanged,
                            label = { Text("What was this for?") },
                            placeholder = { Text("Groceries, salary, rent…") },
                            singleLine = false,
                            minLines = 1,
                            maxLines = 3,
                            isError = uiState.fieldErrors.description != null,
                            supportingText = uiState.fieldErrors.description?.let { { Text(it) } },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        TransactionFormMoreOptions(
                            expanded = uiState.moreOptionsExpanded,
                            onToggle = viewModel::toggleMoreOptions,
                            categoryQuery = uiState.categoryQuery,
                            categorySelected = uiState.categorySelected,
                            categorySuggestions = uiState.categorySuggestions,
                            isSearchingCategory = uiState.isSearchingCategory,
                            onCategoryQueryChange = viewModel::onCategoryQueryChanged,
                            onCategorySelected = viewModel::selectCategory,
                            onCategoryClear = viewModel::clearCategory,
                            expenseQuery = uiState.expenseQuery,
                            expenseSelected = uiState.expenseSelected,
                            expenseSuggestions = uiState.expenseSuggestions,
                            isSearchingExpense = uiState.isSearchingExpense,
                            onExpenseQueryChange = viewModel::onExpenseQueryChanged,
                            onExpenseSelected = viewModel::selectExpense,
                            onExpenseClear = viewModel::clearExpense,
                            contractQuery = uiState.contractQuery,
                            contractSelected = uiState.contractSelected,
                            contractSuggestions = uiState.contractSuggestions,
                            isSearchingContract = uiState.isSearchingContract,
                            onContractQueryChange = viewModel::onContractQueryChanged,
                            onContractSelected = viewModel::selectContract,
                            onContractClear = viewModel::clearContract,
                            tags = uiState.tags,
                            tagInput = uiState.tagInput,
                            onTagInputChange = viewModel::onTagInputChanged,
                            onAddTag = viewModel::addTag,
                            onRemoveTag = viewModel::removeTag,
                        )

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}
