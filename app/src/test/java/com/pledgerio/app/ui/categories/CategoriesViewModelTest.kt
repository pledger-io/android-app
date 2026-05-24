package com.pledgerio.app.ui.categories

import com.pledgerio.app.domain.model.Category
import com.pledgerio.app.domain.repository.CategoryRepository
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CategoriesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = mockk<CategoryRepository>()

    private fun category(id: Long = 1L, name: String = "Groceries") =
        Category(id = id, name = name, description = "")

    private fun createViewModel(): CategoriesViewModel {
        every { repository.observeCategories() } returns flowOf(listOf(category()))
        every { repository.observeMatching(any()) } returns flowOf(emptyList())
        coEvery { repository.refreshCategories() } returns Resource.Success(listOf(category()))
        return CategoriesViewModel(repository)
    }

    @Test
    fun `loads categories from repository on init`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.categories.size)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `search query updates ui state immediately`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onSearchQueryChanged("trans")

        assertEquals("trans", viewModel.uiState.value.searchQuery)
    }

    @Test
    fun `saveEditor rejects blank name`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.openCreateEditor()
        viewModel.saveEditor()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.editor?.hasNameError == true)
    }

    @Test
    fun `createCategory clears editor on success`() = runTest(mainDispatcherRule.dispatcher) {
        coEvery {
            repository.createCategory(name = "Utilities", description = "")
        } returns Resource.Success(category(id = 3L, name = "Utilities"))

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.openCreateEditor()
        viewModel.onEditorNameChanged("Utilities")
        viewModel.saveEditor()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.editor)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun `refresh surfaces error when cache is empty`() = runTest(mainDispatcherRule.dispatcher) {
        every { repository.observeCategories() } returns flowOf(emptyList())
        coEvery { repository.refreshCategories() } returns Resource.Error("Network error")

        val viewModel = CategoriesViewModel(repository)
        advanceUntilIdle()

        assertEquals("Network error", viewModel.uiState.value.error)
    }
}
