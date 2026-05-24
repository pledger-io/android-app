package com.pledgerio.app.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pledgerio.app.domain.model.Category
import com.pledgerio.app.domain.repository.CategoryRepository
import com.pledgerio.app.ui.catalog.observeCatalogSearch
import com.pledgerio.app.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CategoryEditorUiState(
    val id: Long? = null,
    val name: String = "",
    val description: String = "",
    val hasNameError: Boolean = false,
    val serverError: String? = null,
) {
    val isEditing: Boolean get() = id != null
}

data class CategoriesUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isSaving: Boolean = false,
    val categories: List<Category> = emptyList(),
    val searchQuery: String = "",
    val error: String? = null,
    val editor: CategoryEditorUiState? = null,
    val pendingDelete: Category? = null,
) {
    val categoryCount: Int get() = categories.size
}

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoriesUiState())
    val uiState: StateFlow<CategoriesUiState> = _uiState.asStateFlow()

    private val searchQueryFlow = MutableStateFlow("")

    init {
        observeCategories()
        refresh()
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchQueryFlow.value = query
    }

    fun refresh() {
        if (_uiState.value.isRefreshing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            when (val result = categoryRepository.refreshCategories()) {
                is Resource.Success -> _uiState.update { it.copy(isRefreshing = false, error = null) }
                is Resource.Error -> _uiState.update { state ->
                    state.copy(
                        isRefreshing = false,
                        error = if (state.categories.isEmpty()) result.message else null,
                    )
                }
                is Resource.Loading -> Unit
            }
        }
    }

    fun openCreateEditor() {
        _uiState.update {
            it.copy(
                editor = CategoryEditorUiState(),
                pendingDelete = null,
                error = null,
            )
        }
    }

    fun openEditEditor(category: Category) {
        _uiState.update {
            it.copy(
                editor = CategoryEditorUiState(
                    id = category.id,
                    name = category.name,
                    description = category.description,
                ),
                pendingDelete = null,
                error = null,
            )
        }
    }

    fun dismissEditor() {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(editor = null) }
    }

    fun onEditorNameChanged(value: String) {
        _uiState.update { state ->
            val editor = state.editor ?: return@update state
            state.copy(
                editor = editor.copy(
                    name = value,
                    hasNameError = false,
                    serverError = null,
                ),
            )
        }
    }

    fun onEditorDescriptionChanged(value: String) {
        _uiState.update { state ->
            val editor = state.editor ?: return@update state
            state.copy(
                editor = editor.copy(
                    description = value,
                    serverError = null,
                ),
            )
        }
    }

    fun saveEditor() {
        val editor = _uiState.value.editor ?: return
        val cleanName = editor.name.trim()
        if (cleanName.isBlank()) {
            _uiState.update { state ->
                state.copy(editor = editor.copy(hasNameError = true))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            val result = if (editor.id == null) {
                categoryRepository.createCategory(
                    name = cleanName,
                    description = editor.description,
                )
            } else {
                categoryRepository.updateCategory(
                    Category(
                        id = editor.id,
                        name = cleanName,
                        description = editor.description.trim(),
                    ),
                )
            }

            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isSaving = false,
                        editor = null,
                        error = null,
                    )
                }
                is Resource.Error -> _uiState.update { state ->
                    state.copy(
                        isSaving = false,
                        editor = state.editor?.copy(serverError = result.message),
                    )
                }
                is Resource.Loading -> Unit
            }
        }
    }

    fun askDelete(category: Category) {
        _uiState.update { it.copy(pendingDelete = category, editor = null, error = null) }
    }

    fun dismissDeleteDialog() {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(pendingDelete = null) }
    }

    fun confirmDelete() {
        val category = _uiState.value.pendingDelete ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            when (val result = categoryRepository.deleteCategory(category.id)) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isSaving = false,
                        pendingDelete = null,
                        error = null,
                    )
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isSaving = false,
                        pendingDelete = null,
                        error = result.message,
                    )
                }
                is Resource.Loading -> Unit
            }
        }
    }

    private fun observeCategories() {
        observeCatalogSearch(
            searchQueryFlow = searchQueryFlow,
            observeAll = categoryRepository::observeCategories,
            observeMatching = categoryRepository::observeMatching,
        ) { categories ->
            _uiState.update {
                it.copy(
                    isLoading = false,
                    categories = categories,
                )
            }
        }
    }
}
