package com.pledgerio.app.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pledgerio.app.R
import com.pledgerio.app.domain.model.FinanceExperienceMode
import com.pledgerio.app.ui.util.formBannerHint
import com.pledgerio.app.ui.util.formBannerTitle
import com.pledgerio.app.ui.components.LoadingScreen
import com.pledgerio.app.ui.components.PledgerCard
import com.pledgerio.app.ui.components.PledgerTopBar
import com.pledgerio.app.ui.transactions.form.OwnedAccountPickerSheet
import com.pledgerio.app.ui.transactions.form.TransactionAmountCard
import com.pledgerio.app.ui.transactions.form.TransactionFlowCard
import com.pledgerio.app.ui.transactions.form.TransactionFormFooter
import com.pledgerio.app.ui.transactions.form.SaveTemplateDialog
import com.pledgerio.app.ui.transactions.form.TransactionFormMoreOptions
import com.pledgerio.app.ui.transactions.form.TransactionSplitEditor
import com.pledgerio.app.ui.transactions.form.TransactionTemplatesSection
import com.pledgerio.app.ui.transactions.form.TransactionFormLabels
import com.pledgerio.app.ui.transactions.form.localizedFieldErrors
import com.pledgerio.app.ui.transactions.form.localizedSplitValidationError
import com.pledgerio.app.ui.transactions.form.localizedValidationSummary
import com.pledgerio.app.ui.transactions.form.screenTitleLocalized
import com.pledgerio.app.ui.transactions.form.submitLabelLocalized
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
    val fieldErrors = uiState.localizedFieldErrors()
    val typeSubtitle = TransactionFormLabels.typeSubtitle(uiState.type)
    val sourceLabel = TransactionFormLabels.sourceLabel(uiState.type)
    val targetLabel = TransactionFormLabels.targetLabel(uiState.type)
    val flowHelperText = TransactionFormLabels.flowHelperText(uiState.type)
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now() }
    val yesterday = remember { today.minusDays(1) }
    val defaultTemplateName = stringResource(R.string.transaction_template_default_name)

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
                    Text(stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDatePicker) {
                    Text(stringResource(R.string.cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    when (uiState.ownedAccountPickerSide) {
        OwnedAccountPickerSide.SOURCE -> {
            OwnedAccountPickerSheet(
                title = sourceLabel,
                accounts = uiState.ownedAccounts,
                selectedId = uiState.sourceAccountId,
                onDismiss = viewModel::dismissOwnedAccountPicker,
                onAccountSelected = viewModel::onSourceDropdownSelected,
            )
        }
        OwnedAccountPickerSide.TARGET -> {
            OwnedAccountPickerSheet(
                title = targetLabel,
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
                title = uiState.screenTitleLocalized(),
                subtitle = typeSubtitle,
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back),
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (!uiState.isLoading) {
                TransactionFormFooter(
                    canSubmit = uiState.canSubmit,
                    isSaving = uiState.isSaving,
                    validationSummary = uiState.localizedValidationSummary(),
                    serverError = uiState.error,
                    submitLabel = uiState.submitLabelLocalized(),
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
                            subtitle = typeSubtitle,
                            onSelected = viewModel::onTypeChanged,
                        )

                        if (uiState.showTemplatesSection) {
                            Spacer(modifier = Modifier.height(12.dp))
                            TransactionTemplatesSection(
                                templates = uiState.templates,
                                onApplyTemplate = viewModel::applyTemplate,
                                onSaveAsTemplate = {
                                    viewModel.showSaveTemplateDialog(defaultTemplateName)
                                },
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        TransactionAmountCard(
                            amount = uiState.amount,
                            onAmountChange = viewModel::onAmountChanged,
                            amountError = fieldErrors.amount,
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
                            sourceLabel = sourceLabel,
                            targetLabel = targetLabel,
                            helperText = flowHelperText,
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
                            sourceError = fieldErrors.source,
                            targetError = fieldErrors.target,
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
                            label = { Text(stringResource(R.string.transaction_description_label)) },
                            placeholder = { Text(stringResource(R.string.transaction_description_hint)) },
                            singleLine = false,
                            minLines = 1,
                            maxLines = 3,
                            isError = fieldErrors.description != null,
                            supportingText = fieldErrors.description?.let { { Text(it) } },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        if (uiState.isEditing) {
                            TransactionSplitEditor(
                                expanded = uiState.splitSectionExpanded,
                                onToggle = viewModel::toggleSplitSection,
                                transactionAmount = uiState.amount.toDoubleOrNull() ?: 0.0,
                                currency = uiState.currency,
                                lines = uiState.splitLines,
                                splitTotal = uiState.splitTotal,
                                remaining = uiState.splitRemaining,
                                validationError = if (uiState.validationAttempted) {
                                    uiState.localizedSplitValidationError()
                                } else {
                                    null
                                },
                                onLineDescriptionChange = viewModel::onSplitLineDescriptionChanged,
                                onLineAmountChange = viewModel::onSplitLineAmountChanged,
                                onRemoveLine = viewModel::removeSplitLine,
                                onAddLine = viewModel::addSplitLine,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }

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
                            tagSuggestions = uiState.tagSuggestions,
                            isSearchingTags = uiState.isSearchingTags,
                            isAddingTag = uiState.isAddingTag,
                            tagError = uiState.tagError,
                            onTagInputChange = viewModel::onTagInputChanged,
                            onAddTag = viewModel::addTag,
                            onRemoveTag = viewModel::removeTag,
                            showAutoClassifyAction = !uiState.isEditing,
                            canAutoClassify = uiState.canAutoClassify,
                            isAutoClassifying = uiState.isAutoClassifying,
                            autoClassifyStatus = uiState.autoClassifyStatus,
                            onAutoClassify = viewModel::autoClassify,
                            onSelectTagSuggestion = viewModel::selectTagFromSuggestion,
                            onTagsFieldFocus = viewModel::ensureTagCatalogLoaded,
                        )

                        if (
                            uiState.financeExperienceMode == FinanceExperienceMode.GUIDED &&
                            !uiState.moreOptionsExpanded
                        ) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.transaction_form_guided_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ExperienceModeBanner(
    title: String,
    hint: String,
) {
    val accent = MaterialTheme.colorScheme.primary
    val badgeBackground = accent.copy(alpha = 0.15f)
    val icon = Icons.Default.School

    PledgerCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = accent,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .background(
                        color = badgeBackground,
                        shape = RoundedCornerShape(999.dp),
                    )
                    .padding(horizontal = 10.dp, vertical = 2.dp),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
