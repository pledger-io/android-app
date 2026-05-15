package com.pledgerio.app.domain.model

enum class AccountListFilter {
    ALL,
    OWNED,
    COUNTERPARTY,
}

enum class AccountTypeGroup(val title: String, val description: String) {
    EVERYDAY(
        title = "Everyday banking",
        description = "Current accounts and cash for daily spending",
    ),
    SAVINGS(
        title = "Savings",
        description = "Money set aside for goals and reserves",
    ),
    CREDIT(
        title = "Credit",
        description = "Cards and revolving credit lines",
    ),
    LIABILITIES(
        title = "Loans & liabilities",
        description = "Money you owe — mortgages, loans, and debt",
    ),
    COUNTERPARTY(
        title = "Counterparties",
        description = "Parties you pay or receive money from in transactions",
    ),
    OTHER(
        title = "Other",
        description = "Additional account types from your server",
    ),
}

data class AccountTypeMetadata(
    val code: String,
    val displayName: String,
    val description: String,
    val isCounterparty: Boolean,
    val group: AccountTypeGroup,
    val showOpeningBalance: Boolean = true,
    val showBankDetails: Boolean = true,
) {
    val category: AccountListFilter
        get() = if (isCounterparty) AccountListFilter.COUNTERPARTY else AccountListFilter.OWNED
}

data class AccountSection(
    val group: AccountTypeGroup,
    val accounts: List<Account>,
) {
    val totalBalance: Double get() = accounts.sumOf { it.balance }
}

object AccountTypeCatalog {

    private val knownTypes = mapOf(
        "default" to metadata(
            code = "default",
            displayName = "Checking",
            description = "Your main bank account for everyday payments and direct debits.",
            group = AccountTypeGroup.EVERYDAY,
        ),
        "checking" to metadata(
            code = "checking",
            displayName = "Checking",
            description = "Your main bank account for everyday payments and direct debits.",
            group = AccountTypeGroup.EVERYDAY,
        ),
        "joined" to metadata(
            code = "joined",
            displayName = "Joint checking",
            description = "A shared current account for household spending.",
            group = AccountTypeGroup.EVERYDAY,
        ),
        "cash" to metadata(
            code = "cash",
            displayName = "Cash",
            description = "Physical cash kept on hand, outside the bank.",
            group = AccountTypeGroup.EVERYDAY,
            showBankDetails = false,
        ),
        "savings" to metadata(
            code = "savings",
            displayName = "Savings",
            description = "Funds reserved for goals, emergencies, or future purchases.",
            group = AccountTypeGroup.SAVINGS,
        ),
        "saving" to metadata(
            code = "saving",
            displayName = "Savings",
            description = "Funds reserved for goals, emergencies, or future purchases.",
            group = AccountTypeGroup.SAVINGS,
        ),
        "joined_savings" to metadata(
            code = "joined_savings",
            displayName = "Joint savings",
            description = "Shared savings held with a partner or household.",
            group = AccountTypeGroup.SAVINGS,
        ),
        "credit_card" to metadata(
            code = "credit_card",
            displayName = "Credit card",
            description = "Revolving credit — spending increases what you owe the issuer.",
            group = AccountTypeGroup.CREDIT,
        ),
        "creditcard" to metadata(
            code = "creditcard",
            displayName = "Credit card",
            description = "Revolving credit — spending increases what you owe the issuer.",
            group = AccountTypeGroup.CREDIT,
        ),
        "loan" to metadata(
            code = "loan",
            displayName = "Loan",
            description = "A loan balance you repay over time.",
            group = AccountTypeGroup.LIABILITIES,
        ),
        "mortgage" to metadata(
            code = "mortgage",
            displayName = "Mortgage",
            description = "Property financing — principal and interest owed to the lender.",
            group = AccountTypeGroup.LIABILITIES,
        ),
        "debt" to metadata(
            code = "debt",
            displayName = "Debt",
            description = "General debt or liability you are paying down.",
            group = AccountTypeGroup.LIABILITIES,
        ),
        "liability" to metadata(
            code = "liability",
            displayName = "Liability",
            description = "Money you owe to a lender or institution.",
            group = AccountTypeGroup.LIABILITIES,
        ),
        "investment" to metadata(
            code = "investment",
            displayName = "Investment",
            description = "Brokerage or investment account holdings.",
            group = AccountTypeGroup.OTHER,
        ),
        AccountTypeCodes.CREDITOR to metadata(
            code = AccountTypeCodes.CREDITOR,
            displayName = "Creditor",
            description = "Someone you pay — shops, utilities, landlords, subscriptions.",
            isCounterparty = true,
            group = AccountTypeGroup.COUNTERPARTY,
            showOpeningBalance = false,
        ),
        AccountTypeCodes.DEBTOR to metadata(
            code = AccountTypeCodes.DEBTOR,
            displayName = "Debtor",
            description = "Someone who pays you — employer, clients, refunds, gifts.",
            isCounterparty = true,
            group = AccountTypeGroup.COUNTERPARTY,
            showOpeningBalance = false,
        ),
        "debit" to metadata(
            code = "debit",
            displayName = "Debtor",
            description = "Someone who pays you — employer, clients, refunds, gifts.",
            isCounterparty = true,
            group = AccountTypeGroup.COUNTERPARTY,
            showOpeningBalance = false,
        ),
    )

    fun metadataFor(typeCode: String): AccountTypeMetadata {
        val key = typeCode.lowercase()
        return knownTypes[key] ?: AccountTypeMetadata(
            code = typeCode,
            displayName = typeCode.toAccountTypeDisplayName(),
            description = "Account type “${typeCode.toAccountTypeDisplayName()}” from your Pledger server.",
            isCounterparty = key in AccountTypeCodes.counterpartyTypeCodes,
            group = if (key in AccountTypeCodes.counterpartyTypeCodes) {
                AccountTypeGroup.COUNTERPARTY
            } else {
                AccountTypeGroup.OTHER
            },
        )
    }

    fun isCounterparty(typeCode: String): Boolean {
        val normalized = typeCode.trim().lowercase()
        if (normalized in AccountTypeCodes.counterpartyTypeCodes) return true
        return when (AccountType.fromApiLabel(typeCode)) {
            AccountType.CREDITOR, AccountType.DEBTOR -> true
            else -> metadataFor(typeCode).isCounterparty
        }
    }

    fun toOptions(apiOwnedTypeCodes: List<String>): List<AccountTypeOption> {
        val owned = apiOwnedTypeCodes.map { code ->
            val meta = metadataFor(code)
            AccountTypeOption(
                code = code,
                displayName = meta.displayName,
                description = meta.description,
                isCounterparty = false,
                group = meta.group,
            )
        }
        val counterparty = AccountTypeCodes.counterpartyTypes.map { option ->
            val meta = metadataFor(option.code)
            option.copy(
                description = meta.description,
                group = meta.group,
            )
        }
        return owned + counterparty
    }

    fun filterAccounts(accounts: List<Account>, filter: AccountListFilter): List<Account> {
        return when (filter) {
            AccountListFilter.ALL -> accounts
            AccountListFilter.OWNED -> accounts.filter { !isCounterparty(it.typeCode) }
            AccountListFilter.COUNTERPARTY -> accounts.filter { isCounterparty(it.typeCode) }
        }
    }

    fun sectionAccounts(accounts: List<Account>): List<AccountSection> {
        val unique = accounts.distinctBy { it.id }
        val grouped = unique.groupBy { metadataFor(it.typeCode).group }
        return AccountTypeGroup.entries.mapNotNull { group ->
            grouped[group]?.takeIf { it.isNotEmpty() }?.let { list ->
                AccountSection(
                    group = group,
                    accounts = list.sortedBy { it.name.lowercase() },
                )
            }
        }
    }

    private fun metadata(
        code: String,
        displayName: String,
        description: String,
        isCounterparty: Boolean = false,
        group: AccountTypeGroup,
        showOpeningBalance: Boolean = true,
        showBankDetails: Boolean = true,
    ) = AccountTypeMetadata(
        code = code,
        displayName = displayName,
        description = description,
        isCounterparty = isCounterparty,
        group = group,
        showOpeningBalance = showOpeningBalance,
        showBankDetails = showBankDetails,
    )
}
