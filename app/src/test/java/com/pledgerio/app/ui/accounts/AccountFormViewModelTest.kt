package com.pledgerio.app.ui.accounts

import androidx.lifecycle.SavedStateHandle
import com.pledgerio.app.domain.model.AccountTypeOption
import com.pledgerio.app.domain.repository.AccountRepository
import com.pledgerio.app.domain.repository.CurrencyRepository
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AccountFormViewModelTest {

  @get:Rule
  val mainDispatcherRule = MainDispatcherRule()

  private val accountRepository = mockk<AccountRepository>()
  private val currencyRepository = mockk<CurrencyRepository>()

  private val ownedTypes = listOf(
    AccountTypeOption("default", "Checking"),
    AccountTypeOption("joined", "Joint checking"),
    AccountTypeOption("savings", "Savings"),
    AccountTypeOption("joined_savings", "Joint savings"),
  )

  private fun createViewModel(preselectedType: String = ""): AccountFormViewModel {
    every { currencyRepository.getCurrencies() } returns flowOf(emptyList())
    coEvery { accountRepository.getAccountTypes() } returns Resource.Success(
      ownedTypes + AccountTypeOption("creditor", "Creditor", isCounterparty = true),
    )
    return AccountFormViewModel(
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
    val checking = entries.single { it.label == "Checking" }
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
}
