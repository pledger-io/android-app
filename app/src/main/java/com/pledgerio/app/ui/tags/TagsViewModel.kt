package com.pledgerio.app.ui.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pledgerio.app.domain.model.Tag
import com.pledgerio.app.domain.repository.TagRepository
import com.pledgerio.app.ui.catalog.observeCatalogSearch
import com.pledgerio.app.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TagEditorUiState(
    val originalName: String? = null,
    val name: String = "",
    val nameError: String? = null,
    val serverError: String? = null,
) {
    val isEditing: Boolean get() = originalName != null
    val title: String get() = if (isEditing) "Rename tag" else "New tag"
    val confirmLabel: String get() = if (isEditing) "Save" else "Create"
}

data class TagsUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isSaving: Boolean = false,
    val tags: List<Tag> = emptyList(),
    val searchQuery: String = "",
    val error: String? = null,
    val editor: TagEditorUiState? = null,
    val pendingDelete: Tag? = null,
) {
    val subtitle: String
        get() = if (tags.isEmpty()) {
            "Create tags to organize transactions"
        } else {
            "${tags.size} available"
        }
}

@HiltViewModel
class TagsViewModel @Inject constructor(
    private val tagRepository: TagRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TagsUiState())
    val uiState: StateFlow<TagsUiState> = _uiState.asStateFlow()

    private val searchQueryFlow = MutableStateFlow("")

    init {
        observeTags()
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
            when (val result = tagRepository.refreshTags()) {
                is Resource.Success -> _uiState.update { it.copy(isRefreshing = false, error = null) }
                is Resource.Error -> _uiState.update { state ->
                    state.copy(
                        isRefreshing = false,
                        error = if (state.tags.isEmpty()) result.message else null,
                    )
                }
                is Resource.Loading -> Unit
            }
        }
    }

    fun openCreateEditor() {
        _uiState.update {
            it.copy(
                editor = TagEditorUiState(),
                pendingDelete = null,
                error = null,
            )
        }
    }

    fun openEditEditor(tag: Tag) {
        _uiState.update {
            it.copy(
                editor = TagEditorUiState(
                    originalName = tag.name,
                    name = tag.name,
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
                    nameError = null,
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
                state.copy(editor = editor.copy(nameError = "Name is required"))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            val result = if (editor.originalName == null) {
                tagRepository.createTag(cleanName)
            } else {
                tagRepository.renameTag(editor.originalName, cleanName)
            }

            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(isSaving = false, editor = null, error = null)
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

    fun askDelete(tag: Tag) {
        _uiState.update { it.copy(pendingDelete = tag, editor = null, error = null) }
    }

    fun dismissDeleteDialog() {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(pendingDelete = null) }
    }

    fun confirmDelete() {
        val tag = _uiState.value.pendingDelete ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            when (val result = tagRepository.deleteTag(tag.name)) {
                is Resource.Success -> _uiState.update {
                    it.copy(isSaving = false, pendingDelete = null, error = null)
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

    private fun observeTags() {
        observeCatalogSearch(
            searchQueryFlow = searchQueryFlow,
            observeAll = tagRepository::observeTags,
            observeMatching = tagRepository::observeMatching,
        ) { tags ->
            _uiState.update {
                it.copy(
                    isLoading = false,
                    tags = tags,
                )
            }
        }
    }
}
