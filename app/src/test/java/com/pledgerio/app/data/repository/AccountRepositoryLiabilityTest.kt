package com.pledgerio.app.data.repository

import com.pledgerio.app.shared.Resource
import com.pledgerio.app.shared.data.cache.CacheRefresher
import com.pledgerio.app.data.local.dao.AccountDao
import com.pledgerio.app.data.local.dao.AccountTypeDao
import com.pledgerio.app.shared.data.remote.api.PledgerApiService
import com.pledgerio.app.data.remote.dto.AccountDto
import com.pledgerio.app.data.remote.dto.AccountIdentificationDto
import com.pledgerio.app.data.remote.dto.AccountInterestDto
import com.pledgerio.app.data.remote.dto.AccountPagedResponse
import com.pledgerio.app.data.remote.dto.BalanceDto
import com.pledgerio.app.data.remote.dto.CreateAccountRequest
import com.pledgerio.app.data.remote.dto.CreateTransactionRequest
import com.pledgerio.app.data.remote.dto.PageInfo
import com.pledgerio.app.domain.model.Account
import com.pledgerio.app.util.FakeSyncMetadataDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.time.LocalDate

class AccountRepositoryLiabilityTest {

    private val apiService = mockk<PledgerApiService>()
    private val accountDao = mockk<AccountDao>(relaxed = true)
    private val accountTypeDao = mockk<AccountTypeDao>(relaxed = true)
    private val syncMetadataDao = FakeSyncMetadataDao()
    private lateinit var repository: AccountRepositoryImpl

    private val mortgage = Account(id = 7, name = "House", typeCode = "mortgage", currency = "EUR")

    @Before
    fun setUp() {
        repository = AccountRepositoryImpl(
            apiService,
            accountDao,
            accountTypeDao,
            CacheRefresher(syncMetadataDao, TestScope()),
        )
        coEvery { accountDao.getAll() } returns flowOf(emptyList())
    }

    @Test
    fun `refreshOwnedAccounts requests hidden liability types and maps interest`() = runTest {
        // The backend hides loan/mortgage/debt from account-types.
        coEvery { accountTypeDao.getAllCodes() } returns listOf("default", "savings")
        coEvery {
            apiService.getAccounts(
                type = match { it != null && it.containsAll(listOf("default", "loan", "mortgage", "debt")) },
                offset = 0,
                numberOfResults = 200,
            )
        } returns Response.success(
            AccountPagedResponse(
                content = listOf(
                    AccountDto(id = 1, name = "Bank", type = "default"),
                    AccountDto(
                        id = 7,
                        name = "House",
                        type = "mortgage",
                        account = AccountIdentificationDto(number = "M-1", currency = "EUR"),
                        interest = AccountInterestDto(periodicity = "YEARS", interest = 0.035),
                    ),
                ),
                info = PageInfo(records = 2, pages = 1, pageSize = 200),
            ),
        )
        coEvery { apiService.getBalance(any()) } returns Response.success(BalanceDto(balance = -199000.0))

        val result = repository.refreshOwnedAccounts()

        assertTrue(result is Resource.Success)
        val house = (result as Resource.Success).data.single { it.id == 7L }
        assertTrue(house.isLiability)
        assertEquals(0.035, house.interest!!, 0.0)
        assertEquals("YEARS", house.interestPeriodicity)
        assertEquals("M-1", house.accountNumber)
        assertEquals(-199000.0, house.balance, 0.0)
        coVerify {
            accountDao.replaceByTypes(match { it.containsAll(listOf("loan", "mortgage", "debt")) }, any())
        }
    }

    @Test
    fun `getAccountTypes offers liability types the server hides`() = runTest {
        coEvery { accountTypeDao.getAllCodes() } returns listOf("default", "creditor")

        val result = repository.getAccountTypes()

        val codes = (result as Resource.Success).data.filter { !it.isCounterparty }.map { it.code }
        assertEquals(listOf("default", "loan", "mortgage", "debt"), codes)
    }

    @Test
    fun `updateAccount sends interest and periodicity together`() = runTest {
        val request = slot<CreateAccountRequest>()
        coEvery { apiService.updateAccount(7, capture(request)) } returns Response.success(
            AccountDto(id = 7, name = "House", type = "mortgage"),
        )

        repository.updateAccount(
            mortgage.copy(interest = 0.04, interestPeriodicity = "MONTHS", accountNumber = "M-1"),
        )

        assertEquals(0.04, request.captured.interest!!, 0.0)
        assertEquals("MONTHS", request.captured.interestPeriodicity)
        assertEquals("M-1", request.captured.number)
    }

    @Test
    fun `updateAccount omits interest when periodicity is missing`() = runTest {
        val request = slot<CreateAccountRequest>()
        coEvery { apiService.updateAccount(1, capture(request)) } returns Response.success(
            AccountDto(id = 1, name = "Bank", type = "default"),
        )

        repository.updateAccount(Account(id = 1, name = "Bank", interest = 0.0))

        assertEquals(null, request.captured.interest)
        assertEquals(null, request.captured.interestPeriodicity)
    }

    @Test
    fun `recordOpeningBalance books a transfer from the liability to the reconcile account`() = runTest {
        coEvery {
            apiService.getAccounts(type = listOf("reconcile"), offset = 0, numberOfResults = 1)
        } returns Response.success(
            AccountPagedResponse(content = listOf(AccountDto(id = 99, name = "Reconcile", type = "reconcile"))),
        )
        val request = slot<CreateTransactionRequest>()
        coEvery { apiService.createTransaction(capture(request)) } returns Response.success(mockk(relaxed = true))

        val result = repository.recordOpeningBalance(mortgage, 200000.0, LocalDate.of(2020, 1, 15))

        assertTrue(result is Resource.Success)
        with(request.captured) {
            assertEquals(7L, source)
            assertEquals(99L, target)
            assertEquals(200000.0, amount, 0.0)
            assertEquals("2020-01-15", date)
            assertEquals("EUR", currency)
            assertEquals("Opening balance", description)
        }
    }

    @Test
    fun `recordOpeningBalance fails without a reconcile account`() = runTest {
        coEvery {
            apiService.getAccounts(type = listOf("reconcile"), offset = 0, numberOfResults = 1)
        } returns Response.success(AccountPagedResponse())

        val result = repository.recordOpeningBalance(mortgage, 100.0, LocalDate.now())

        assertTrue(result is Resource.Error)
        coVerify(exactly = 0) { apiService.createTransaction(any()) }
    }
}
