package com.pledgerio.app.ui.accounts

import com.pledgerio.app.domain.model.Account
import com.pledgerio.app.domain.model.AccountListFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountsUiStateTest {

  @Test
  fun `owned filter uses only owned accounts`() {
    val state = AccountsUiState(
      ownedAccounts = listOf(
        Account(id = 1, name = "Bank", typeCode = "default"),
      ),
      counterpartyAccounts = listOf(
        Account(id = 2, name = "Shop", typeCode = "creditor"),
      ),
      filter = AccountListFilter.OWNED,
    )
    assertEquals(1, state.filteredAccounts.size)
    assertEquals("Bank", state.filteredAccounts.first().name)
  }

  @Test
  fun `counterparty filter uses paginated list`() {
    val state = AccountsUiState(
      ownedAccounts = listOf(Account(id = 1, name = "Bank", typeCode = "default")),
      counterpartyAccounts = listOf(Account(id = 2, name = "Shop", typeCode = "creditor")),
      filter = AccountListFilter.COUNTERPARTY,
    )
    assertEquals(1, state.filteredAccounts.size)
    assertEquals("Shop", state.filteredAccounts.first().name)
  }

  @Test
  fun `all filter shows owned only not loaded parties`() {
    val state = AccountsUiState(
      ownedAccounts = listOf(Account(id = 1, name = "Bank", typeCode = "default")),
      counterpartyAccounts = listOf(Account(id = 2, name = "Shop", typeCode = "creditor")),
      counterpartyTotal = 50,
      filter = AccountListFilter.ALL,
    )
    assertEquals(1, state.filteredAccounts.size)
    assertTrue(state.showCounterpartyBrowseCard)
  }

  @Test
  fun `showCounterpartyBrowseCard hidden on parties filter`() {
    val state = AccountsUiState(
      counterpartyTotal = 10,
      filter = AccountListFilter.COUNTERPARTY,
    )
    assertFalse(state.showCounterpartyBrowseCard)
  }
}
