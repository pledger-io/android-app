package com.pledgerio.app.accounts.ui

import com.pledgerio.app.accounts.ui.AccountPickerLabel
import com.pledgerio.app.accounts.ui.AccountFormViewModel
import com.pledgerio.app.accounts.ui.INTEREST_PERIODICITY_YEARS
import com.pledgerio.app.shared.Resource
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.pledgerio.app.R
import com.pledgerio.app.accounts.domain.model.Account
import com.pledgerio.app.accounts.domain.model.AccountTypeGroup
import com.pledgerio.app.accounts.domain.model.AccountTypeOption
import io.mockk.slot
import java.time.LocalDate
import com.pledgerio.app.accounts.domain.repository.AccountRepository
import com.pledgerio.app.catalog.domain.repository.CurrencyRepository
import com.pledgerio.app.shared.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AccountFormViewModelTest {

  @get:Rule
  val mainDispatcherRule = MainDispatcherRule()

  private val context = mockk<Context>(relaxed = true)
  private val accountRepository = mockk<AccountRepository>()
  private val currencyRepository = mockk<CurrencyRepository>()

  init {
    every { context.getString(R.string.account_error_name_required) } returns "Please provide a name"
    every { context.getString(R.string.account_error_opening_balance_failed, any()) } returns "Opening failed"
  }

  private val ownedTypes = listOf(
    AccountTypeOption("default", "Checking"),
    AccountTypeOption("joined", "Joint checking"),
    AccountTypeOption("savings", "Savings"),
    AccountTypeOption("joined_savings", "Joint savings"),
    AccountTypeOption("loan", "Loan", group = AccountTypeGroup.LIABILITIES),
    AccountTypeOption("mortgage", "Mortgage", group = AccountTypeGroup.LIABILITIES),
  )

  private fun createViewModel(preselectedType: String = ""): AccountFormViewModel {
    every { currencyRepository.getCurrencies() } returns flowOf(emptyList())
    coEvery { accountRepository.getAccountTypes() } returns Resource.Success(
      ownedTypes + AccountTypeOption("creditor", "Creditor", isCounterparty = true),
    )
    return AccountFormViewModel(
      context,
      SavedStateHandle(mapOf("type" to preselectedType)),
      accountRepository,
      currencyRepository,
    )
  }

  @Test
  fun `preselected type applied for new account`() = runTest {
    val viewModel = createViewModel("joined")
    advanceUntilIdle()

    assertEquals("joined", viewModel.uiState.value.typeCode)
    assertNotNull(viewModel.uiState.value.typeVariantChoice)
    assertTrue(viewModel.uiState.value.typeVariantChoice!!.isJoint)
  }

  @Test
  fun `onOwnershipVariantChanged switches to joint type code`() = runTest {
    val viewModel = createViewModel("default")
    advanceUntilIdle()

    viewModel.onOwnershipVariantChanged(joint = true)
    assertEquals("joined", viewModel.uiState.value.typeCode)

    viewModel.onOwnershipVariantChanged(joint = false)
    assertEquals("default", viewModel.uiState.value.typeCode)
  }

  @Test
  fun `ownedPickerEntries merges checking types`() = runTest {
    val viewModel = createViewModel()
    advanceUntilIdle()

    val entries = viewModel.uiState.value.ownedPickerEntries
    val checking = entries.single { it.label is AccountPickerLabel.Checking }
    assertEquals("default", checking.soloTypeCode)
    assertEquals("joined", checking.jointTypeCode)
  }

  @Test
  fun `isValid requires name currency and type`() = runTest {
    val viewModel = createViewModel()
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isValid)

    viewModel.onNameChanged("My account")
    assertTrue(viewModel.uiState.value.isValid)
  }

  @Test
  fun `save ignores a second submit while the first is in flight`() = runTest {
    val viewModel = createViewModel()
    advanceUntilIdle()
    viewModel.onNameChanged("My account")
    coEvery { accountRepository.createAccount(any()) } coAnswers {
      delay(50)
      Resource.Success(firstArg())
    }

    viewModel.save()
    viewModel.save()
    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.saveSuccess)
    coVerify(exactly = 1) { accountRepository.createAccount(any()) }
  }

  @Test
  fun `liabilities get their own picker section`() = runTest {
    val viewModel = createViewModel()
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals(listOf("loan", "mortgage"), state.liabilityAccountTypes.map { it.code })
    assertTrue(state.ownedPickerEntries.none { it.soloTypeCode == "loan" })
  }

  @Test
  fun `creating a liability sends the rate as a fraction and records the starting amount`() = runTest {
    val viewModel = createViewModel("mortgage")
    advanceUntilIdle()
    viewModel.onNameChanged("House")
    viewModel.onInterestChanged("3.5")
    viewModel.onInterestPeriodicityChanged(INTEREST_PERIODICITY_YEARS)
    viewModel.onOpeningAmountChanged("200000")
    viewModel.onOpeningDateChanged(LocalDate.of(2020, 1, 15))
    val created = slot<Account>()
    coEvery { accountRepository.createAccount(capture(created)) } coAnswers {
      Resource.Success(firstArg<Account>().copy(id = 7))
    }
    coEvery { accountRepository.recordOpeningBalance(any(), any(), any()) } returns Resource.Success(Unit)

    viewModel.save()
    advanceUntilIdle()

    assertEquals(0.035, created.captured.interest!!, 1e-9)
    assertEquals("YEARS", created.captured.interestPeriodicity)
    coVerify {
      accountRepository.recordOpeningBalance(match { it.id == 7L }, 200000.0, LocalDate.of(2020, 1, 15))
    }
    assertTrue(viewModel.uiState.value.saveSuccess)
  }

  @Test
  fun `failed starting amount keeps the form open in edit mode`() = runTest {
    val viewModel = createViewModel("loan")
    advanceUntilIdle()
    viewModel.onNameChanged("Car")
    viewModel.onOpeningAmountChanged("5000")
    coEvery { accountRepository.createAccount(any()) } coAnswers {
      Resource.Success(firstArg<Account>().copy(id = 9))
    }
    coEvery { accountRepository.recordOpeningBalance(any(), any(), any()) } returns Resource.Error("boom")
    coEvery { accountRepository.updateAccount(any()) } coAnswers { Resource.Success(firstArg()) }

    viewModel.save()
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertFalse(state.saveSuccess)
    assertTrue(state.isEditing)
    assertFalse(state.showOpeningAmount)
    assertEquals("Opening failed", state.error)

    viewModel.save()
    advanceUntilIdle()
    coVerify(exactly = 1) { accountRepository.createAccount(any()) }
    coVerify { accountRepository.updateAccount(match { it.id == 9L }) }
  }

  @Test
  fun `invalid interest blocks saving`() = runTest {
    val viewModel = createViewModel("loan")
    advanceUntilIdle()
    viewModel.onNameChanged("Car")
    viewModel.onInterestChanged("abc")

    assertTrue(viewModel.uiState.value.interestError)
    assertFalse(viewModel.uiState.value.isValid)
  }

  @Test
  fun `editing a liability keeps its interest`() = runTest {
    every { currencyRepository.getCurrencies() } returns flowOf(emptyList())
    coEvery { accountRepository.getAccountTypes() } returns Resource.Success(ownedTypes)
    coEvery { accountRepository.getAccount(7) } returns Resource.Success(
      Account(id = 7, name = "House", typeCode = "mortgage", interest = 0.035, interestPeriodicity = "YEARS"),
    )
    val updated = slot<Account>()
    coEvery { accountRepository.updateAccount(capture(updated)) } coAnswers { Resource.Success(firstArg()) }
    val viewModel = AccountFormViewModel(
      context,
      SavedStateHandle(mapOf("accountId" to 7L)),
      accountRepository,
      currencyRepository,
    )
    advanceUntilIdle()
    assertEquals("3.5", viewModel.uiState.value.interestPercent)

    viewModel.onNameChanged("Home")
    viewModel.save()
    advanceUntilIdle()

    assertEquals(0.035, updated.captured.interest!!, 1e-9)
    assertEquals("YEARS", updated.captured.interestPeriodicity)
    coVerify(exactly = 0) { accountRepository.recordOpeningBalance(any(), any(), any()) }
  }
}
