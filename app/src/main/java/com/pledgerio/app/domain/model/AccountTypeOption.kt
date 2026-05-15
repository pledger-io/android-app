package com.pledgerio.app.domain.model

data class AccountTypeOption(
    val code: String,
    val displayName: String,
    val isCounterparty: Boolean = false,
)

object AccountTypeCodes {
    const val CREDITOR = "creditor"
    const val DEBTOR = "debtor"

    val counterpartyTypes = listOf(
        AccountTypeOption(CREDITOR, "Creditor", isCounterparty = true),
        AccountTypeOption(DEBTOR, "Debtor", isCounterparty = true),
    )
}

fun String.toAccountTypeDisplayName(): String = when (lowercase()) {
    "default" -> "Checking"
    "creditor" -> "Creditor"
    "debtor", "debit" -> "Debtor"
    "credit_card" -> "Credit Card"
    "joined_savings" -> "Joint Savings"
    else -> split("_")
        .joinToString(" ") { word ->
            word.replaceFirstChar { c -> c.uppercase() }
        }
}
