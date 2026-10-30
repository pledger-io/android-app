package com.pledgerio.app.catalog.domain.model

data class AccountTypeOption(
    val code: String,
    val displayName: String,
    val description: String = "",
    val isCounterparty: Boolean = false,
    val group: AccountTypeGroup = if (isCounterparty) {
        AccountTypeGroup.COUNTERPARTY
    } else {
        AccountTypeGroup.OTHER
    },
)

object AccountTypeCodes {
    const val CREDITOR = "creditor"
    const val DEBTOR = "debtor"

    val counterpartyTypeCodes: Set<String> = setOf(CREDITOR, DEBTOR, "debit")

    const val LOAN = "loan"
    const val MORTGAGE = "mortgage"
    const val DEBT = "debt"
    const val RECONCILE = "reconcile"

    /** Liability types the backend keeps hidden from `GET /v2/api/account-types`. */
    val liabilityTypeCodes: List<String> = listOf(LOAN, MORTGAGE, DEBT)

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
