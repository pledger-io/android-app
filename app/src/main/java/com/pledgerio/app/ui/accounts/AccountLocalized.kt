package com.pledgerio.app.ui.accounts

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pledgerio.app.R
import com.pledgerio.app.domain.model.AccountTypeGroup
import com.pledgerio.app.domain.model.AccountTypeMetadata

sealed interface AccountPickerLabel {
    data object Checking : AccountPickerLabel
    data object Savings : AccountPickerLabel
    data class Custom(val value: String) : AccountPickerLabel
}

sealed interface AccountPickerDescription {
    data object Checking : AccountPickerDescription
    data object Savings : AccountPickerDescription
    data class Custom(val value: String) : AccountPickerDescription
}

@Composable
fun AccountPickerLabel.resolve(): String = when (this) {
    AccountPickerLabel.Checking -> stringResource(R.string.account_picker_checking_label)
    AccountPickerLabel.Savings -> stringResource(R.string.account_picker_savings_label)
    is AccountPickerLabel.Custom -> value
}

@Composable
fun AccountPickerDescription.resolve(): String = when (this) {
    AccountPickerDescription.Checking -> stringResource(R.string.account_picker_checking_description)
    AccountPickerDescription.Savings -> stringResource(R.string.account_picker_savings_description)
    is AccountPickerDescription.Custom -> value
}

@Composable
fun AccountTypeGroup.localizedTitle(): String = stringResource(titleRes())

@Composable
fun AccountTypeGroup.localizedDescription(): String = stringResource(descriptionRes())

@StringRes
private fun AccountTypeGroup.titleRes(): Int = when (this) {
    AccountTypeGroup.EVERYDAY -> R.string.account_group_everyday_title
    AccountTypeGroup.SAVINGS -> R.string.account_group_savings_title
    AccountTypeGroup.CREDIT -> R.string.account_group_credit_title
    AccountTypeGroup.LIABILITIES -> R.string.account_group_liabilities_title
    AccountTypeGroup.COUNTERPARTY -> R.string.account_group_counterparty_title
    AccountTypeGroup.OTHER -> R.string.account_group_other_title
}

@StringRes
private fun AccountTypeGroup.descriptionRes(): Int = when (this) {
    AccountTypeGroup.EVERYDAY -> R.string.account_group_everyday_description
    AccountTypeGroup.SAVINGS -> R.string.account_group_savings_description
    AccountTypeGroup.CREDIT -> R.string.account_group_credit_description
    AccountTypeGroup.LIABILITIES -> R.string.account_group_liabilities_description
    AccountTypeGroup.COUNTERPARTY -> R.string.account_group_counterparty_description
    AccountTypeGroup.OTHER -> R.string.account_group_other_description
}

@Composable
fun AccountTypeMetadata.localizedDisplayName(): String {
    typeNameRes(code)?.let { return stringResource(it) }
    return displayName
}

@Composable
fun AccountTypeMetadata.localizedDescription(): String {
    typeDescriptionRes(code)?.let { return stringResource(it) }
    return stringResource(R.string.account_type_unknown_description, localizedDisplayName())
}

@StringRes
private fun typeNameRes(typeCode: String): Int? = when (typeCode.lowercase()) {
    "default", "checking" -> R.string.account_type_checking_name
    "joined" -> R.string.account_type_joint_checking_name
    "cash" -> R.string.account_type_cash_name
    "savings", "saving" -> R.string.account_type_savings_name
    "joined_savings" -> R.string.account_type_joint_savings_name
    "credit_card", "creditcard" -> R.string.account_type_credit_card_name
    "loan" -> R.string.account_type_loan_name
    "mortgage" -> R.string.account_type_mortgage_name
    "debt" -> R.string.account_type_debt_name
    "liability" -> R.string.account_type_liability_name
    "investment" -> R.string.account_type_investment_name
    "creditor" -> R.string.account_type_creditor_name
    "debtor", "debit" -> R.string.account_type_debtor_name
    else -> null
}

@StringRes
private fun typeDescriptionRes(typeCode: String): Int? = when (typeCode.lowercase()) {
    "default", "checking" -> R.string.account_type_checking_description
    "joined" -> R.string.account_type_joint_checking_description
    "cash" -> R.string.account_type_cash_description
    "savings", "saving" -> R.string.account_type_savings_description
    "joined_savings" -> R.string.account_type_joint_savings_description
    "credit_card", "creditcard" -> R.string.account_type_credit_card_description
    "loan" -> R.string.account_type_loan_description
    "mortgage" -> R.string.account_type_mortgage_description
    "debt" -> R.string.account_type_debt_description
    "liability" -> R.string.account_type_liability_description
    "investment" -> R.string.account_type_investment_description
    "creditor" -> R.string.account_type_creditor_description
    "debtor", "debit" -> R.string.account_type_debtor_description
    else -> null
}
