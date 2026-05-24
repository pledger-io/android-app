package com.pledgerio.app.ui.transactions

import com.pledgerio.app.domain.model.FilterOption
import com.pledgerio.app.ui.transactions.form.AutoClassifyStatus
import com.pledgerio.app.ui.transactions.form.ClassifyPart

internal data class AutoClassifyApplyResult(
    val updatedState: TransactionFormUiState,
    val unresolvedCategoryQuery: String?,
    val unresolvedExpenseQuery: String?,
)

internal fun applyAutoClassifySuggestion(
    current: TransactionFormUiState,
    suggestedCategoryRaw: String?,
    suggestedExpenseRaw: String?,
    suggestedTagsRaw: List<String>,
    categoryOption: FilterOption?,
    expenseOption: FilterOption?,
): AutoClassifyApplyResult {
    val suggestedCategory = suggestedCategoryRaw?.trim().orEmpty()
    val suggestedExpense = suggestedExpenseRaw?.trim().orEmpty()
    val suggestedTags = suggestedTagsRaw
        .map { it.trim() }
        .filter { it.isNotBlank() }

    val status = resolveAutoClassifyStatus(
        suggestedCategory = suggestedCategory,
        suggestedExpense = suggestedExpense,
        suggestedTags = suggestedTags,
        categoryOption = categoryOption,
        expenseOption = expenseOption,
    )

    val mergedTags = if (suggestedTags.isNotEmpty()) {
        (current.tags + suggestedTags).distinctBy { it.lowercase() }
    } else {
        current.tags
    }
    val shouldExpand = suggestedCategory.isNotBlank() ||
        suggestedExpense.isNotBlank() ||
        suggestedTags.isNotEmpty()

    return AutoClassifyApplyResult(
        updatedState = current.copy(
            isAutoClassifying = false,
            autoClassifyStatus = status,
            categorySelected = when {
                categoryOption != null -> categoryOption
                suggestedCategory.isNotBlank() -> null
                else -> current.categorySelected
            },
            categoryQuery = when {
                categoryOption != null -> categoryOption.label
                suggestedCategory.isNotBlank() -> suggestedCategory
                else -> current.categoryQuery
            },
            categorySuggestions = emptyList(),
            expenseSelected = when {
                expenseOption != null -> expenseOption
                suggestedExpense.isNotBlank() -> null
                else -> current.expenseSelected
            },
            expenseQuery = when {
                expenseOption != null -> expenseOption.label
                suggestedExpense.isNotBlank() -> suggestedExpense
                else -> current.expenseQuery
            },
            expenseSuggestions = emptyList(),
            tags = mergedTags,
            tagInput = "",
            moreOptionsExpanded = if (shouldExpand) true else current.moreOptionsExpanded,
            moreOptionsManuallyToggled = shouldExpand || current.moreOptionsManuallyToggled,
        ),
        unresolvedCategoryQuery = suggestedCategory.takeIf {
            it.isNotBlank() && categoryOption == null
        },
        unresolvedExpenseQuery = suggestedExpense.takeIf {
            it.isNotBlank() && expenseOption == null
        },
    )
}

private fun resolveAutoClassifyStatus(
    suggestedCategory: String,
    suggestedExpense: String,
    suggestedTags: List<String>,
    categoryOption: FilterOption?,
    expenseOption: FilterOption?,
): AutoClassifyStatus {
    val appliedParts = buildList {
        if (categoryOption != null) add(ClassifyPart.CATEGORY)
        if (expenseOption != null) add(ClassifyPart.EXPENSE_GROUP)
        if (suggestedTags.isNotEmpty()) add(ClassifyPart.TAGS)
    }
    val unresolvedParts = buildList {
        if (suggestedCategory.isNotBlank() && categoryOption == null) {
            add(ClassifyPart.CATEGORY)
        }
        if (suggestedExpense.isNotBlank() && expenseOption == null) {
            add(ClassifyPart.EXPENSE_GROUP)
        }
    }
    return when {
        appliedParts.isEmpty() && unresolvedParts.isEmpty() -> AutoClassifyStatus.NoSuggestions
        appliedParts.isNotEmpty() && unresolvedParts.isEmpty() -> AutoClassifyStatus.Applied(appliedParts)
        appliedParts.isEmpty() -> AutoClassifyStatus.Unresolved(unresolvedParts)
        else -> AutoClassifyStatus.Partial(applied = appliedParts, unresolved = unresolvedParts)
    }
}
