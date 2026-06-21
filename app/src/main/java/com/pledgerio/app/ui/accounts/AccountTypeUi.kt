package com.pledgerio.app.ui.accounts

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.ui.graphics.vector.ImageVector
import com.pledgerio.app.domain.model.Account
import com.pledgerio.app.domain.model.AccountType
import com.pledgerio.app.domain.model.AccountTypeCatalog
import com.pledgerio.app.domain.model.AccountTypeGroup

fun accountTypeIcon(typeCode: String): ImageVector {
    return when (AccountType.fromApiLabel(typeCode)) {
        AccountType.SAVINGS -> Icons.Default.Savings
        AccountType.CREDIT_CARD -> Icons.Default.CreditCard
        AccountType.CASH -> Icons.Default.Wallet
        AccountType.CREDITOR -> Icons.Default.Store
        AccountType.DEBTOR -> Icons.Default.Payments
        AccountType.LOAN, AccountType.MORTGAGE, AccountType.LIABILITY -> Icons.AutoMirrored.Filled.TrendingDown
        AccountType.INVESTMENT -> Icons.AutoMirrored.Filled.TrendingUp
        AccountType.CHECKING -> when (typeCode.lowercase()) {
            "joined", "joined_savings" -> Icons.Default.Group
            else -> Icons.Default.AccountBalance
        }
        else -> Icons.Default.AccountBalance
    }
}

fun Account.icon(): ImageVector = accountTypeIcon(typeCode)

fun AccountTypeGroup.icon(): ImageVector = when (this) {
    AccountTypeGroup.EVERYDAY -> Icons.Default.AccountBalance
    AccountTypeGroup.SAVINGS -> Icons.Default.Savings
    AccountTypeGroup.CREDIT -> Icons.Default.CreditCard
    AccountTypeGroup.LIABILITIES -> Icons.AutoMirrored.Filled.TrendingDown
    AccountTypeGroup.COUNTERPARTY -> Icons.Default.Payments
    AccountTypeGroup.OTHER -> Icons.Default.AccountBalance
}

fun Account.typeDescription(): String = AccountTypeCatalog.metadataFor(typeCode).description
