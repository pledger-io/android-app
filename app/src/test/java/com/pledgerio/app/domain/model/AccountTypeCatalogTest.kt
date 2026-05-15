package com.pledgerio.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountTypeCatalogTest {

  @Test
  fun `isCounterparty recognizes creditor debtor and debit`() {
    assertTrue(AccountTypeCatalog.isCounterparty("creditor"))
    assertTrue(AccountTypeCatalog.isCounterparty("debtor"))
    assertTrue(AccountTypeCatalog.isCounterparty("debit"))
    assertTrue(AccountTypeCatalog.isCounterparty("CREDITOR"))
  }

  @Test
  fun `isCounterparty rejects owned types`() {
    assertFalse(AccountTypeCatalog.isCounterparty("default"))
    assertFalse(AccountTypeCatalog.isCounterparty("savings"))
    assertFalse(AccountTypeCatalog.isCounterparty("credit_card"))
  }

  @Test
  fun `filterAccounts owned filter excludes counterparties`() {
    val accounts = listOf(
      Account(id = 1, name = "Bank", typeCode = "default"),
      Account(id = 2, name = "Shop", typeCode = "creditor"),
    )
    val filtered = AccountTypeCatalog.filterAccounts(accounts, AccountListFilter.OWNED)
    assertEquals(1, filtered.size)
    assertEquals("Bank", filtered.first().name)
  }

  @Test
  fun `filterAccounts counterparty filter includes only parties`() {
    val accounts = listOf(
      Account(id = 1, name = "Bank", typeCode = "default"),
      Account(id = 2, name = "Employer", typeCode = "debtor"),
    )
    val filtered = AccountTypeCatalog.filterAccounts(accounts, AccountListFilter.COUNTERPARTY)
    assertEquals(1, filtered.size)
    assertEquals("Employer", filtered.first().name)
  }

  @Test
  fun `sectionAccounts deduplicates by id`() {
    val accounts = listOf(
      Account(id = 1, name = "A", typeCode = "default"),
      Account(id = 1, name = "A duplicate", typeCode = "default"),
    )
    val sections = AccountTypeCatalog.sectionAccounts(accounts)
    val everyday = sections.single { it.group == AccountTypeGroup.EVERYDAY }
    assertEquals(1, everyday.accounts.size)
  }

  @Test
  fun `toOptions adds counterparty types to owned api types`() {
    val options = AccountTypeCatalog.toOptions(listOf("default", "savings"))
    assertTrue(options.any { it.code == "default" && !it.isCounterparty })
    assertTrue(options.any { it.code == "creditor" && it.isCounterparty })
    assertTrue(options.any { it.code == "debtor" && it.isCounterparty })
  }

  @Test
  fun `metadataFor known checking types`() {
    val meta = AccountTypeCatalog.metadataFor("default")
    assertEquals("Checking", meta.displayName)
    assertFalse(meta.isCounterparty)
    assertEquals(AccountTypeGroup.EVERYDAY, meta.group)
  }

  @Test
  fun `metadataFor creditor hides opening balance and bank details`() {
    val meta = AccountTypeCatalog.metadataFor("creditor")
    assertTrue(meta.isCounterparty)
    assertFalse(meta.showOpeningBalance)
    assertFalse(meta.showBankDetails)
  }
}
