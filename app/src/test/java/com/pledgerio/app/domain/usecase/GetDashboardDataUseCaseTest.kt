package com.pledgerio.app.domain.usecase

import com.pledgerio.app.domain.model.Account
import com.pledgerio.app.domain.model.Transaction
import com.pledgerio.app.domain.model.TransactionType
import com.pledgerio.app.domain.repository.AccountRepository
import com.pledgerio.app.domain.repository.TransactionRepository
import com.pledgerio.app.util.Resource
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class GetDashboardDataUseCaseTest {

  private lateinit var accountRepository: AccountRepository
  private lateinit var transactionRepository: TransactionRepository
  private lateinit var useCase: GetDashboardDataUseCase

  @Before
  fun setup() {
    accountRepository = mockk()
    transactionRepository = mockk()
    useCase = GetDashboardDataUseCase(accountRepository, transactionRepository)
  }

  @Test
  fun `returns combined dashboard data on success`() = runTest {
    val accounts = listOf(
      Account(id = 1, name = "Checking", balance = 1000.0, typeCode = "default"),
      Account(id = 2, name = "Savings", balance = 5000.0, typeCode = "savings"),
    )
    val transactions = listOf(
      Transaction(
        id = 1,
        description = "Salary",
        amount = 3000.0,
        type = TransactionType.DEBIT,
        date = LocalDate.now(),
        destinationAccountId = 1,
        destinationAccountName = "Checking",
      ),
      Transaction(
        id = 2,
        description = "Groceries",
        amount = 150.0,
        type = TransactionType.CREDIT,
        date = LocalDate.now(),
        sourceAccountId = 1,
        sourceAccountName = "Checking",
      ),
    )

    every { accountRepository.getAccounts() } returns flowOf(Resource.Success(accounts))
    every { transactionRepository.getRecentTransactions(5) } returns flowOf(Resource.Success(transactions))

    val result = useCase().first()
    assertTrue(result is Resource.Success)
    val data = (result as Resource.Success).data
    assertEquals(6000.0, data.netWorth, 0.01)
    assertEquals(3000.0, data.monthlyIncome, 0.01)
    assertEquals(150.0, data.monthlyExpense, 0.01)
    assertEquals(2, data.recentTransactions.size)
  }

  @Test
  fun `returns account error when accounts fail`() = runTest {
    every { accountRepository.getAccounts() } returns flowOf(Resource.Error("Network down"))
    every { transactionRepository.getRecentTransactions(5) } returns flowOf(Resource.Success(emptyList()))

    val result = useCase().first()
    assertTrue(result is Resource.Error)
    assertEquals("Network down", (result as Resource.Error).message)
  }

  @Test
  fun `returns transaction error when transactions fail`() = runTest {
    every { accountRepository.getAccounts() } returns flowOf(Resource.Success(emptyList()))
    every { transactionRepository.getRecentTransactions(5) } returns flowOf(Resource.Error("Tx error"))

    val result = useCase().first()
    assertTrue(result is Resource.Error)
    assertEquals("Tx error", (result as Resource.Error).message)
  }
}
