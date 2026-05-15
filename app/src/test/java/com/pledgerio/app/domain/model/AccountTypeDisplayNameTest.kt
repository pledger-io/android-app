package com.pledgerio.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AccountTypeDisplayNameTest {

  @Test
  fun `toAccountTypeDisplayName maps known codes`() {
    assertEquals("Checking", "default".toAccountTypeDisplayName())
    assertEquals("Creditor", "creditor".toAccountTypeDisplayName())
    assertEquals("Debtor", "debtor".toAccountTypeDisplayName())
    assertEquals("Debtor", "debit".toAccountTypeDisplayName())
    assertEquals("Joint Savings", "joined_savings".toAccountTypeDisplayName())
  }

  @Test
  fun `toAccountTypeDisplayName title-cases unknown codes`() {
    assertEquals("Custom Type", "custom_type".toAccountTypeDisplayName())
  }
}
