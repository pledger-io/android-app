package com.pledgerio.app.transactions.ui

import com.pledgerio.app.transactions.ui.TransactionFormUiState
import com.pledgerio.app.accounts.domain.model.Account
import com.pledgerio.app.transactions.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TransactionFormLiabilityAccountsTest {

    private val checking = Account(id = 1, name = "Checking", typeCode = "default")
    private val loan = Account(id = 2, name = "Car loan", typeCode = "loan")
    private val accounts = listOf(checking, loan)

    @Test
    fun `liabilities are never offered as source`() {
        TransactionType.entries.forEach { type ->
            val state = TransactionFormUiState(type = type, ownedAccounts = accounts)
            assertEquals(listOf(checking), state.sourceOwnedAccounts)
        }
    }

    @Test
    fun `liabilities are offered as transfer destination only`() {
        assertEquals(
            accounts,
            TransactionFormUiState(type = TransactionType.TRANSFER, ownedAccounts = accounts).targetOwnedAccounts,
        )
        assertEquals(
            listOf(checking),
            TransactionFormUiState(type = TransactionType.DEBIT, ownedAccounts = accounts).targetOwnedAccounts,
        )
    }

    @Test
    fun `income cannot be submitted when only liabilities exist`() {
        val state = TransactionFormUiState(
            type = TransactionType.DEBIT,
            ownedAccounts = listOf(loan),
            amount = "10",
            sourceAccountId = 5,
            targetAccountId = loan.id,
        )
        assertFalse(state.canSubmit)
    }
}
