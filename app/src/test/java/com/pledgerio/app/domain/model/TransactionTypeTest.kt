package com.pledgerio.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionTypeTest {

  @Test
  fun `fromString maps API labels`() {
    assertEquals(TransactionType.DEBIT, TransactionType.fromString("DEBIT"))
    assertEquals(TransactionType.CREDIT, TransactionType.fromString("CREDIT"))
    assertEquals(TransactionType.TRANSFER, TransactionType.fromString("TRANSFER"))
  }

  @Test
  fun `fromString maps legacy income and expense aliases`() {
    assertEquals(TransactionType.CREDIT, TransactionType.fromString("INCOME"))
    assertEquals(TransactionType.DEBIT, TransactionType.fromString("EXPENSE"))
  }
}
