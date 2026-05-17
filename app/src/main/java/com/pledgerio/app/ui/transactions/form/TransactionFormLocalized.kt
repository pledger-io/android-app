package com.pledgerio.app.ui.transactions.form

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pledgerio.app.R
import com.pledgerio.app.domain.model.TransactionType
import com.pledgerio.app.ui.transactions.TransactionFormFieldErrors
import com.pledgerio.app.ui.transactions.TransactionFormUiState

enum class ClassifyPart {
    CATEGORY,
    EXPENSE_GROUP,
    TAGS,
}

sealed interface AutoClassifyStatus {
    data object NeedInput : AutoClassifyStatus
    data object NoSuggestions : AutoClassifyStatus
    data class Applied(val parts: List<ClassifyPart>) : AutoClassifyStatus
    data class Unresolved(val parts: List<ClassifyPart>) : AutoClassifyStatus
    data class Partial(
        val applied: List<ClassifyPart>,
        val unresolved: List<ClassifyPart>,
    ) : AutoClassifyStatus

    data class Error(val message: String) : AutoClassifyStatus
}

enum class SplitValidationIssue {
    LINE_DESCRIPTION,
    LINE_AMOUNT,
    TOTAL_MISMATCH,
}

object TransactionFormLabels {

    @Composable
    fun typeSubtitle(type: TransactionType): String = stringResource(
        when (type) {
            TransactionType.DEBIT -> R.string.transaction_type_subtitle_income
            TransactionType.CREDIT -> R.string.transaction_type_subtitle_expense
            TransactionType.TRANSFER -> R.string.transaction_type_subtitle_transfer
        },
    )

    @Composable
    fun sourceLabel(type: TransactionType): String = stringResource(
        when (type) {
            TransactionType.DEBIT -> R.string.transaction_source_received_from
            TransactionType.CREDIT -> R.string.transaction_source_paid_from
            TransactionType.TRANSFER -> R.string.transaction_source_from
        },
    )

    @Composable
    fun targetLabel(type: TransactionType): String = stringResource(
        when (type) {
            TransactionType.DEBIT -> R.string.transaction_target_deposited_to
            TransactionType.CREDIT -> R.string.transaction_target_payee
            TransactionType.TRANSFER -> R.string.transaction_target_to
        },
    )

    @Composable
    fun flowHelperText(type: TransactionType): String = stringResource(
        when (type) {
            TransactionType.DEBIT -> R.string.transaction_flow_help_income
            TransactionType.CREDIT -> R.string.transaction_flow_help_expense
            TransactionType.TRANSFER -> R.string.transaction_flow_help_transfer
        },
    )
}

@Composable
fun ClassifyPart.localizedName(): String = stringResource(
    when (this) {
        ClassifyPart.CATEGORY -> R.string.transaction_classify_part_category
        ClassifyPart.EXPENSE_GROUP -> R.string.transaction_classify_part_expense_group
        ClassifyPart.TAGS -> R.string.transaction_classify_part_tags
    },
)

@Composable
private fun localizedClassifyPartNames(parts: List<ClassifyPart>): String {
    val names = mutableListOf<String>()
    for (part in parts) {
        names.add(
            when (part) {
                ClassifyPart.CATEGORY -> stringResource(R.string.transaction_classify_part_category)
                ClassifyPart.EXPENSE_GROUP -> stringResource(R.string.transaction_classify_part_expense_group)
                ClassifyPart.TAGS -> stringResource(R.string.transaction_classify_part_tags)
            },
        )
    }
    return names.joinToString()
}

@Composable
fun AutoClassifyStatus.localize(): String = when (this) {
    AutoClassifyStatus.NeedInput ->
        stringResource(R.string.transaction_auto_classify_need_input)
    AutoClassifyStatus.NoSuggestions ->
        stringResource(R.string.transaction_auto_classify_none)
    is AutoClassifyStatus.Applied ->
        stringResource(
            R.string.transaction_auto_classify_applied,
            localizedClassifyPartNames(parts),
        )
    is AutoClassifyStatus.Unresolved ->
        stringResource(
            R.string.transaction_auto_classify_unresolved,
            localizedClassifyPartNames(parts),
        )
    is AutoClassifyStatus.Partial ->
        stringResource(
            R.string.transaction_auto_classify_partial,
            localizedClassifyPartNames(applied),
            localizedClassifyPartNames(unresolved),
        )
    is AutoClassifyStatus.Error ->
        message.ifBlank { stringResource(R.string.transaction_auto_classify_failed) }
}

@Composable
fun SplitValidationIssue.localize(): String = stringResource(
    when (this) {
        SplitValidationIssue.LINE_DESCRIPTION ->
            R.string.transaction_split_error_line_description
        SplitValidationIssue.LINE_AMOUNT ->
            R.string.transaction_split_error_line_amount
        SplitValidationIssue.TOTAL_MISMATCH ->
            R.string.transaction_split_error_total_mismatch
    },
)

@Composable
fun TransactionFormUiState.localizedFieldErrors(): TransactionFormFieldErrors {
    if (!validationAttempted) return TransactionFormFieldErrors()
    val sourceLabel = TransactionFormLabels.sourceLabel(type)
    val targetLabel = TransactionFormLabels.targetLabel(type)
    return TransactionFormFieldErrors(
        amount = if (amount.toDoubleOrNull()?.let { it > 0 } != true) {
            stringResource(R.string.transaction_error_amount)
        } else {
            null
        },
        source = if (sourceAccountId == null) {
            stringResource(R.string.transaction_error_choose_field, sourceLabel)
        } else {
            null
        },
        target = if (targetAccountId == null) {
            stringResource(R.string.transaction_error_choose_field, targetLabel)
        } else {
            null
        },
        description = if (description.isBlank()) {
            stringResource(R.string.transaction_error_description)
        } else {
            null
        },
    )
}

@Composable
fun TransactionFormUiState.localizedSplitValidationError(): String? {
    val issue = splitValidationIssue() ?: return null
    return issue.localize()
}

fun TransactionFormUiState.splitValidationIssue(): SplitValidationIssue? {
    if (splitLines.isEmpty()) return null
    if (splitLines.any { it.description.isBlank() }) return SplitValidationIssue.LINE_DESCRIPTION
    if (splitLines.any { it.amount.toDoubleOrNull() == null }) return SplitValidationIssue.LINE_AMOUNT
    if (kotlin.math.abs(splitRemaining) > 0.01) return SplitValidationIssue.TOTAL_MISMATCH
    return null
}

@Composable
fun TransactionFormUiState.localizedValidationSummary(): String? {
    if (!validationAttempted || canSubmit) return null
    localizedFieldErrors().let { errors ->
        listOfNotNull(
            errors.amount,
            errors.source,
            errors.target,
            errors.description,
            localizedSplitValidationError(),
        ).firstOrNull()?.let { return it }
    }
    return null
}

@Composable
fun TransactionFormUiState.screenTitleLocalized(): String = stringResource(
    if (isEditing) R.string.transaction_form_edit_title else R.string.transaction_form_new_title,
)

@Composable
fun TransactionFormUiState.submitLabelLocalized(): String = stringResource(
    if (isEditing) R.string.transaction_form_save_changes else R.string.transaction_form_create,
)
