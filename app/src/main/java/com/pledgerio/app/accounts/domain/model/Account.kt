package com.pledgerio.app.accounts.domain.model
import com.pledgerio.app.accounts.domain.model.AccountTypeCatalog
import com.pledgerio.app.accounts.domain.model.toAccountTypeDisplayName

data class Account(
    val id: Long,
    val name: String,
    val description: String = "",
    val currency: String = "EUR",
    val balance: Double = 0.0,
    val typeCode: String = "default",
    val iconFileCode: String? = null,
    val iban: String? = null,
    val bic: String? = null,
    val openingBalance: Double = 0.0,
    val lastActivity: String? = null,
    val accountNumber: String? = null,
    /** Interest rate as a fraction (0.035 = 3.5 %); only used by liabilities. */
    val interest: Double? = null,
    /** `MONTHS` or `YEARS`; only used by liabilities. */
    val interestPeriodicity: String? = null,
) {
    val isLiability: Boolean get() = AccountTypeCatalog.isLiability(typeCode)
    val type: AccountType get() = AccountType.fromApiLabel(typeCode)

    val typeDisplayName: String get() = typeCode.toAccountTypeDisplayName()
}

enum class AccountType {
    CHECKING,
    SAVINGS,
    CREDIT_CARD,
    CASH,
    LIABILITY,
    LOAN,
    INVESTMENT,
    MORTGAGE,
    CREDITOR,
    DEBTOR,
    OTHER;

    companion object {
        fun fromApiLabel(label: String): AccountType = when (label.lowercase()) {
            "default", "checking", "joined" -> CHECKING
            "savings", "saving", "joined_savings" -> SAVINGS
            "credit_card", "credit card", "creditcard" -> CREDIT_CARD
            "cash" -> CASH
            "liability", "debt" -> LIABILITY
            "loan" -> LOAN
            "investment" -> INVESTMENT
            "mortgage" -> MORTGAGE
            "creditor" -> CREDITOR
            "debtor", "debit" -> DEBTOR
            else -> OTHER
        }

        @Deprecated("Use fromApiLabel", ReplaceWith("fromApiLabel(value)"))
        fun fromString(value: String): AccountType = fromApiLabel(value)
    }
}
