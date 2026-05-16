package com.pledgerio.app.ui.accounts

import com.pledgerio.app.domain.model.Account
import com.pledgerio.app.domain.model.AccountListFilter
import com.pledgerio.app.domain.model.AccountTypeOption
import com.pledgerio.app.domain.model.PagedAccounts
import com.pledgerio.app.domain.repository.AccountRepository
import com.pledgerio.app.util.MainDispatcherRule
import com.pledgerio.app.util.Resource
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AccountsViewModelTest {

  @get:Rule
  val mainDispatcherRule = MainDispatcherRule()

  private val repository = mockk<AccountRepository>()

  private fun ownedAccount() = Account(id = 1, name = "Checking", typeCode = "default")

  private fun counterpartyAccount() = Account(id = 2, name = "Shop", typeCode = "creditor")

  @Test
  fun `loads owned accounts on init`() = runTest {
    every { repository.getAccounts() } returns flowOf(Resource.Success(listOf(ownedAccount())))
    coEvery { repository.getAccountTypes() } returns Resource.Success(emptyList())
    coEvery { repository.getCounterpartyAccountsPage(0, 1, "") } returns Resource.Success(
      PagedAccounts(emptyList(), totalRecords = 0, offset = 0, pageSize = 1),
    )

    val viewModel = AccountsViewModel(repository)
    advanceUntilIdle()

    assertEquals(1, viewModel.uiState.value.ownedAccounts.size)
    assertFalse(viewModel.uiState.value.isLoading)
  }

  @Test
  fun `loads counterparty total from first page metadata`() = runTest {
    every { repository.getAccounts() } returns flowOf(Resource.Success(emptyList()))
    coEvery { repository.getAccountTypes() } returns Resource.Success(emptyList())
    coEvery { repository.getCounterpartyAccountsPage(0, 1, "") } returns Resource.Success(
      PagedAccounts(emptyList(), totalRecords = 342, offset = 0, pageSize = 1),
    )

    val viewModel = AccountsViewModel(repository)
    advanceUntilIdle()

    assertEquals(342, viewModel.uiState.value.counterpartyTotal)
  }

  @Test
  fun `setFilter counterparty loads first page`() = runTest {
    every { repository.getAccounts() } returns flowOf(Resource.Success(emptyList()))
    coEvery { repository.getAccountTypes() } returns Resource.Success(emptyList())
    coEvery { repository.getCounterpartyAccountsPage(0, 1, "") } returns Resource.Success(
      PagedAccounts(emptyList(), totalRecords = 2, offset = 0, pageSize = 1),
    )
    coEvery { repository.getCounterpartyAccountsPage(0, 50, "") } returns Resource.Success(
      PagedAccounts(
        items = listOf(counterpartyAccount()),
        totalRecords = 2,
        offset = 0,
        pageSize = 50,
      ),
    )

    val viewModel = AccountsViewModel(repository)
    advanceUntilIdle()

    viewModel.setFilter(AccountListFilter.COUNTERPARTY)
    advanceUntilIdle()

    assertEquals(1, viewModel.uiState.value.counterpartyAccounts.size)
    assertEquals(AccountListFilter.COUNTERPARTY, viewModel.uiState.value.filter)
  }

  @Test
  fun `loadMoreCounterparties appends next page`() = runTest {
    every { repository.getAccounts() } returns flowOf(Resource.Success(emptyList()))
    coEvery { repository.getAccountTypes() } returns Resource.Success(emptyList())
    coEvery { repository.getCounterpartyAccountsPage(0, 1, "") } returns Resource.Success(
      PagedAccounts(emptyList(), totalRecords = 2, offset = 0, pageSize = 1),
    )
    coEvery { repository.getCounterpartyAccountsPage(0, 50, "") } returns Resource.Success(
      PagedAccounts(
        items = listOf(counterpartyAccount()),
        totalRecords = 2,
        offset = 0,
        pageSize = 50,
      ),
    )
    coEvery { repository.getCounterpartyAccountsPage(1, 50, "") } returns Resource.Success(
      PagedAccounts(
        items = listOf(Account(id = 3, name = "Employer", typeCode = "debtor")),
        totalRecords = 2,
        offset = 1,
        pageSize = 50,
      ),
    )

    val viewModel = AccountsViewModel(repository)
    advanceUntilIdle()
    viewModel.setFilter(AccountListFilter.COUNTERPARTY)
    advanceUntilIdle()

    viewModel.loadMoreCounterparties()
    advanceUntilIdle()

    assertEquals(2, viewModel.uiState.value.counterpartyAccounts.size)
    assertFalse(viewModel.uiState.value.hasMoreCounterparties)
  }

  @Test
  fun `applyAccountSaved prepends counterparty on parties filter`() = runTest {
    val debtor = Account(id = 99, name = "Employer", typeCode = "debtor")
    every { repository.getAccounts() } returns flowOf(Resource.Success(emptyList()))
    coEvery { repository.getAccountTypes() } returns Resource.Success(emptyList())
    coEvery { repository.getCounterpartyAccountsPage(0, 1, "") } returns Resource.Success(
      PagedAccounts(emptyList(), totalRecords = 0, offset = 0, pageSize = 1),
    )
    coEvery { repository.getCounterpartyAccountsPage(0, 50, "") } returns Resource.Success(
      PagedAccounts(emptyList(), totalRecords = 0, offset = 0, pageSize = 50),
    )

    val viewModel = AccountsViewModel(repository)
    advanceUntilIdle()
    viewModel.setFilter(AccountListFilter.COUNTERPARTY)
    advanceUntilIdle()

    viewModel.applyAccountSaved(debtor)

    assertEquals(1, viewModel.uiState.value.counterpartyAccounts.size)
    assertEquals("Employer", viewModel.uiState.value.counterpartyAccounts.first().name)
    assertEquals(1, viewModel.uiState.value.counterpartyTotal)
  }

  @Test
  fun `applyAccountSaved increments counterparty total on all filter`() = runTest {
    val debtor = Account(id = 99, name = "Employer", typeCode = "debtor")
    every { repository.getAccounts() } returns flowOf(Resource.Success(emptyList()))
    coEvery { repository.getAccountTypes() } returns Resource.Success(emptyList())
    coEvery { repository.getCounterpartyAccountsPage(0, 1, "") } returns Resource.Success(
      PagedAccounts(emptyList(), totalRecords = 5, offset = 0, pageSize = 1),
    )

    val viewModel = AccountsViewModel(repository)
    advanceUntilIdle()

    viewModel.applyAccountSaved(debtor)

    assertEquals(6, viewModel.uiState.value.counterpartyTotal)
    assertTrue(viewModel.uiState.value.counterpartyAccounts.isEmpty())
  }

  @Test
  fun `account type options loaded from repository`() = runTest {
    val types = listOf(AccountTypeOption("default", "Checking"))
    every { repository.getAccounts() } returns flowOf(Resource.Success(emptyList()))
    coEvery { repository.getAccountTypes() } returns Resource.Success(types)
    coEvery { repository.getCounterpartyAccountsPage(0, 1, "") } returns Resource.Success(
      PagedAccounts(emptyList(), totalRecords = 0, offset = 0, pageSize = 1),
    )

    val viewModel = AccountsViewModel(repository)
    advanceUntilIdle()

    assertEquals(types, viewModel.uiState.value.accountTypeOptions)
  }
}
