package com.pledgerio.app.accounts.domain.model

import com.pledgerio.app.accounts.domain.model.PagedAccounts
import com.pledgerio.app.accounts.domain.model.Account
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PagedAccountsTest {

  @Test
  fun `hasMore is true when more records exist`() {
    val page = PagedAccounts(
      items = List(50) { Account(id = it.toLong(), name = "A$it") },
      totalRecords = 120,
      offset = 0,
      pageSize = 50,
    )
    assertTrue(page.hasMore)
  }

  @Test
  fun `hasMore is false on last page`() {
    val page = PagedAccounts(
      items = List(20) { Account(id = it.toLong(), name = "A$it") },
      totalRecords = 120,
      offset = 100,
      pageSize = 50,
    )
    assertFalse(page.hasMore)
  }
}
